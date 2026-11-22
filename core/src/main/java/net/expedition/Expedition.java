package net.expedition;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

/** Core mod of the pack: route, garrisons, dungeon reset, pack items and data overrides live here. */
@Mod(Expedition.MOD_ID)
public final class Expedition {
    public static final String MOD_ID = "expedition";
    public static final Logger LOG = LogUtils.getLogger();

    public Expedition(IEventBus modBus) {
        ExpeditionRegistry.register(modBus);
        net.expedition.world.realm.RealmWorld.register(modBus);
        net.expedition.route.StoryItems.register(modBus);
        net.expedition.garrison.DungeonBlocks.register(modBus);
    }
}
