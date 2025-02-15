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

package net.daporkchop.fp2.client.gl.command;

/**
 * Represents an OpenGL indirect draw command.
 *
 * @author DaPorkchop_
 */
public interface IDrawIndirectCommand extends IDrawCommand {
    /**
     * @return the size of a single command, in bytes
     */
    long size();

    /**
     * Loads this command into this object instance from the given off-heap memory address.
     *
     * @param addr the memory address to load from
     */
    void load(long addr);

    /**
     * Stores this command to the given off-heap memory address.
     *
     * @param addr the memory address to store to
     */
    void store(long addr);

    /**
     * @return the base instance number
     */
    int baseInstance();

    /**
     * Sets the base instance number.
     *
     * @param baseInstance the new baseInstance
     */
    IDrawIndirectCommand baseInstance(int baseInstance);

    /**
     * @return the number of instances to be rendered
     */
    int instanceCount();

    /**
     * Sets the number of instances to be rendered.
     *
     * @param instanceCount the new instanceCount
     */
    IDrawIndirectCommand instanceCount(int instanceCount);
}
