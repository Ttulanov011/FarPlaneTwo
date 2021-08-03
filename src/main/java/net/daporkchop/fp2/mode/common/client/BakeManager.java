/*
 * Adapted from The MIT License (MIT)
 *
 * Copyright (c) 2020-2021 DaPorkchop_
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy,
 * modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software
 * is furnished to do so, subject to the following conditions:
 *
 * Any persons and/or organizations using this software must include the above copyright notice and this permission notice,
 * provide sufficient credit to the original authors of the project (IE: DaPorkchop_), as well as provide a link to the original project.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS
 * BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 */

package net.daporkchop.fp2.mode.common.client;

import lombok.Getter;
import lombok.NonNull;
import net.daporkchop.fp2.config.FP2Config;
import net.daporkchop.fp2.mode.api.Compressed;
import net.daporkchop.fp2.mode.api.IFarPos;
import net.daporkchop.fp2.mode.api.IFarTile;
import net.daporkchop.fp2.mode.api.client.IFarTileCache;
import net.daporkchop.fp2.util.SimpleRecycler;
import net.daporkchop.fp2.util.threading.ClientThreadExecutor;
import net.daporkchop.fp2.util.threading.keyed.KeyedTaskScheduler;
import net.daporkchop.fp2.util.threading.keyed.PriorityKeyedTaskScheduler;
import net.daporkchop.lib.common.misc.threadfactory.PThreadFactories;
import net.daporkchop.lib.unsafe.util.AbstractReleasable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static net.daporkchop.lib.common.util.PorkUtil.*;

/**
 * @author DaPorkchop_
 */
@Getter
public class BakeManager<POS extends IFarPos, T extends IFarTile> extends AbstractReleasable implements IFarTileCache.Listener<POS, T>, Runnable {
    protected final AbstractFarRenderer<POS, T> renderer;
    protected final IFarRenderStrategy<POS, T> strategy;

    protected final IFarTileCache<POS, T> tileCache;

    protected final FarRenderTree<POS, T> tree;

    protected final KeyedTaskScheduler<POS> bakeExecutor;

    protected final Map<POS, Optional<BakeOutput>> pendingTreeUpdates = new ConcurrentHashMap<>();
    protected final AtomicBoolean isBulkUpdateQueued = new AtomicBoolean();

    public BakeManager(@NonNull AbstractFarRenderer<POS, T> renderer, @NonNull IFarTileCache<POS, T> tileCache) {
        this.renderer = renderer;
        this.strategy = renderer.strategy();
        this.tileCache = tileCache;

        this.tree = new FarRenderTree<>(renderer.mode(), this.strategy, renderer.maxLevel());

        this.bakeExecutor = uncheckedCast(new PriorityKeyedTaskScheduler<>(
                FP2Config.client.renderThreads,
                PThreadFactories.builder().daemon().minPriority().collapsingId().name("FP2 Rendering Thread #%d").build()));

        this.tileCache.addListener(this, true);
    }

    @Override
    protected void doRelease() {
        this.tileCache.removeListener(this, false);

        this.bakeExecutor.release();
    }

    @Override
    public void tileAdded(@NonNull Compressed<POS, T> tile) {
        this.notifyOutputs(tile.pos());
    }

    @Override
    public void tileModified(@NonNull Compressed<POS, T> tile) {
        this.notifyOutputs(tile.pos());
    }

    @Override
    public void tileRemoved(@NonNull POS pos) {
        //make sure that any in-progress bake tasks are finished before the tile is removed
        this.bakeExecutor.submitExclusive(pos, () -> this.updateTree(pos, Optional.empty()));
    }

    protected void notifyOutputs(@NonNull POS pos) {
        this.strategy.bakeOutputs(pos).forEach(outputPos -> {
            if (outputPos.level() < 0 || outputPos.level() > this.renderer.maxLevel) { //output tile is at an invalid zoom level, skip it
                return;
            }

            //schedule tile for baking
            this.bakeExecutor.submitExclusive(outputPos, () -> this.bake(outputPos));
        });
    }

    protected void bake(@NonNull POS pos) { //this function is called from inside of RENDER_WORKERS, which holds a lock on the position
        Compressed<POS, T>[] compressedInputTiles = uncheckedCast(this.tileCache.getTilesCached(this.strategy.bakeInputs(pos)).toArray(Compressed[]::new));
        if (compressedInputTiles[0] == null) { //the tile isn't cached any more
            return;
        }

        if (compressedInputTiles[0].isEmpty()) { //tile has no data, we "bake" it by deleting it if it exists
            this.updateTree(pos, Optional.of(BakeOutput.empty()));
            return;
        }

        SimpleRecycler<T> recycler = this.renderer.mode().tileRecycler();
        T[] srcs = this.renderer.mode().tileArray(compressedInputTiles.length);
        try {
            for (int i = 0; i < srcs.length; i++) { //inflate tiles
                if (compressedInputTiles[i] != null) {
                    srcs[i] = compressedInputTiles[i].inflate(recycler);
                }
            }

            BakeOutput output = new BakeOutput(this.strategy.renderDataSize());
            boolean nonEmpty = this.strategy.bake(pos, srcs, output);

            this.updateTree(pos, Optional.of(nonEmpty ? output : BakeOutput.empty()));
        } finally { //release tiles again
            for (int i = 0; i < srcs.length; i++) {
                if (srcs[i] != null) {
                    recycler.release(srcs[i]);
                }
            }
        }
    }

    protected void updateTree(@NonNull POS pos, @NonNull Optional<BakeOutput> optionalBakeOutput) {
        this.pendingTreeUpdates.put(pos, optionalBakeOutput);

        if (this.isBulkUpdateQueued.compareAndSet(false, true)) { //we won the race to enqueue a bulk update!
            ClientThreadExecutor.INSTANCE.execute(this);
        }
    }

    /**
     * @deprecated internal API, do not touch!
     */
    @Override
    @Deprecated
    public void run() { //executes a bulk update on the client thread
        try {
            int size = this.pendingTreeUpdates.size();
            List<Map.Entry<POS, Optional<BakeOutput>>> updates = new ArrayList<>(size + (size >> 3)); //pre-allocate a bit of extra space in case it grows while we're iterating
            updates.addAll(this.pendingTreeUpdates.entrySet());

            //atomically remove the corresponding entries from the pending update queue
            updates.forEach(update -> this.pendingTreeUpdates.remove(update.getKey(), update.getValue()));

            //execute the bulk update
            this.tree.bulkUpdate(updates);
        } finally {
            this.isBulkUpdateQueued.set(false);
        }
    }
}
