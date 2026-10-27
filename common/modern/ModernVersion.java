package bench;

// What differs between modern (LWJGL 3) versions. Each versions/<id>/Bench.java extends this and implements
// createWorld; the defaults below use the official names that have been stable since 1.15.
public abstract class ModernVersion {
    protected static final String MC = "net.minecraft.client.Minecraft";
    protected static final String WINDOW = "com.mojang.blaze3d.platform.Window";
    protected static final String ENTITY = "net.minecraft.world.entity.Entity";

    // Called on the main thread; must return once the player is about to enter the world.
    public abstract void createWorld(Mapped m, Object mc, String dir, long seed) throws Exception;

    // The title screen is up and the resource-loading overlay is gone.
    public boolean atTitle(Mapped m, Object mc) throws Exception {
        Object screen = m.get(mc, MC, "screen");
        return screen != null && m.cls("net.minecraft.client.gui.screens.TitleScreen").isInstance(screen)
                && m.get(mc, MC, "overlay") == null;
    }

    public boolean inWorld(Mapped m, Object mc) throws Exception {
        return m.get(mc, MC, "level") != null && m.get(mc, MC, "player") != null;
    }

    // "<n> fps ..." as on the F3 screen; "(<n> chunk updates)" is parsed when present (up to 1.14).
    public String fpsLine(Mapped m, Object mc) throws Exception {
        return String.valueOf(m.get(mc, MC, "fpsString"));
    }

    // The F3 chunk line; "pC: <n>" (sections waiting to be compiled) is parsed when present.
    public String chunkLine(Mapped m, Object mc) throws Exception {
        Object lr = m.get(mc, MC, "levelRenderer");
        return String.valueOf(m.call(lr, "net.minecraft.client.renderer.LevelRenderer", "getChunkStatistics()"));
    }

    public double[] pos(Mapped m, Object player) throws Exception {
        return new double[] {
            (Double) m.call(player, ENTITY, "getX()"), (Double) m.call(player, ENTITY, "getY()"), (Double) m.call(player, ENTITY, "getZ()") };
    }

    public long windowHandle(Mapped m, Object mc) throws Exception {
        Object w = m.call(mc, MC, "getWindow()");
        return (Long) m.call(w, WINDOW, "getWindow()");
    }
}
