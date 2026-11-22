package net.expedition.garrison;

import java.util.List;
import net.expedition.Expedition;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.levelgen.structure.StructureSpawnOverride;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;

/**
 * Natural spawning in dungeons, the other way they make mobs besides spawner blocks: many structures have their own
 * spawn lists (structure spawn overrides) that fill them with their mobs endlessly - graveyards, crypts, fortresses,
 * monuments, outposts, end ships - or none at all (Twilight Forest, Aether, Cataclysm: empty lists).
 * <ul>
 * <li>While a dungeon has its garrison (not cleared yet), no monster spawns in it naturally: neither the structure's
 * nor the biome's. Its mobs are the garrison's, counted and finite.</li>
 * <li>Once cleared (or a structure that never gets a garrison), the structure's own lists are gone: what spawns there
 * is what spawns in its biome, as anywhere else, with no multiplier.</li>
 * </ul>
 * Runs last, after mods that add their own structure mobs to the event.
 */
@EventBusSubscriber(modid = Expedition.MOD_ID)
public final class DungeonSpawns {
    private DungeonSpawns() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onPotentialSpawns(LevelEvent.PotentialSpawns event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        BlockPos pos = event.getPos();
        // Spawn attempts are many (every loaded chunk, every tick): only chunks a structure reaches are looked into.
        if (level.getChunkAt(pos).getAllReferences().isEmpty()) {
            return;
        }
        MobCategory category = event.getMobCategory();
        for (Garrisons.Dungeon dungeon : Garrisons.dungeonsAt(level, pos)) {
            StructureSpawnOverride override = dungeon.structure().spawnOverrides().get(category);
            boolean inside = override != null && override.boundingBox() == StructureSpawnOverride.BoundingBoxType.STRUCTURE
                ? dungeon.box().isInside(pos) : Garrisons.inPiece(level, dungeon, pos);
            if (!inside) {
                continue;
            }
            if (!category.isFriendly() && holdsGarrison(level, dungeon)) {
                event.setCanceled(true);
                return;
            }
            if (override != null) {
                List<MobSpawnSettings.SpawnerData> biome = level.getBiome(pos).value().getMobSettings().getMobs(category).unwrap();
                List.copyOf(event.getSpawnerDataList()).forEach(event::removeSpawnerData);
                biome.forEach(event::addSpawnerData);
                return;
            }
        }
    }

    /** The dungeon's garrison isn't beaten yet (or it hasn't been seen, but will have one). */
    static boolean holdsGarrison(ServerLevel level, Garrisons.Dungeon dungeon) {
        GarrisonRecord record = GarrisonData.get(level).find(dungeon.key());
        if (record == null) {
            return Garrisons.isEligible(new GarrisonRecord(), dungeon);
        }
        return record.state == GarrisonRecord.State.ACTIVE || record.state == GarrisonRecord.State.PENDING && Garrisons.isEligible(record, dungeon);
    }
}
