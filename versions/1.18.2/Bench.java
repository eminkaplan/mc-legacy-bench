package bench;

import java.util.OptionalLong;

// Minecraft 1.18.2: a fixed-seed survival world through Minecraft.createLevel, as CreateWorldScreen.onCreate does.
public class Bench extends ModernVersion {
    public static void main(String[] args) throws Exception {
        ModernBench.run(new Bench(), args);
    }

    public void createWorld(Mapped m, Object mc, String dir, long seed) throws Exception {
        String ra = "net.minecraft.core.RegistryAccess", wgs = "net.minecraft.world.level.levelgen.WorldGenSettings";
        Object registries = m.call(null, ra, "builtinCopy()");
        Object gen = m.call(null, wgs, "makeDefault(" + ra + ")", registries);
        gen = m.call(gen, wgs, "withSeed(boolean,java.util.OptionalLong)", false, OptionalLong.of(seed));
        Object settings = m.make("net.minecraft.world.level.LevelSettings",
                "(java.lang.String,net.minecraft.world.level.GameType,boolean,net.minecraft.world.Difficulty,boolean,"
                        + "net.minecraft.world.level.GameRules,net.minecraft.world.level.DataPackConfig)",
                "Bench", m.get(null, "net.minecraft.world.level.GameType", "SURVIVAL"), false,
                m.get(null, "net.minecraft.world.Difficulty", "NORMAL"), false,
                m.make("net.minecraft.world.level.GameRules", "()"),
                m.get(null, "net.minecraft.world.level.DataPackConfig", "DEFAULT"));
        m.call(mc, MC, "createLevel(java.lang.String,net.minecraft.world.level.LevelSettings," + ra + "," + wgs + ")",
                dir, settings, registries, gen);
    }
}
