package net.expedition.garrison;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;

/** A bar at the top of the screen for players inside a garrisoned dungeon: how many of its mobs are left. */
final class GarrisonBars {
    private GarrisonBars() {
    }

    private static final Map<String, ServerBossEvent> BARS = new HashMap<>();
    private static final Map<String, Set<ServerPlayer>> PRESENT = new HashMap<>();

    static void beginTick() {
        PRESENT.clear();
    }

    /** Called for every player inside an active dungeon during a garrison tick. */
    static void show(String key, GarrisonRecord record, ServerPlayer player) {
        if (record.state != GarrisonRecord.State.ACTIVE || record.total <= 0) {
            return;
        }
        ServerBossEvent bar = BARS.computeIfAbsent(key, k -> new ServerBossEvent(Component.empty(), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10));
        int left = record.remaining();
        bar.setName(Component.translatable("expedition.garrison.bar",
            Garrisons.dungeonName(player.getServer(), key), left, record.total));
        bar.setProgress(Math.max(0, Math.min(1, left / (float) record.total)));
        bar.addPlayer(player);
        PRESENT.computeIfAbsent(key, k -> new HashSet<>()).add(player);
    }

    /** Players who left the dungeon stop seeing its bar. */
    static void endTick() {
        BARS.forEach((key, bar) -> {
            Set<ServerPlayer> present = PRESENT.getOrDefault(key, Set.of());
            for (ServerPlayer player : Set.copyOf(bar.getPlayers())) {
                if (!present.contains(player)) {
                    bar.removePlayer(player);
                }
            }
        });
    }

    static void remove(String key) {
        ServerBossEvent bar = BARS.remove(key);
        if (bar != null) {
            bar.removeAllPlayers();
        }
    }
}
