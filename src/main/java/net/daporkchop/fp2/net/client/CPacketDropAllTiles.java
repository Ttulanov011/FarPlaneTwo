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

package net.daporkchop.fp2.net.client;

import io.netty.buffer.ByteBuf;
import net.daporkchop.fp2.mode.api.ctx.IFarWorldServer;
import net.daporkchop.fp2.util.Constants;
import net.daporkchop.fp2.util.threading.ThreadingHelper;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import static net.daporkchop.fp2.debug.FP2Debug.*;

/**
 * @author DaPorkchop_
 */
public class CPacketDropAllTiles implements IMessage {
    @Override
    public void fromBytes(ByteBuf buf) {
    }

    @Override
    public void toBytes(ByteBuf buf) {
    }

    public static class Handler implements IMessageHandler<CPacketDropAllTiles, IMessage> {
        @Override
        public IMessage onMessage(CPacketDropAllTiles message, MessageContext ctx) {
            if (!FP2_DEBUG) {
                ctx.getServerHandler().disconnect(new TextComponentTranslation("fp2.debug.debugModeNotEnabled"));
                return null;
            }
            ThreadingHelper.scheduleTaskInWorldThread(ctx.getServerHandler().player.world, () -> {
                Constants.FP2_LOG.info("Dropping all tiles");
                ((IFarWorldServer) ctx.getServerHandler().player.world).fp2_IFarWorldServer_forEachContext(context -> context.world().tracker().debug_dropAllTiles());
            });
            return null;
        }
    }
}
