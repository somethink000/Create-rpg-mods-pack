package net.expedition.route;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/**
 * One step of the expedition route (data: {@code data/<ns>/expedition_route/<name>.json}).
 * A step with structures is placed on demand when it opens and is done when its boss (or guardian) dies, which also
 * frees its dungeon, or, with neither, when its garrison is cleared; a step without structures (Ender Dragon, Wither)
 * is done when the boss dies anywhere.
 * {@code heart}: what the boss's heart crafts into; {@code loot}: the boss's other drops (both shown in the journal).
 */
public record RouteStep(int order, String tier, List<ResourceLocation> structures, ResourceLocation dimension,
                        int minDistance, int maxDistance, Optional<ResourceLocation> boss, List<ResourceLocation> heart,
                        List<ResourceLocation> loot, Optional<ResourceLocation> biomeTag, List<ResourceLocation> biomeExclude, boolean overVoid,
                        boolean summon, String scenes, List<ResourceLocation> homeBiomes, Optional<Guardian> guardian,
                        List<ResourceLocation> garrison) {

    /** Without a garrison list of its own. */
    public RouteStep(int order, String tier, List<ResourceLocation> structures, ResourceLocation dimension, int minDistance, int maxDistance,
                     Optional<ResourceLocation> boss, List<ResourceLocation> heart, List<ResourceLocation> loot, Optional<ResourceLocation> biomeTag,
                     List<ResourceLocation> biomeExclude, boolean overVoid, boolean summon, String scenes, List<ResourceLocation> homeBiomes,
                     Optional<Guardian> guardian) {
        this(order, tier, structures, dimension, minDistance, maxDistance, boss, heart, loot, biomeTag, biomeExclude, overVoid, summon, scenes,
            homeBiomes, guardian, List.of());
    }

    /**
     * A dungeon's guardian where the dungeon has no boss of its own: a creature (often an ordinary one, as in Dark Souls)
     * made into a boss for its chapter: its health and damage set (0: its own), grown by {@code scale}, armoured, with a
     * name ({@code expedition.guardian.<step>}) and a boss bar ({@code bar}: false if it has one of its own). It rises in
     * the dungeon's main hall when a player comes in; its death, not the garrison's, completes the step (Guardians).
     */
    public record Guardian(ResourceLocation entity, double health, double damage, double scale, double armor, boolean bar, boolean rise,
                           boolean water) {
        /** A guardian that rises in the hall when the dungeon lacks it. */
        public Guardian(ResourceLocation entity, double health, double damage, double scale, double armor, boolean bar) {
            this(entity, health, damage, scale, armor, bar, true, false);
        }

        public static final Codec<Guardian> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("entity").forGetter(Guardian::entity),
            Codec.DOUBLE.optionalFieldOf("health", 0.0).forGetter(Guardian::health),
            Codec.DOUBLE.optionalFieldOf("damage", 0.0).forGetter(Guardian::damage),
            Codec.DOUBLE.optionalFieldOf("scale", 1.0).forGetter(Guardian::scale),
            Codec.DOUBLE.optionalFieldOf("armor", 0.0).forGetter(Guardian::armor),
            Codec.BOOL.optionalFieldOf("bar", true).forGetter(Guardian::bar),
            // false: the dungeon's own boss (from its structure, spawner or altar) is adopted when it appears; none rises.
            Codec.BOOL.optionalFieldOf("rise", true).forGetter(Guardian::rise),
            // Rises in the dungeon's water, not on its floor (a sea monster that walks: the Kraken).
            Codec.BOOL.optionalFieldOf("water", false).forGetter(Guardian::water)
        ).apply(instance, Guardian::new));
    }

    /** Speakers of the step's approach scene (see Acts.speaker). */
    public String arriveSpeakers() {
        return scenes.contains("|") ? scenes.substring(0, scenes.indexOf('|')) : scenes;
    }

    /** Speakers of the step's fall scene (empty: none). */
    public String fallSpeakers() {
        return scenes.contains("|") ? scenes.substring(scenes.indexOf('|') + 1) : "";
    }

    public static final Codec<RouteStep> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.INT.fieldOf("order").forGetter(RouteStep::order),
        Codec.STRING.fieldOf("tier").forGetter(RouteStep::tier),
        ResourceLocation.CODEC.listOf().optionalFieldOf("structures", List.of()).forGetter(RouteStep::structures),
        ResourceLocation.CODEC.optionalFieldOf("dimension", ResourceLocation.withDefaultNamespace("overworld")).forGetter(RouteStep::dimension),
        Codec.INT.optionalFieldOf("min_distance", 600).forGetter(RouteStep::minDistance),
        Codec.INT.optionalFieldOf("max_distance", 1200).forGetter(RouteStep::maxDistance),
        ResourceLocation.CODEC.optionalFieldOf("boss").forGetter(RouteStep::boss),
        ResourceLocation.CODEC.listOf().optionalFieldOf("heart", List.of()).forGetter(RouteStep::heart),
        ResourceLocation.CODEC.listOf().optionalFieldOf("loot", List.of()).forGetter(RouteStep::loot),
        // Structures whose own code checks a narrower biome (Mowzie's: savanna, snowy, peaks): search only there.
        ResourceLocation.CODEC.optionalFieldOf("biome_tag").forGetter(RouteStep::biomeTag),
        // ...but not in these biome tags (Mowzie's Frostmaw: snowy, but not forests, taiga or water).
        ResourceLocation.CODEC.listOf().optionalFieldOf("biome_exclude", List.of()).forGetter(RouteStep::biomeExclude),
        // Arenas built to hang over the End's void (Geburah): search void columns instead of islands.
        com.mojang.serialization.Codec.BOOL.optionalFieldOf("over_void", false).forGetter(RouteStep::overVoid),
        // The boss doesn't live in the structure on its own (the Wither): it rises there when a player comes near.
        com.mojang.serialization.Codec.BOOL.optionalFieldOf("summon", false).forGetter(RouteStep::summon),
        // Story scenes: "<approach speakers>|<fall speakers>" (lang expedition.story.arrive/fall.<step>.N).
        com.mojang.serialization.Codec.STRING.optionalFieldOf("scenes", "").forGetter(RouteStep::scenes),
        // The dungeon's own lands, most fitting first: one is chosen per world, a different one for every dungeon (HomeBiomes).
        ResourceLocation.CODEC.listOf().optionalFieldOf("home_biomes", List.of()).forGetter(RouteStep::homeBiomes),
        // The dungeon's defenders (one field pair: a codec takes 16 fields at most): its guardian, and the monsters its
        // garrison is made of (Garrisons: the dungeon's tier and theme, every mob whose drops the gear of that tier needs).
        Codec.mapPair(Guardian.CODEC.optionalFieldOf("guardian"), ResourceLocation.CODEC.listOf().optionalFieldOf("garrison", List.of()))
            .forGetter(step -> com.mojang.datafixers.util.Pair.of(step.guardian(), step.garrison()))
    ).apply(instance, (order, tier, structures, dimension, minDistance, maxDistance, boss, heart, loot, biomeTag, biomeExclude, overVoid, summon,
        scenes, homeBiomes, defenders) -> new RouteStep(order, tier, structures, dimension, minDistance, maxDistance, boss, heart, loot, biomeTag,
        biomeExclude, overVoid, summon, scenes, homeBiomes, defenders.getFirst(), defenders.getSecond())));

    /** Lang key base: expedition.route.<file name>.title / .text */
    public String langKey(ResourceLocation id) {
        return "expedition.route." + id.getPath();
    }
}
