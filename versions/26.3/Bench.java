package bench;

import java.util.function.Function;

// Minecraft 26.3: unobfuscated, so names are used as-is. A fixed-seed survival world through
// WorldOpenFlows.createFreshLevel, as CreateWorldScreen does; screen and overlay moved into Gui.
public class Bench extends ModernVersion {
    public static void main(String[] args) throws Exception {
        ModernBench.run(new Bench(), args);
    }

    public void createWorld(final Mapped m, Object mc, String dir, long seed) throws Exception {
        String ls = "net.minecraft.world.level.LevelSettings";
        Object difficulty = m.make(ls + "$DifficultySettings", "(net.minecraft.world.Difficulty,boolean,boolean)",
                m.get(null, "net.minecraft.world.Difficulty", "NORMAL"), false, false);
        Object settings = m.make(ls, "(java.lang.String,net.minecraft.world.level.GameType," + ls + "$DifficultySettings,boolean,"
                + "net.minecraft.world.level.WorldDataConfiguration)",
                "Bench", m.get(null, "net.minecraft.world.level.GameType", "SURVIVAL"), difficulty, false,
                m.get(null, "net.minecraft.world.level.WorldDataConfiguration", "DEFAULT"));
        Object options = m.make("net.minecraft.world.level.levelgen.WorldOptions", "(long,boolean,boolean)", seed, true, false);
        Function<Object, Object> dimensions = new Function<Object, Object>() {
            public Object apply(Object registries) {
                try {
                    return m.call(null, "net.minecraft.world.level.levelgen.presets.WorldPresets",
                            "createNormalWorldDimensions(net.minecraft.core.HolderLookup$Provider)", registries);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };
        Object flows = m.call(mc, MC, "createWorldOpenFlows()");
        m.call(flows, "net.minecraft.client.gui.screens.worldselection.WorldOpenFlows",
                "createFreshLevel(java.lang.String," + ls + ",net.minecraft.world.level.levelgen.WorldOptions,"
                        + "java.util.function.Function,net.minecraft.client.gui.screens.Screen)",
                dir, settings, options, dimensions, screen(m, mc));
    }

    private Object gui(Mapped m, Object mc) throws Exception {
        return m.get(mc, MC, "gui");
    }

    private Object screen(Mapped m, Object mc) throws Exception {
        return m.call(gui(m, mc), "net.minecraft.client.gui.Gui", "screen()");
    }

    public boolean atTitle(Mapped m, Object mc) throws Exception {
        if (gui(m, mc) == null) return false;     // not built yet while the client starts
        Object screen = screen(m, mc);
        return screen != null && m.cls("net.minecraft.client.gui.screens.TitleScreen").isInstance(screen)
                && m.call(gui(m, mc), "net.minecraft.client.gui.Gui", "overlay()") == null;
    }

    public String fpsLine(Mapped m, Object mc) throws Exception {
        return m.call(mc, MC, "getFps()") + " fps";
    }

    public String chunkLine(Mapped m, Object mc) throws Exception {
        Object lr = m.get(mc, MC, "levelRenderer");
        Object dispatcher = m.call(lr, "net.minecraft.client.renderer.LevelRenderer", "sectionRenderDispatcher()");
        return "pC: " + m.call(dispatcher, "net.minecraft.client.renderer.chunk.SectionRenderDispatcher", "getCompileQueueSize()");
    }

    public long windowHandle(Mapped m, Object mc) throws Exception {
        return (Long) m.call(m.call(mc, MC, "getWindow()"), WINDOW, "handle()");
    }
}
