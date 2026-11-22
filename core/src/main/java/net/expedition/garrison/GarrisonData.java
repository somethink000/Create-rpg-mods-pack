package net.expedition.garrison;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** Garrisons of one dimension, saved with the world (data/expedition_garrisons.dat). */
public final class GarrisonData extends SavedData {
    private static final String NAME = "expedition_garrisons";

    private final Map<String, GarrisonRecord> records = new HashMap<>();

    public static GarrisonData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new Factory<>(GarrisonData::new, GarrisonData::load), NAME);
    }

    public GarrisonRecord find(String key) {
        return records.get(key);
    }

    public GarrisonRecord getOrCreate(String key) {
        setDirty();
        return records.computeIfAbsent(key, k -> new GarrisonRecord());
    }

    public Map<String, GarrisonRecord> all() {
        return records;
    }

    private static GarrisonData load(CompoundTag tag, HolderLookup.Provider registries) {
        GarrisonData data = new GarrisonData();
        for (String key : tag.getAllKeys()) {
            data.records.put(key, GarrisonRecord.load(tag.getCompound(key), registries));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        records.forEach((key, record) -> tag.put(key, record.save(registries)));
        return tag;
    }
}
