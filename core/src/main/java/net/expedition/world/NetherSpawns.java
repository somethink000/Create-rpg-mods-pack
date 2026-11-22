package net.expedition.world;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.ModifiableBiomeInfo;

/**
 * The Nether's monsters as the realm needs them (data/expedition/neoforge/biome_modifier/nether_spawns.json). The realm
 * lies in the Overworld, under the sky: no other monster spawns there by day, while a ghast's rule ignores light, so at
 * vanilla weight (50) ghasts took the whole monster cap. They come rarely here, and blazes (vanilla: only in fortresses,
 * which only the story places) come now and then. Set last, after every mod's own additions and removals.
 */
public record NetherSpawns(HolderSet<Biome> biomes, int ghastWeight, int blazeWeight) implements BiomeModifier {
    public static final MapCodec<NetherSpawns> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
        Biome.LIST_CODEC.fieldOf("biomes").forGetter(NetherSpawns::biomes),
        com.mojang.serialization.Codec.INT.fieldOf("ghast_weight").forGetter(NetherSpawns::ghastWeight),
        com.mojang.serialization.Codec.INT.fieldOf("blaze_weight").forGetter(NetherSpawns::blazeWeight)
    ).apply(instance, NetherSpawns::new));

    @Override
    public void modify(Holder<Biome> biome, Phase phase, ModifiableBiomeInfo.BiomeInfo.Builder builder) {
        if (phase != Phase.AFTER_EVERYTHING || !biomes.contains(biome)) {
            return;
        }
        var monsters = builder.getMobSpawnSettings().getSpawner(MobCategory.MONSTER);
        monsters.removeIf(data -> data.type == EntityType.GHAST || data.type == EntityType.BLAZE);
        monsters.add(new MobSpawnSettings.SpawnerData(EntityType.GHAST, ghastWeight, 1, 1));
        monsters.add(new MobSpawnSettings.SpawnerData(EntityType.BLAZE, blazeWeight, 1, 2));
    }

    @Override
    public MapCodec<? extends BiomeModifier> codec() {
        return CODEC;
    }
}
