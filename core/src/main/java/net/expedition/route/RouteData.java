package net.expedition.route;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Story progress of every expedition journal (one per player), keyed by the journal's id (saved with the overworld). */
public final class RouteData extends SavedData {
    private static final String NAME = "expedition_route";

    /** A step's dungeon (or boss lair) standing in the world, marked on its player's map. */
    public static final class Target {
        public String dimension;
        public BlockPos pos;
        public String dungeonKey;
        /** A boss that rises at the place when a player comes near (lairs, the Wither's sanctum), or null. */
        public String summon;

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            if (summon != null) {
                tag.putString("summon", summon);
            }
            tag.putString("dimension", dimension);
            tag.putLong("pos", pos.asLong());
            tag.putString("dungeon", dungeonKey);
            return tag;
        }

        static Target load(CompoundTag tag) {
            Target target = new Target();
            target.dimension = tag.getString("dimension");
            target.pos = BlockPos.of(tag.getLong("pos"));
            target.dungeonKey = tag.getString("dungeon");
            target.summon = tag.contains("summon") ? tag.getString("summon") : null;
            return target;
        }
    }

    /** One journal's progress: the story act it is in, the steps done, and the steps whose dungeons stand in the world. */
    public static final class Progress {
        /** 0: not started yet; 1..7: the act. */
        public int act;
        public boolean finished;
        /** Route step ids (file names) completed. */
        public final Set<String> done = new TreeSet<>();
        /** Steps of opened acts whose dungeon stands in the world, by step id. */
        public final Map<String, Target> active = new TreeMap<>();
        /** Story books, items and the like handed out once (by name). */
        public final Set<String> given = new TreeSet<>();
        /** The branch the journal's story goes on: 0 around spawn, else RouteData.branches[branch - 1] (Branches). */
        public int branch;
        /** Where the journal's bosses fell, by step id: marked on every player's map (RouteMarkers). */
        public final Map<String, Target> fallen = new TreeMap<>();

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("act", act);
            tag.putBoolean("finished", finished);
            tag.put("done", strings(done));
            tag.put("given", strings(given));
            CompoundTag targets = new CompoundTag();
            active.forEach((step, target) -> targets.put(step, target.save()));
            tag.put("active", targets);
            CompoundTag fallenTag = new CompoundTag();
            fallen.forEach((step, target) -> fallenTag.put(step, target.save()));
            tag.put("fallen", fallenTag);
            tag.putInt("branch", branch);
            return tag;
        }

        static Progress load(CompoundTag tag) {
            Progress progress = new Progress();
            if (tag.contains("step")) {
                // The old one-step-at-a-time route: every step before the current one is done, the act is its tier's.
                int step = tag.getInt("step");
                for (int i = 0; i < step && i < RouteSteps.all().size(); i++) {
                    progress.done.add(RouteSteps.all().get(i).id().getPath());
                }
                RouteSteps.Entry current = RouteSteps.get(step);
                progress.act = current == null ? Acts.LAST : Acts.actOf(current.step());
                progress.finished = current == null;
                return progress;
            }
            progress.act = tag.getInt("act");
            progress.branch = tag.getInt("branch");
            progress.finished = tag.getBoolean("finished");
            tag.getList("done", Tag.TAG_STRING).forEach(entry -> progress.done.add(entry.getAsString()));
            tag.getList("given", Tag.TAG_STRING).forEach(entry -> progress.given.add(entry.getAsString()));
            CompoundTag targets = tag.getCompound("active");
            for (String step : targets.getAllKeys()) {
                progress.active.put(step, Target.load(targets.getCompound(step)));
            }
            CompoundTag fallenTag = tag.getCompound("fallen");
            for (String step : fallenTag.getAllKeys()) {
                progress.fallen.put(step, Target.load(fallenTag.getCompound(step)));
            }
            return progress;
        }

        private static ListTag strings(Set<String> values) {
            ListTag list = new ListTag();
            values.forEach(value -> list.add(StringTag.valueOf(value)));
            return list;
        }
    }

    private final Map<UUID, Progress> journals = new HashMap<>();
    /** Dungeons placed ahead of their act (the prologue films them), by step id: the first journal to open the step takes it. */
    public final Map<String, Target> reserved = new TreeMap<>();
    private static final String RESERVED = "reserved";
    /** Middles of the story branches begun away from spawn (branch 1, 2...): a player can start their story anew there. */
    public final java.util.List<net.minecraft.core.BlockPos> branches = new java.util.ArrayList<>();
    private static final String BRANCHES = "branches";

    public static RouteData get(MinecraftServer server) {
        return get(server.overworld());
    }

    /** From the Overworld itself (while it loads, before the server knows it as its Overworld). */
    public static RouteData get(net.minecraft.server.level.ServerLevel overworld) {
        return overworld.getDataStorage().computeIfAbsent(new Factory<>(RouteData::new, RouteData::load), NAME);
    }

    public Progress progress(UUID journal) {
        setDirty();
        return journals.computeIfAbsent(journal, id -> new Progress());
    }

    public Map<UUID, Progress> all() {
        return journals;
    }

    private static RouteData load(CompoundTag tag, HolderLookup.Provider registries) {
        RouteData data = new RouteData();
        CompoundTag reserved = tag.getCompound(RESERVED);
        reserved.getAllKeys().forEach(step -> data.reserved.put(step, Target.load(reserved.getCompound(step))));
        for (long packed : tag.getLongArray(BRANCHES)) {
            data.branches.add(net.minecraft.core.BlockPos.of(packed));
        }
        for (String key : tag.getAllKeys()) {
            if (key.equals(RESERVED) || key.equals(BRANCHES)) {
                continue;
            }
            data.journals.put(UUID.fromString(key), Progress.load(tag.getCompound(key)));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        journals.forEach((id, progress) -> tag.put(id.toString(), progress.save()));
        CompoundTag reservedTag = new CompoundTag();
        reserved.forEach((step, target) -> reservedTag.put(step, target.save()));
        tag.put(RESERVED, reservedTag);
        tag.putLongArray(BRANCHES, branches.stream().mapToLong(net.minecraft.core.BlockPos::asLong).toArray());
        return tag;
    }
}
