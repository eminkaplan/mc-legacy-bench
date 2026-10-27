package bench;

import java.util.OptionalLong;

// Minecraft 1.16.5: a fixed-seed survival world through Minecraft.createLevel, as CreateWorldScreen.onCreate does.
public class Bench extends ModernVersion {
    public static void main(String[] args) throws Exception {
        ModernBench.run(new Bench(), args);
    }

    public void createWorld(Mapped m, Object mc, String dir, long seed) throws Exception {
        String ra = "net.minecraft.core.RegistryAccess", reg = "net.minecraft.core.Registry", key = "net.minecraft.resources.ResourceKey";
        String wgs = "net.minecraft.world.level.levelgen.WorldGenSettings";
        Object registries = m.call(null, ra, "builtin()");
        Object dims = m.call(registries, ra, "registryOrThrow(" + key + ")", m.get(null, reg, "DIMENSION_TYPE_REGISTRY"));
        Object biomes = m.call(registries, ra, "registryOrThrow(" + key + ")", m.get(null, reg, "BIOME_REGISTRY"));
        Object noise = m.call(registries, ra, "registryOrThrow(" + key + ")", m.get(null, reg, "NOISE_GENERATOR_SETTINGS_REGISTRY"));
        Object gen = m.call(null, wgs, "makeDefault(" + reg + "," + reg + "," + reg + ")", dims, biomes, noise);
        gen = m.call(gen, wgs, "withSeed(boolean,java.util.OptionalLong)", false, OptionalLong.of(seed));
        Object settings = m.make("net.minecraft.world.level.LevelSettings",
                "(java.lang.String,net.minecraft.world.level.GameType,boolean,net.minecraft.world.Difficulty,boolean,"
                        + "net.minecraft.world.level.GameRules,net.minecraft.world.level.DataPackConfig)",
                "Bench", m.get(null, "net.minecraft.world.level.GameType", "SURVIVAL"), false,
                m.get(null, "net.minecraft.world.Difficulty", "NORMAL"), false,
                m.make("net.minecraft.world.level.GameRules", "()"),
                m.get(null, "net.minecraft.world.level.DataPackConfig", "DEFAULT"));
        m.call(mc, MC, "createLevel(java.lang.String,net.minecraft.world.level.LevelSettings,"
                + "net.minecraft.core.RegistryAccess$RegistryHolder," + wgs + ")", dir, settings, registries, gen);
    }
}
