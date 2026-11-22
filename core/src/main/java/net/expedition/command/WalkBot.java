package net.expedition.command;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.expedition.Expedition;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Load testing without a client: survival players that walk straight out from a point at a given speed, each on its own
 * heading, so chunks load and generate, mobs spawn and garrisons wake up exactly as around real players
 * ({@code /expedition bot <speed> [count]|stop}). A bot waits at the edge of land not yet loaded, as a player would, so
 * its real speed against the asked one is how well generation keeps up; every 5 s the log gets the tick times and it.
 */
@EventBusSubscriber(modid = Expedition.MOD_ID)
public final class WalkBot {
    private WalkBot() {
    }

    private record Bot(ServerPlayer player, EmbeddedChannel channel, double dx, double dz) {
    }

    private static final int REPORT_TICKS = 100;
    /** Blocks ahead that should already be loaded in full: 4 chunks, inside the simulation distance of 5 (past it C2ME
     * sends chunks straight from storage without loading them, so they never count as loaded). */
    private static final double VIEW_AHEAD = 4 * 16;
    private static final List<Bot> BOTS = new ArrayList<>();
    private static double speed;
    private static long ticks;
    private static double slowTicks;
    private static double walked;
    private static double walkedAtReport;
    private static long maxTickNanos;
    /** Bot-ticks, and those with land loaded at the edge of the view ahead (what a player would see, not only underfoot). */
    private static long looks, seen;
    private static long reportNanos;

    static String start(ServerLevel level, double x, double z, double blocksPerSecond, int count) {
        stop();
        for (int i = 0; i < count; i++) {
            double angle = 2 * Math.PI * i / count;
            BOTS.add(spawn(level, x, z, Math.cos(angle), Math.sin(angle), i));
        }
        speed = blocksPerSecond / 20;
        ticks = 0;
        slowTicks = 0;
        walked = 0;
        walkedAtReport = 0;
        maxTickNanos = 0;
        looks = 0;
        seen = 0;
        reportNanos = System.nanoTime();
        return count + " walk-bots from " + (int) x + " " + (int) z + ", " + blocksPerSecond + " b/s each";
    }

    private static Bot spawn(ServerLevel level, double x, double z, double dx, double dz, int index) {
        // A client's render distance (the default would be 2 chunks); the server caps it at its own view distance.
        var info = new net.minecraft.server.level.ClientInformation("en_us", 32, net.minecraft.world.entity.player.ChatVisiblity.FULL, true, 0,
            net.minecraft.world.entity.HumanoidArm.RIGHT, false, false);
        CommonListenerCookie cookie = new CommonListenerCookie(new GameProfile(UUID.randomUUID(), "walk-bot-" + index), 0, info, false,
            net.neoforged.neoforge.network.connection.ConnectionType.NEOFORGE);
        ServerPlayer bot = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), info);
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        EmbeddedChannel channel = new EmbeddedChannel(connection);
        net.neoforged.neoforge.network.registration.NetworkRegistry.configureMockConnection(connection); // as if a full NeoForge client
        level.getServer().getPlayerList().placeNewPlayer(connection, bot, cookie);
        // Packets are still built (chunk serialization is part of the load) but go nowhere: mods' payloads would
        // throw on a connection that never negotiated their channels.
        bot.connection = new net.minecraft.server.network.ServerGamePacketListenerImpl(level.getServer(), connection, bot, cookie) {
            @Override
            public void send(net.minecraft.network.protocol.Packet<?> packet) {
            }

            @Override
            public void send(net.minecraft.network.protocol.Packet<?> packet, net.minecraft.network.PacketSendListener listener) {
            }
        };
        bot.setInvulnerable(true);
        bot.teleportTo(level, x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) x, (int) z), z, -90, 0);
        return new Bot(bot, channel, dx, dz);
    }

    static String stop() {
        if (BOTS.isEmpty()) {
            return "no bot";
        }
        double asked = ticks * speed * BOTS.size();
        String report = BOTS.size() + " bots walked " + (int) walked + " of " + (int) asked + " blocks (" + percent(walked, asked) + ") in "
            + ticks / 20 + " s, ticks over 50 ms: " + (int) slowTicks;
        Expedition.LOG.info("Bench: {}", report);
        BOTS.forEach(bot -> {
            bot.player().getServer().getPlayerList().remove(bot.player());
            bot.channel().close();
        });
        BOTS.clear();
        return report;
    }

    private static String percent(double part, double whole) {
        return whole <= 0 ? "-" : Math.round(100 * part / whole) + "%";
    }

    @SubscribeEvent
    static void onTick(ServerTickEvent.Post event) {
        if (BOTS.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        ticks++;
        long nanos = server.getTickTimesNanos()[server.getTickCount() % 100];
        maxTickNanos = Math.max(maxTickNanos, nanos);
        if (nanos > 50_000_000L) {
            slowTicks++;
        }
        for (Bot bot : BOTS) {
            bot.channel().releaseOutbound(); // drop the packets a client would have received
            ServerPlayer player = bot.player();
            ServerLevel level = player.serverLevel();
            looks++;
            if (level.hasChunk((int) Math.floor(player.getX() + VIEW_AHEAD * bot.dx()) >> 4, (int) Math.floor(player.getZ() + VIEW_AHEAD * bot.dz()) >> 4)) {
                seen++;
            }
            double x = player.getX() + speed * bot.dx();
            double z = player.getZ() + speed * bot.dz();
            if (!level.hasChunk((int) Math.floor(x) >> 4, (int) Math.floor(z) >> 4)) {
                continue; // waits for the chunk ahead like a player would stop at the edge of generated land
            }
            player.teleportTo(level, x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(x), (int) Math.floor(z)), z, -90, 0);
            level.getChunkSource().move(player); // a client's movement packet does this: chunk loading follows the player
            walked += speed;
        }
        if (ticks % REPORT_TICKS == 0) {
            long now = System.nanoTime();
            Expedition.LOG.info("Bench t={}s: tps {}, mspt avg {} max {}, bots at {} of the asked speed, view ahead ready {}, {} chunks loaded",
                ticks / 20, String.format("%.1f", REPORT_TICKS * 1e9 / (now - reportNanos)), String.format("%.1f", server.getAverageTickTimeNanos() / 1e6), String.format("%.1f", maxTickNanos / 1e6),
                percent(walked - walkedAtReport, REPORT_TICKS * speed * BOTS.size()), percent(seen, looks),
                server.overworld().getChunkSource().getLoadedChunksCount());
            looks = 0;
            seen = 0;
            walkedAtReport = walked;
            maxTickNanos = 0;
            reportNanos = now;
        }
    }
}
