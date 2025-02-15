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

package net.daporkchop.fp2.config;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.daporkchop.fp2.FP2;
import net.daporkchop.lib.common.util.PorkUtil;
import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import static java.lang.Math.*;

/**
 * @author DaPorkchop_
 */
@Config(modid = FP2.MODID)
@Mod.EventBusSubscriber
public class FP2Config {
    @Config.Comment({
            "The mode that will be used for rendering distant terrain."
    })
    @Config.LangKey("config.fp2.renderMode")
    @Config.RequiresWorldRestart
    public static String renderMode = "voxel";

    @Config.Comment({
            "The far plane render distance (in blocks)"
    })
    @Config.RangeInt(min = 1)
    @Config.LangKey("config.fp2.renderDistance")
    public static int renderDistance = 512;

    @Config.Comment({
            "The number of LoD levels to use.",
            "Warning: Increasing this value requires exponentially larger amounts of memory! (for now, this'll be fixed later)"
    })
    @Config.RangeInt(min = 1, max = 32)
    @Config.SlidingOption
    @Config.LangKey("config.fp2.maxLevels")
    @Config.RequiresWorldRestart
    public static int maxLevels = 3;

    @Config.Comment({
            "The distance (in blocks) between LoD transitions.",
            "Note that this value is doubled for each level, so a setting of 64 will mean 64 blocks for the first layer, 128 blocks for the second layer, etc."
    })
    @Config.RangeInt(min = 0)
    @Config.LangKey("config.fp2.levelCutoffDistance")
    public static int levelCutoffDistance = 256;

    @Config.Comment({
            "The number of threads that will be used on the server for loading and generating fp2 terrain data.",
            "Default: <cpu count> - 1 (and at least 1)"
    })
    @Config.RangeInt(min = 1)
    @Config.LangKey("config.fp2.generationThreads")
    @Config.RequiresWorldRestart
    public static int generationThreads = max(PorkUtil.CPU_COUNT - 1, 1);

    @Config.Comment({
            "Config options available only on the client."
    })
    @Config.LangKey("config.fp2.client")
    public static Client client = new Client();

    @Config.Comment({
            "Performance options."
    })
    @Config.LangKey("config.fp2.performance")
    public static Performance performance = new Performance();

    @Config.Comment({
            "Compatibility options."
    })
    @Config.LangKey("config.fp2.compatibility")
    public static Compatibility compatibility = new Compatibility();

    @Config.Comment({
            "Options for storage of far terrain tiles."
    })
    @Config.LangKey("config.fp2.storage")
    public static Storage storage = new Storage();

    @Config.Comment({
            "Config options useful while developing the mod.",
            "Note: these options will be ignored unless you add '-Dfp2.debug=true' to your JVM launch arguments."
    })
    @Config.LangKey("config.fp2.debug")
    public static Debug debug = new Debug();

