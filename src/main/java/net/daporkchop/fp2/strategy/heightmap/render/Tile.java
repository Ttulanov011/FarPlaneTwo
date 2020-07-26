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

import lombok.NonNull;
import net.daporkchop.fp2.util.Constants;
import net.daporkchop.fp2.util.math.Sphere;
import net.daporkchop.lib.unsafe.PUnsafe;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.util.math.AxisAlignedBB;

import java.nio.ByteBuffer;
import java.util.function.Consumer;

import static net.daporkchop.fp2.strategy.heightmap.HeightmapConstants.*;
import static net.daporkchop.fp2.strategy.heightmap.render.HeightmapRenderHelper.*;
import static net.daporkchop.lib.common.util.PValidation.*;

/**
 * @author DaPorkchop_
 */
class Tile extends AxisAlignedBB {
    protected static final long MINY_OFFSET = Constants.fieldOffset(AxisAlignedBB.class, "minY", "field_72338_b");
    protected static final long MAXY_OFFSET = Constants.fieldOffset(AxisAlignedBB.class, "maxY", "field_72337_e");

    protected final int x;
    protected final int z;
    protected final int level;

    protected final Tile parent;
    protected final Tile[] children; //non-null for levels above 0

    protected long address = -1L;
    protected ByteBuffer renderData;

    public boolean doesSelfOrAnyChildrenHaveAddress = false;

    public Tile(Tile parent, int x, int z, int level) {
        super(x << HEIGHTMAP_SHIFT << level, 0.0d, z << HEIGHTMAP_SHIFT << level, (x + 1) << HEIGHTMAP_SHIFT << level, 0.0d, (z + 1) << HEIGHTMAP_SHIFT << level);

        this.parent = parent;
        this.x = x;
        this.z = z;
        this.level = level;

        this.children = level > 0 ? new Tile[4] : null;
    }

    public Tile findChild(int x, int z, int level) {
        Tile next = this.findChildStep(x, z, level);
        return next == this || next == null ? next : next.findChild(x, z, level);
    }

    public Tile findOrCreateChild(int x, int z, int level) {
        Tile next = this.findChildStep(x, z, level);
        if (next == null) {
            next = new Tile(this, x >> (level - this.level - 1), z >> (level - this.level - 1), this.level - 1);
            this.children[this.childIndex(x, z, level)] = next;
        }
        return next == this ? next : next.findOrCreateChild(x, z, level);
    }

    public Tile findChildStep(int x, int z, int level) {
        if (this.x == x && this.z == z && this.level == level) {
            return this;
        }
        checkState(this.level > level);
        checkState((x >> (level - this.level)) == this.x && (z >> (level - this.level)) == this.z);
        return this.children[this.childIndex(x, z, level)];
    }

    private int childIndex(int x, int z, int level) {
        return (((x >> (level - this.level - 1)) & 1) << 1) | ((z >> (level - this.level - 1)) & 1);
    }

    public void forEach(@NonNull Consumer<Tile> callback) {
        callback.accept(this);
        if (this.children != null) {
            for (Tile child : this.children) {
                if (child != null) {
                    callback.accept(child);
                }
            }
        }
    }

    public boolean hasAddress() {
        return this.address >= 0L;
    }

    public void assignAddress(long address) {
        checkState(!this.hasAddress(), "already has an address?!?");
        this.address = address;
        this.renderData = Constants.createByteBuffer(HEIGHTMAP_RENDER_SIZE);
        this.markHasAddress(true);
    }

    /**
     * Deletes this node's address.
     *
     * @return whether or not the root tile should be deleted
     */
    public boolean dropAddress() {
        checkState(this.hasAddress(), "doesn't have an address?!?");
        this.address = -1L;
        PUnsafe.pork_releaseBuffer(this.renderData);
        this.renderData = null;
        return this.markHasAddress(false);
    }

    private boolean markHasAddress(boolean hasAddress) {
        if (!hasAddress && this.children != null) {
            //check if any children have an address
            for (int i = 0, len = this.children.length; !hasAddress && i < len; i++) {
                if (this.children[i] != null) {
                    hasAddress = this.children[i].doesSelfOrAnyChildrenHaveAddress;
                }
            }
        }
        this.doesSelfOrAnyChildrenHaveAddress = hasAddress;
        if (!hasAddress) {
            //neither this tile nor any of its children has an address
            if (this.parent != null) {
                //this tile has a parent, remove it from the parent
                this.parent.children[this.parent.childIndex(this.x, this.z, this.level)] = null;

                //notify the parent that this tile has been removed
                return this.parent.markHasAddress(this.parent.hasAddress());
            } else {
                //this tile is the root, return true to inform render cache to delete the root
                return true;
            }
        }
        return false;
    }

    public boolean considerForSelection(@NonNull Sphere[] ranges, @NonNull ICamera frustum) {
        if (!ranges[this.level].intersects(this)) {
            //the view range for this level doesn't intersect this tile's bounding box,
            // so we can be certain that neither this tile nor any of its children would be contained
            return false;
        } else if (!frustum.isBoundingBoxInFrustum(this)) {
            //the frustum doesn't contain this tile's bounding box, so we can be certain that neither
            // this tile nor any of its children would be visible
            return false;
        }
        return true;
    }

    public boolean select(Tile parent, @NonNull Sphere[] ranges, @NonNull ICamera frustum, @NonNull HeightmapRenderIndex index) {
        if (!this.considerForSelection(ranges, frustum)) {
            return false;
        }

        if (this.children == null) {
            //level 0 tiles have no children to be considered during selection
            index.add(this, parent);
            return true;
        } else {
            if (ranges[this.level - 1].intersects(this)) {
                //this tile intersects with the view distance for tiles below it, consider selecting them as well
                //currently there is no mechanism for rendering part of a tile, so we only do the actual selection at the next level
                // if all of the children could be selectable
                boolean childrenSelectable = true;
                for (int i = 0, len = this.children.length; childrenSelectable && i < len; i++) {
                    if (this.children[i] != null) {
                        childrenSelectable = this.children[i].considerForSelection(ranges, frustum);
                    } else {
                        childrenSelectable = false;
                    }
                }
                if (childrenSelectable) {
                    //if all children are selectable, select all of them
                    for (int i = 0, len = this.children.length; i < len; i++) {
                        this.children[i].select(this, ranges, frustum, index);
                    }
                    return true;
                }
            }
            index.add(this, parent);
            return true;
        }
    }
}
