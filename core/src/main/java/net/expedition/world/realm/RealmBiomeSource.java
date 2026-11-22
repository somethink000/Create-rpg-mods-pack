package net.expedition.world.realm;

import com.mojang.serialization.MapCodec;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

/**
 * The world's biomes: the Overworld's own climate-driven biomes, and in every realm cell one biome of that realm (any
 * of its former dimension's biomes that is a land of its own), so all biomes follow one size rule.
 */
public final class RealmBiomeSource extends BiomeSource {
    public static final MapCodec<RealmBiomeSource> CODEC = BiomeSource.CODEC.fieldOf("overworld")
        .xmap(overworld -> new RealmBiomeSource(overworld, Map.of()), source -> source.overworld);

    private final BiomeSource overworld;
    private final BiomeSource[] sources = new BiomeSource[Realm.values().length];
    /** Per realm: its biomes that are lands of their own (see Realm.NOT_A_LAND), in a fixed order. */
    @SuppressWarnings("unchecked")
    private final List<Holder<Biome>>[] lands = new List[Realm.values().length];

    /** The biome of the ice wall behind the world border (the Ice Wall: packed and blue ice, rock, snow). */
    private final Holder<Biome> rim;

    RealmBiomeSource(BiomeSource overworld, Map<Realm, BiomeSource> realms) {
        this(overworld, realms, null);
    }

    RealmBiomeSource(BiomeSource overworld, Map<Realm, BiomeSource> realms, Holder<Biome> rim) {
        this.overworld = overworld;
        this.rim = rim;
        sources[0] = overworld;
        Map<Realm, BiomeSource> copy = new EnumMap<>(Realm.class);
        copy.putAll(realms);
        copy.forEach((realm, source) -> {
            sources[realm.ordinal()] = source;
            lands[realm.ordinal()] = source.possibleBiomes().stream()
                .filter(biome -> biome.unwrapKey().map(key -> !Realm.NOT_A_LAND.contains(key.location().toString())).orElse(false))
                .sorted(java.util.Comparator.comparing(biome -> biome.unwrapKey().get().location().toString())).toList();
        });
    }

    /**
     * A story dungeon's own land inside a realm (HomeBiomes): a round, ragged-edged biome of its own around the dungeon
     * (about {@link #LAND_RADIUS} blocks), whatever the realm's cell holds around it.
     */
    public record Land(Realm realm, int x, int z, Holder<Biome> biome) {
    }

    public static final int LAND_RADIUS = 400;
    private volatile List<Land> storyLands = List.of();

    /** Set once per world before its realm chunks generate (HomeBiomes, on the Overworld's load). */
    public void setStoryLands(List<Land> lands) {
        storyLands = List.copyOf(lands);
    }

    public List<Land> storyLands() {
        return storyLands;
    }

    /** The biomes an Overworld column may have (its climate's). */
    public java.util.Set<Holder<Biome>> overworldBiomes() {
        return overworld.possibleBiomes();
    }

    /** A realm's lands (the biomes its cells and story lands may hold), or none. */
    public List<Holder<Biome>> lands(Realm realm) {
        List<Holder<Biome>> lands = this.lands[realm.ordinal()];
        return lands == null ? List.of() : lands;
    }

    /** Is this biome one of the realm's lands (a biome a cell or story land of it may hold)? */
    public boolean isLand(Realm realm, Holder<Biome> biome) {
        List<Holder<Biome>> lands = this.lands[realm.ordinal()];
        return lands != null && biome.unwrapKey().map(key -> lands.stream().anyMatch(land -> land.is(key))).orElse(false);
    }

    /** The land of a realm at a block: a story land there, else the cell's biome. */
    public Holder<Biome> landAt(Realm realm, long cell, int x, int z) {
        Holder<Biome> land = storyLand(realm, x, z);
        return land != null ? land : cellBiome(realm, cell);
    }

    /** The story land of a realm at a block, or null. */
    private Holder<Biome> storyLand(Realm realm, int x, int z) {
        for (Land land : storyLands) {
            if (land.realm() != realm) {
                continue;
            }
            double dx = x - land.x();
            double dz = z - land.z();
            double reach = LAND_RADIUS * 1.25;
            if (dx * dx + dz * dz < reach * reach
                && Math.sqrt(dx * dx + dz * dz) < LAND_RADIUS * (0.75 + 0.5 * RealmLayout.noise(x, z, 37, 160))) {
                return land.biome();
            }
        }
        return null;
    }

    private static final net.minecraft.tags.TagKey<Biome> CAVE = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BIOME,
        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("c", "is_cave"));
    private static final net.minecraft.tags.TagKey<Biome> UNDERGROUND = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BIOME,
        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("c", "is_underground"));

    /** A cave biome (kept under an Overworld story land: the land is the surface's). */
    public static boolean underground(Holder<Biome> biome) {
        return biome.is(CAVE) || biome.is(UNDERGROUND) || biome.unwrapKey().map(key -> key.location().getPath().contains("cave")).orElse(false);
    }

    /** The biome a cell of this realm holds (see Sample.cell), or null if the realm has none. */
    public Holder<Biome> cellBiome(Realm realm, long cell) {
        List<Holder<Biome>> lands = this.lands[realm.ordinal()];
        return lands == null || lands.isEmpty() ? null : lands.get((int) Math.floorMod(cell, (long) lands.size()));
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        Stream<Holder<Biome>> all = Stream.of(sources).filter(java.util.Objects::nonNull).flatMap(source -> source.possibleBiomes().stream());
        return (rim == null ? all : Stream.concat(all, Stream.of(rim))).distinct();
    }

    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler sampler) {
        RealmLayout.Sample sample = RealmLayout.sample(QuartPos.toBlock(x), QuartPos.toBlock(z));
        if (rim != null && sample.square() >= RealmLayout.RADIUS) {
            return rim;
        }
        // One biome per realm cell: a Twilight dark forest, a crimson forest... each a land as big as an Overworld biome.
        if (sample.first() != Realm.OVERWORLD) {
            Holder<Biome> land = landAt(sample.first(), sample.cell(), QuartPos.toBlock(x), QuartPos.toBlock(z));
            if (land != null) {
                return land;
            }
        }
        Holder<Biome> natural = overworld.getNoiseBiome(x, y, z, sampler);
        // An Overworld dungeon whose biome doesn't lie in its ring gets a land of it too (on land: the terrain stays the Overworld's).
        Holder<Biome> story = sample.first() == Realm.OVERWORLD ? storyLand(Realm.OVERWORLD, QuartPos.toBlock(x), QuartPos.toBlock(z)) : null;
        return story != null && !underground(natural) ? story : natural;
    }
}