    @SubscribeEvent
    public static void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
        if (FP2.MODID.equals(event.getModID())) {
            ConfigManager.sync(FP2.MODID, Config.Type.INSTANCE);
            ConfigListenerManager.fire();
        }
    }

    /**
     * @author DaPorkchop_
     */
    public static class Client {
        @Config.Comment({
                "The number of threads that will be used for preparing far plane terrain data for rendering.",
                "Default: <cpu count> - 1 (and at least 1)"
        })
        @Config.LangKey("config.fp2.client.renderThreads")
        @Config.RequiresWorldRestart
        public int renderThreads = max(PorkUtil.CPU_COUNT - 1, 1);

        @Config.Comment({
                "The factor of the maximum extent of a detail level at which the detail transition will start.",
                "Should be less than levelTransitionEnd."
        })
        @Config.LangKey("config.fp2.client.levelTransitionStart")
        @Config.SlidingOption
        @Config.RangeDouble(min = 0.0d, max = 1.0d)
        public double levelTransitionStart = 0.6d;

        @Config.Comment({
                "The factor of the maximum extent of a detail level at which the detail transition will end.",
                "Should be more than levelTransitionStart."
        })
        @Config.LangKey("config.fp2.client.levelTransitionEnd")
        @Config.SlidingOption
        @Config.RangeDouble(min = 0.0d, max = 1.0d)
        public double levelTransitionEnd = 0.9d;
    }

    /**
     * @author DaPorkchop_
     */
    public static class Performance {
        @Config.Comment({
                "Whether or not tiles can be generated at low resolution if supported by the terrain generator.",
                "Don't disable this unless you have a specific reason for doing so - it can have MASSIVE performance implications."
        })
        @Config.LangKey("config.fp2.performance.lowResolutionEnable")
        @Config.RequiresWorldRestart
        public boolean lowResolutionEnable = true;

        @Config.Comment({
                "The number of threads to be used for tracking the tiles loaded by a given player.",
                "You shouldn't need to change this unless you have huge player counts.",
                "Default: 2"
        })
        @Config.LangKey("config.fp2.performance.trackingThreads")
        @Config.RequiresMcRestart
        public int trackingThreads = min(PorkUtil.CPU_COUNT, 2);

        @Config.Comment({
                "Allows frustum culling to be done on the GPU instead of the CPU.",
                "This can have major performance benefits, but may cause visual glitches or even crashes."
        })
        @Config.LangKey("config.fp2.performance.gpuFrustumCulling")
        @Config.RequiresWorldRestart
        public boolean gpuFrustumCulling = true;

        @Config.Comment({
                "Whether or not frustum culling should be done on multiple threads.",
                "Only makes a difference if GPU frustum culling is disabled.",
                "This will likely hurt performance except for specific scenarios.",
                "Currently unimplemented."
        })
        @Config.LangKey("config.fp2.performance.multithreadedFrustumCulling")
        public boolean multithreadedFrustumCulling = false;

        @Config.Comment({
                "The maximum number of tile bake outputs to process per frame.",
                "Increasing this value will increase the rate at which the client can process terrain data from the server, at the cost",
                "of more stutters when loading terrain. Lowering this value will reduce or eliminate stutters, but may cause artificially increased tile",
                "update latency and client memory usage."
        })
        @Config.LangKey("config.fp2.performance.maxBakesProcessedPerFrame")
        @Config.RangeInt(min = 1)
        public int maxBakesProcessedPerFrame = 128;
    }

    /**
     * @author DaPorkchop_
     */
    public static class Compatibility {
        @Config.Comment({
                "Whether or not to use a reversed-Z projection matrix on the client.",
                "Enabling this prevents Z-fighting (flickering) of distant geometry, but may cause issues with other rendering mods."
        })
        @Config.LangKey("config.fp2.compatibility.reversedZ")
        public boolean reversedZ = true;

        @Config.Comment({
                "A workaround for an issue with AMD's official GPU driver which results in horrible performance when a vertex attribute",
                "doesn't have a 4-byte alignment."
        })
        @Config.LangKey("config.fp2.compatibility.workaroundAmdVertexPadding")
        @Config.RequiresMcRestart
        public WorkaroundState workaroundAmdVertexPadding = WorkaroundState.AUTO;

        @Config.Comment({
                "A workaround for an issue with Intel's official GPU driver which results in multidraw drawing commands being so horrifically buggy",
                "that they might as well not work at all. (more specifically: flickering, things being rendered in the wrong positions, memory corruption,",
                "driver segfaults, the whole deal)"
        })
        @Config.LangKey("config.fp2.compatibility.workaroundIntelMultidrawNotWorking")
        @Config.RequiresMcRestart
        public WorkaroundState workaroundIntelMultidrawNotWorking = WorkaroundState.AUTO;

        /**
         * @author DaPorkchop_
         */
        public enum WorkaroundState {
            AUTO {
                @Override
                public boolean shouldEnable(boolean flag) {
                    return flag;
                }
            },
            ENABLED {
                @Override
                public boolean shouldEnable(boolean flag) {
                    return true;
                }
            },
            DISABLED {
                @Override
                public boolean shouldEnable(boolean flag) {
                    return false;
                }
            };

            public abstract boolean shouldEnable(boolean flag);
        }
    }

    /**
     * @author DaPorkchop_
     */
    public static class Storage {
    }

    /**
     * @author DaPorkchop_
     */
    public static class Debug {
        @Config.Comment({
                "If true, the vanilla world will not be rendered."
        })
        @Config.LangKey("config.fp2.debug.skipRenderWorld")
        public boolean skipRenderWorld = false;

        @Config.Comment({
                "If true, exact generators will never be used."
        })
        @Config.LangKey("config.fp2.debug.disableExactGeneration")
        public boolean disableExactGeneration = false;

        @Config.Comment({
                "If true, level 0 tiles will not be rendered."
        })
        @Config.LangKey("config.fp2.debug.skipLevel0")
        public boolean skipLevel0 = false;

        @Config.Comment({
                "If true, backface culling will be disabled."
        })
        @Config.LangKey("config.fp2.debug.disableBackfaceCull")
        public boolean disableBackfaceCull = false;

        @Config.Comment({
                "The debug color mode to enable."
        })
        @Config.LangKey("config.fp2.debug.debugShadingMode")
        public DebugColorMode debugColorMode = DebugColorMode.DISABLED;

        /**
         * @author DaPorkchop_
         */
        @RequiredArgsConstructor
        @Getter
        public enum DebugColorMode {
            DISABLED(false),
            DISTANCE(true),
            POSITIONS(true),
            FACE_NORMAL(true);

            protected final boolean enable;
        }
    }
}
