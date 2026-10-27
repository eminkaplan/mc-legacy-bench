package bench;

// Minecraft 1.14.4: a fixed-seed survival world through Minecraft.selectLevel, as CreateWorldScreen.onCreate does.
// 1.14 still has the Window as a field and entity positions as fields (getX() and friends arrived in 1.15).
public class Bench extends ModernVersion {
    public static void main(String[] args) throws Exception {
        ModernBench.run(new Bench(), args);
    }

    public void createWorld(Mapped m, Object mc, String dir, long seed) throws Exception {
        Object settings = m.make("net.minecraft.world.level.LevelSettings",
                "(long,net.minecraft.world.level.GameType,boolean,boolean,net.minecraft.world.level.LevelType)",
                seed, m.get(null, "net.minecraft.world.level.GameType", "SURVIVAL"), true, false,
                m.get(null, "net.minecraft.world.level.LevelType", "NORMAL"));
        m.call(mc, MC, "selectLevel(java.lang.String,java.lang.String,net.minecraft.world.level.LevelSettings)", dir, "Bench", settings);
    }

    public long windowHandle(Mapped m, Object mc) throws Exception {
        return (Long) m.call(m.get(mc, MC, "window"), WINDOW, "getWindow()");
    }

    public double[] pos(Mapped m, Object player) throws Exception {
        return new double[] { (Double) m.get(player, ENTITY, "x"), (Double) m.get(player, ENTITY, "y"), (Double) m.get(player, ENTITY, "z") };
    }
}
