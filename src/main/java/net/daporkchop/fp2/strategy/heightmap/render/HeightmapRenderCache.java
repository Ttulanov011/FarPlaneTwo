/*
 * Adapted from The MIT License (MIT)
 *
 * Copyright (c) 2020-2020 DaPorkchop_
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

package net.daporkchop.fp2.strategy.heightmap.render;

import io.netty.buffer.ByteBuf;
import lombok.NonNull;
import net.daporkchop.fp2.FP2;
import net.daporkchop.fp2.client.gl.object.ShaderStorageBuffer;
import net.daporkchop.fp2.client.gl.object.VertexArrayObject;
import net.daporkchop.fp2.client.gl.shader.ShaderProgram;
import net.daporkchop.fp2.strategy.heightmap.HeightmapPiece;
import net.daporkchop.fp2.strategy.heightmap.HeightmapPos;
import net.daporkchop.fp2.util.alloc.Allocator;
import net.daporkchop.fp2.util.alloc.FixedSizeAllocator;
import net.daporkchop.fp2.util.math.Sphere;
import net.daporkchop.lib.common.math.BinMath;
import net.daporkchop.lib.common.misc.string.PStrings;
import net.daporkchop.lib.primitive.map.LongObjMap;
import net.daporkchop.lib.primitive.map.open.LongObjOpenHashMap;
import net.daporkchop.lib.unsafe.PUnsafe;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.culling.ICamera;

import static net.daporkchop.fp2.strategy.heightmap.render.HeightmapRenderHelper.*;
import static net.daporkchop.fp2.strategy.heightmap.render.HeightmapRenderer.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL31.*;
import static org.lwjgl.opengl.GL43.*;

/**
 * @author DaPorkchop_
 */
public class HeightmapRenderCache {
    protected final HeightmapRenderer renderer;

    protected final LongObjMap<Tile> roots = new LongObjOpenHashMap<>();

    protected final ShaderStorageBuffer dataSSBO = new ShaderStorageBuffer();
    protected final Allocator dataAllocator = new FixedSizeAllocator(HEIGHTMAP_RENDER_SIZE, (oldSize, newSize) -> {
        try (ShaderStorageBuffer ssbo = this.dataSSBO.bind()) {
            //grow SSBO
            glBufferData(GL_SHADER_STORAGE_BUFFER, newSize, GL_STATIC_DRAW);

            this.roots.forEach((l, root) -> root.forEach(tile -> {
                if (tile.hasAddress()) {
                    glBufferSubData(GL_SHADER_STORAGE_BUFFER, tile.address, tile.renderData);
                }
            }));
        }
    });

    protected final ShaderStorageBuffer indexSSBO = new ShaderStorageBuffer();
    protected final HeightmapRenderIndex index = new HeightmapRenderIndex();

    public HeightmapRenderCache(@NonNull HeightmapRenderer renderer) {
        this.renderer = renderer;

        int size = glGetInteger(GL_MAX_SHADER_STORAGE_BLOCK_SIZE);
        FP2.LOGGER.info(PStrings.fastFormat("Max SSBO size: %d bytes (%.2f MiB)", size, size / (1024.0d * 1024.0d)));
    }

    public void receivePiece(@NonNull HeightmapPiece piece) {
        ByteBuf bakedData = HeightmapRenderHelper.bakePiece(piece);
        HeightmapPos pos = piece.pos();

        Minecraft.getMinecraft().addScheduledTask(() -> {
            try {
                int maxLevel = this.renderer.maxLevel;
                long rootKey = BinMath.packXY(pos.x() >> maxLevel, pos.z() >> maxLevel);
                Tile rootTile = this.roots.get(rootKey);
                if (rootTile == null) {
                    //create root tile if absent
                    this.roots.put(rootKey, rootTile = new Tile(null, pos.x() >> maxLevel, pos.z() >> maxLevel, maxLevel));
                }
                Tile tile = rootTile.findOrCreateChild(pos.x(), pos.z(), pos.level());
                if (!tile.hasAddress()) {
                    //allocate address for tile
                    tile.assignAddress(this.dataAllocator.alloc(HEIGHTMAP_RENDER_SIZE));
                }

                //TODO: some sort of "piece attributes" would be good here
                PUnsafe.putDouble(piece, Tile.MINY_OFFSET, Integer.MIN_VALUE);
                PUnsafe.putDouble(piece, Tile.MINY_OFFSET, Integer.MAX_VALUE);

                //copy baked data into tile and upload to GPU
                bakedData.readBytes(tile.renderData);
                tile.renderData.clear();
                try (ShaderStorageBuffer ssbo = this.dataSSBO.bind()) {
                    glBufferSubData(GL_SHADER_STORAGE_BUFFER, tile.address, tile.renderData);
                }
            } finally {
                bakedData.release();
            }
        });
    }

    public void unloadPiece(@NonNull HeightmapPos pos) {
        Minecraft.getMinecraft().addScheduledTask(() -> {
            long rootKey = BinMath.packXY(pos.x() >> pos.level(), pos.z() >> pos.level());
            Tile rootTile = this.roots.get(rootKey);
            if (rootTile != null) {
                Tile unloadedTile = rootTile.findChild(pos.x(), pos.z(), pos.level());
                if (unloadedTile != null && unloadedTile.hasAddress()) {
                    //free address
                    this.dataAllocator.free(unloadedTile.address);

                    //inform tile that the address has been freed
                    if (unloadedTile.dropAddress()) {
                        this.roots.remove(rootKey);
                    }
                    return;
                }
            }
            FP2.LOGGER.warn("Attempted to unload already non-existent piece at {}!", pos);
        });
    }

    public void render(Sphere[] ranges, ICamera frustum) {
        //rebuild and upload index
        this.index.reset();
        this.roots.forEach((l, tile) -> tile.select(null, ranges, frustum, this.index));
        try (ShaderStorageBuffer ssbo = this.indexSSBO.bind()) {
            this.index.upload(GL_SHADER_STORAGE_BUFFER);
        }
        this.indexSSBO.bindSSBO(2);

        //do the rendering stuff
        try (VertexArrayObject vao = this.renderer.vao.bind()) {
            try (ShaderProgram shader = TERRAIN_SHADER.use()) {
                GlStateManager.disableAlpha();

                glDrawElementsInstanced(GL_TRIANGLES, this.renderer.meshVertexCount, GL_UNSIGNED_SHORT, 0L, this.index.size);

                GlStateManager.enableAlpha();
            }
            try (ShaderProgram shader = WATER_SHADER.use()) {
                GlStateManager.enableBlend();
                GlStateManager.blendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

                glUniform1f(shader.uniformLocation("seaLevel"), 63.0f);

                glDrawElementsInstanced(GL_TRIANGLES, this.renderer.meshVertexCount, GL_UNSIGNED_SHORT, 0L, this.index.size);

                GlStateManager.disableBlend();
            }
        }
    }
}
