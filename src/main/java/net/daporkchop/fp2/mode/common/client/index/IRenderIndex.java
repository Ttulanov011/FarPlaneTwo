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

package net.daporkchop.fp2.mode.common.client.index;

import lombok.NonNull;
import net.daporkchop.fp2.client.gl.camera.IFrustum;
import net.daporkchop.fp2.client.gl.command.IDrawCommand;
import net.daporkchop.fp2.mode.api.IFarPos;
import net.daporkchop.fp2.mode.common.client.bake.IBakeOutput;
import net.daporkchop.lib.common.misc.refcount.RefCounted;
import net.daporkchop.lib.unsafe.util.exception.AlreadyReleasedException;

import java.util.Map;
import java.util.Optional;

/**
 * Contains baked render output for tiles at all detail levels, as well as keeping track of which tiles are renderable.
 *
 * @author DaPorkchop_
 */
public interface IRenderIndex<POS extends IFarPos, B extends IBakeOutput, C extends IDrawCommand> extends RefCounted {
    /**
     * Executes multiple updates in bulk.
     * <p>
     * For data updates, each position may be mapped to one of three different things:<br>
     * - an {@link Optional} containing a non-empty {@link B}, in which case the bake output will be executed and inserted at the position<br>
     * - an empty {@link Optional}, in which case the position will be removed
     *
     * @param dataUpdates       the data updates to be applied
     * @param renderableUpdates the updates to tile renderability be applied
     */
    void update(@NonNull Iterable<Map.Entry<POS, Optional<B>>> dataUpdates, @NonNull Iterable<Map.Entry<POS, Boolean>> renderableUpdates);

    /**
     * Should be called before issuing any draw commands.
     * <p>
     * This will determine which tiles need to be rendered for the current frame.
     */
    void select(@NonNull IFrustum frustum, float partialTicks);

    /**
     * Checks whether or not there are any renderable tiles at the given level.
     * @param level the level to check
     * @return whether or not there are any renderable tiles at the given level
     */
    boolean hasAnyTilesForLevel(int level);

    /**
     * Draws a single render pass at the given level.
     *
     * @param level the level to render
     * @param pass  the pass to render
     */
    void draw(int level, int pass);

    @Override
    int refCnt();

    @Override
    IRenderIndex<POS, B, C> retain() throws AlreadyReleasedException;

    @Override
    boolean release() throws AlreadyReleasedException;
}
