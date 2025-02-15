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

package net.daporkchop.fp2.net.server;

import io.netty.buffer.ByteBuf;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import net.daporkchop.fp2.mode.api.IFarPos;
import net.daporkchop.fp2.mode.api.IFarRenderMode;
import net.daporkchop.fp2.mode.api.client.IFarTileCache;
import net.daporkchop.fp2.mode.api.ctx.IFarWorldClient;
import net.daporkchop.fp2.util.Constants;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.ArrayList;
import java.util.Collection;

import static net.daporkchop.lib.common.util.PorkUtil.*;

/**
 * @author DaPorkchop_
 */
@Getter
@Setter
public class SPacketUnloadTiles implements IMessage {
    @NonNull
    protected IFarRenderMode<?, ?> mode;
    @NonNull
    protected Collection<IFarPos> positions;

    @Override
    public void fromBytes(ByteBuf buf) {
        this.mode = IFarRenderMode.REGISTRY.get(Constants.readString(buf));
        int size = Constants.readVarInt(buf);
        this.positions = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            this.positions.add(this.mode.readPos(buf));
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        Constants.writeString(buf, this.mode.name());
        Constants.writeVarInt(buf, this.positions.size());
        this.positions.forEach(pos -> pos.writePos(buf));
    }

    public static class Handler implements IMessageHandler<SPacketUnloadTiles, IMessage> {
        @Override
        public IMessage onMessage(SPacketUnloadTiles message, MessageContext ctx) {
            IFarTileCache<?, ?> tileCache = ((IFarWorldClient) ctx.getClientHandler().world).fp2_IFarWorldClient_contextFor(message.mode).tileCache();
            message.positions.forEach(pos -> tileCache.unloadTile(uncheckedCast(pos)));
            return null;
        }
    }
}
