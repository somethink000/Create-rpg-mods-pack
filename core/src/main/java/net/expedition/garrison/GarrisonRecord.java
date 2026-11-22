package net.expedition.garrison;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/** Saved state of one dungeon's garrison. */
public final class GarrisonRecord {
    public enum State { PENDING, ACTIVE, CLEARED }

    public State state = State.PENDING;
    /** Size of the garrison: kills it takes to clear the dungeon. The bar shows remaining / total. */
    public int total;
    /** Garrison mobs killed so far. */
    public int killed;
    /** Spawned and not yet dead or gone (shown by /expedition garrison; the bar and spawning go by kills). */
    public int alive;
    public int spawners;
    public int chests;
    /** NG+ level: how many times the dungeon was reset after clearing. Mobs get stronger and more numerous. */
    public int ng;
    /** Size fixed by the volume formula; false for garrisons started before it (resized once on the next visit). */
    public boolean sized;
    /** Mob type id -> number of spawners of that type found in the dungeon. */
    public final Map<String, Integer> spawnerTypes = new LinkedHashMap<>();
    /** The garrison's mob table, fixed when it first starts and kept through NG+ (same mobs, only stronger). */
    public final Map<String, Integer> mobs = new LinkedHashMap<>();
    /** Where the spawners stood: part of the garrison spawns there. */
    /** Special mod loot taken out of the dungeon's chests: its garrison mobs drop it. */
    public final List<ItemStack> specialDrops = new ArrayList<>();
    /** Chunks whose loot chests were already counted. */
    public final Set<Long> scannedChunks = new HashSet<>();

    /** After a NG+ reset: the dungeon is rebuilt, so everything is counted again and the garrison returns stronger. */
    public void reset() {
        ng++;
        state = State.PENDING;
        killed = 0;
        alive = 0;
        total = 0;
        spawners = 0;
        chests = 0;
        spawnerTypes.clear();
        scannedChunks.clear();
        specialDrops.clear();
    }

    /** Kills still needed: the bar. Mobs alive somewhere (fallen off a ship, wandered away) don't hold it up. */
    public int remaining() {
        return Math.max(0, total - killed);
    }

    CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putString("state", state.name());
        tag.putInt("killed", killed);
        tag.putInt("alive", alive);
        tag.putInt("total", total);
        tag.putInt("spawners", spawners);
        tag.putInt("chests", chests);
        tag.putInt("ng", ng);
        tag.putBoolean("sized", sized);
        CompoundTag types = new CompoundTag();
        spawnerTypes.forEach(types::putInt);
        tag.put("spawnerTypes", types);
        CompoundTag mobTable = new CompoundTag();
        mobs.forEach(mobTable::putInt);
        tag.put("mobs", mobTable);
        tag.put("scanned", new LongArrayTag(scannedChunks.stream().mapToLong(Long::longValue).toArray()));
        ListTag drops = new ListTag();
        specialDrops.forEach(stack -> drops.add(stack.save(registries)));
        tag.put("specialDrops", drops);
        return tag;
    }

    static GarrisonRecord load(CompoundTag tag, HolderLookup.Provider registries) {
        GarrisonRecord record = new GarrisonRecord();
        record.state = State.valueOf(tag.getString("state"));
        record.alive = tag.getInt("alive");
        record.total = tag.getInt("total");
        // Saved before kills were counted (remaining was "still to spawn + alive"): what isn't either was killed.
        record.killed = tag.contains("killed") ? tag.getInt("killed") : Math.max(0, record.total - tag.getInt("budget") - record.alive);
        record.spawners = tag.getInt("spawners");
        record.chests = tag.getInt("chests");
        record.ng = tag.getInt("ng");
        record.sized = tag.getBoolean("sized");
        CompoundTag mobTable = tag.getCompound("mobs");
        for (String type : mobTable.getAllKeys()) {
            record.mobs.put(type, mobTable.getInt(type));
        }
        CompoundTag types = tag.getCompound("spawnerTypes");
        for (String type : types.getAllKeys()) {
            record.spawnerTypes.put(type, types.getInt(type));
        }
        for (long chunk : tag.getLongArray("scanned")) {
            record.scannedChunks.add(chunk);
        }
        for (Tag drop : tag.getList("specialDrops", Tag.TAG_COMPOUND)) {
            ItemStack.parse(registries, drop).ifPresent(record.specialDrops::add);
        }
        return record;
    }
}
