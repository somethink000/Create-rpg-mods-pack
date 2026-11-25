package net.expedition.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.expedition.Expedition;
import net.expedition.garrison.GarrisonData;
import net.expedition.garrison.GarrisonRecord;
import net.expedition.garrison.Garrisons;
import net.expedition.world.StructurePlacer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceKeyArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import java.util.List;
import net.expedition.content.ChestLoot;
import net.expedition.garrison.GarrisonDrops;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.expedition.route.RoutePlacer;
import net.expedition.route.RouteStep;
import net.expedition.route.RouteSteps;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Admin/test commands: {@code /expedition place <structure> <pos>}, {@code /expedition garrison <pos>}. */
@EventBusSubscriber(modid = Expedition.MOD_ID)
public final class ExpeditionCommand {
    private ExpeditionCommand() {
    }

    @SubscribeEvent
    static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("expedition").requires(source -> source.hasPermission(2))
            .then(Commands.literal("place")
                .then(Commands.argument("structure", ResourceKeyArgument.key(Registries.STRUCTURE))
                    .then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(context -> place(context.getSource(),
                            ResourceKeyArgument.getStructure(context, "structure"), BlockPosArgument.getBlockPos(context, "pos"))))))
            .then(Commands.literal("trial")
                .executes(context -> { net.expedition.route.Trial.start(context.getSource().getServer(), ""); return 1; })
                .then(Commands.argument("step", StringArgumentType.word())
                    .executes(context -> {
                        try {
                            net.expedition.route.Trial.start(context.getSource().getServer(), StringArgumentType.getString(context, "step"));
                        } catch (RuntimeException e) {
                            Expedition.LOG.error("Trial: can't start", e);
                            throw e;
                        }
                        return 1;
                    })))
            .then(Commands.literal("tour")
                .executes(context -> { net.expedition.route.Tour.list(context.getSource().getPlayerOrException()); return 1; })
                .then(Commands.literal("list").executes(context -> { net.expedition.route.Tour.list(context.getSource().getPlayerOrException()); return 1; }))
                .then(Commands.literal("next").executes(context -> { net.expedition.route.Tour.step(context.getSource().getPlayerOrException(), 1); return 1; }))
                .then(Commands.literal("prev").executes(context -> { net.expedition.route.Tour.step(context.getSource().getPlayerOrException(), -1); return 1; }))
                .then(Commands.argument("number", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1))
                    .executes(context -> { net.expedition.route.Tour.go(context.getSource().getPlayerOrException(),
                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "number")); return 1; })))
            .then(Commands.literal("route")
                .then(Commands.literal("testall")
                    .then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(context -> testRoute(context.getSource(), BlockPosArgument.getBlockPos(context, "pos"), ""))))
                .then(Commands.literal("test")
                    .then(Commands.argument("step", StringArgumentType.word())
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                            .executes(context -> testRoute(context.getSource(), BlockPosArgument.getBlockPos(context, "pos"),
                                StringArgumentType.getString(context, "step")))))))
            .then(Commands.literal("audit").then(Commands.literal("gear").executes(context -> auditGear(context.getSource())))
                .then(Commands.literal("entities").executes(context -> auditEntities(context.getSource())))
                .then(Commands.literal("blocks").then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(context -> {
                    // Block entities (spawners, altars, chests) in loaded chunks within 96 blocks: where a dungeon's boss comes from.
                    ServerLevel level = context.getSource().getLevel();
                    BlockPos pos = BlockPosArgument.getBlockPos(context, "pos");
                    StringBuilder out = new StringBuilder();
                    for (int cx = (pos.getX() - 96) >> 4; cx <= (pos.getX() + 96) >> 4; cx++) {
                        for (int cz = (pos.getZ() - 96) >> 4; cz <= (pos.getZ() + 96) >> 4; cz++) {
                            if (level.hasChunk(cx, cz)) {
                                for (BlockPos at : level.getChunk(cx, cz).getBlockEntitiesPos()) {
                                    String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(level.getBlockState(at).getBlock()).toString();
                                    if (!id.contains("chest")) {
                                        out.append(id).append('@').append(at.toShortString()).append("; ");
                                    }
                                }
                            }
                        }
                    }
                    String text = out.isEmpty() ? "no block entities but chests" : out.toString();
                    context.getSource().sendSuccess(() -> Component.literal(text), false);
                    return 1;
                })))
                .then(Commands.literal("garrisons").executes(context -> auditGarrisons(context.getSource())))
                .then(Commands.literal("guns").executes(context -> auditGuns(context.getSource())))
                .then(Commands.literal("recipes").executes(context -> auditRecipes(context.getSource())))
                .then(Commands.literal("chests").executes(context -> auditChests(context.getSource())))
                .then(Commands.literal("ingredients").executes(context -> auditIngredients(context.getSource())))
                .then(Commands.literal("spawns").executes(context -> {
                    // Where dungeons make mobs besides spawner blocks: structures' own spawn lists, and spawner-like block entities of mods.
                    var server = context.getSource().getServer();
                    var structures = server.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE);
                    StringBuilder out = new StringBuilder("structure\tcategory\tbox\tmobs\n");
                    structures.entrySet().stream().sorted(java.util.Comparator.comparing(e -> e.getKey().location().toString())).forEach(e -> e.getValue().spawnOverrides().forEach((category, override) ->
                        out.append(e.getKey().location()).append('\t').append(category.getName()).append('\t').append(override.boundingBox().getSerializedName()).append('\t')
                            .append(override.spawns().unwrap().stream().map(data -> net.minecraft.world.entity.EntityType.getKey(data.type) + "*" + data.getWeight().asInt())
                                .collect(java.util.stream.Collectors.joining(" "))).append('\n')));
                    out.append("\nblock entity types like spawners\n");
                    net.minecraft.core.registries.BuiltInRegistries.BLOCK_ENTITY_TYPE.keySet().stream().map(Object::toString)
                        .filter(id -> id.matches(".*(spawn|summon|generator|altar|nest|hive|portal).*")).sorted().forEach(id -> out.append(id).append('\n'));
                    try {
                        java.nio.file.Files.writeString(server.getServerDirectory().resolve("spawns.tsv"), out);
                    } catch (java.io.IOException e) {
                        throw new RuntimeException(e);
                    }
                    context.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("spawns.tsv written"), false);
                    return 1;
                }))
                .then(Commands.literal("blockstates").executes(context -> {
                    // Block states per mod: each costs memory on both sides (its shapes) and, on the client, its baked model.
                    java.util.Map<String, int[]> perMod = new java.util.TreeMap<>();
                    for (var block : net.minecraft.core.registries.BuiltInRegistries.BLOCK) {
                        int[] count = perMod.computeIfAbsent(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block).getNamespace(), k -> new int[2]);
                        count[0]++;
                        count[1] += block.getStateDefinition().getPossibleStates().size();
                    }
                    StringBuilder out = new StringBuilder("mod\tblocks\tstates\n");
                    perMod.entrySet().stream().sorted((a, b) -> b.getValue()[1] - a.getValue()[1])
                        .forEach(e -> out.append(e.getKey()).append('\t').append(e.getValue()[0]).append('\t').append(e.getValue()[1]).append('\n'));
                    out.append("\nblock\tstates\tproperties\n");
                    net.minecraft.core.registries.BuiltInRegistries.BLOCK.stream()
                        .sorted((a, b) -> b.getStateDefinition().getPossibleStates().size() - a.getStateDefinition().getPossibleStates().size()).limit(40)
                        .forEach(block -> out.append(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block)).append('\t')
                            .append(block.getStateDefinition().getPossibleStates().size()).append('\t')
                            .append(block.getStateDefinition().getProperties().stream().map(property -> property.getName() + "=" + property.getPossibleValues().size())
                                .collect(java.util.stream.Collectors.joining(" "))).append('\n'));
                    try {
                        java.nio.file.Files.writeString(context.getSource().getServer().getServerDirectory().resolve("blockstates.tsv"), out);
                    } catch (java.io.IOException e) {
                        throw new RuntimeException(e);
                    }
                    context.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("blockstates.tsv written"), false);
                    return 1;
                }))
                .then(Commands.literal("loot").executes(context -> auditLoot(context.getSource())))
                .then(Commands.literal("mobdrops").executes(context -> auditMobDrops(context.getSource())))
                .then(Commands.literal("obtainable").executes(context -> auditObtainable(context.getSource())))
                .then(Commands.literal("mobs").executes(context -> {
                    var soldiers = net.expedition.garrison.Garrisons.soldiers();
                    StringBuilder out = new StringBuilder("mod\tcount\tmonsters\n");
                    soldiers.forEach((mod, types) -> out.append(mod).append('\t').append(types.size()).append('\t').append(String.join(" ", types)).append('\n'));
                    try {
                        java.nio.file.Files.writeString(context.getSource().getServer().getServerDirectory().resolve("garrison_soldiers.tsv"), out);
                    } catch (java.io.IOException e) {
                        throw new RuntimeException(e);
                    }
                    int total = soldiers.values().stream().mapToInt(java.util.List::size).sum();
                    context.getSource().sendSuccess(() -> Component.literal("Garrison soldiers: " + total + " types -> garrison_soldiers.tsv"), false);
                    return total;
                })))
            .then(Commands.literal("biomemap")
                .then(Commands.argument("radius", com.mojang.brigadier.arguments.IntegerArgumentType.integer(64, 20000))
                    .then(Commands.argument("step", com.mojang.brigadier.arguments.IntegerArgumentType.integer(4, 256))
                        .executes(context -> biomeMap(context, 80, true))
                        .then(Commands.argument("y", com.mojang.brigadier.arguments.IntegerArgumentType.integer(-512, 512))
                            .executes(context -> biomeMap(context, com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "y"), false))))))
            .then(Commands.literal("prologue").executes(context -> {
                // Places the prologue's lairs (if not yet) and logs each shot as it would be filmed.
                net.expedition.route.Prologue.survey(context.getSource().getLevel(), text -> {
                    net.expedition.Expedition.LOG.info("Prologue shot: {}", text);
                    context.getSource().sendSuccess(() -> Component.literal("Prologue shot: " + text), false);
                });
                return 1;
            }))
            .then(Commands.literal("topview")
                .then(Commands.argument("radius", com.mojang.brigadier.arguments.IntegerArgumentType.integer(16, 2048))
                    .executes(context -> topView(context, null))
                    .then(Commands.argument("y", com.mojang.brigadier.arguments.IntegerArgumentType.integer(-64, 320))
                        .executes(context -> topView(context, com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "y"))))))
            .then(Commands.literal("slice")
                .then(Commands.argument("length", com.mojang.brigadier.arguments.IntegerArgumentType.integer(64, 20000))
                    .then(Commands.argument("step", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 64))
                        .executes(context -> {
                            CommandSourceStack source = context.getSource();
                            BlockPos at = BlockPos.containing(source.getPosition());
                            int length = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "length");
                            int step = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "step");
                            new Thread(() -> {
                                try {
                                    Expedition.LOG.info("biomemap: {}", TerrainSlice.render(source.getLevel(), at.getX(), at.getZ(), length, step));
                                } catch (Exception e) {
                                    Expedition.LOG.error("biomemap failed (slice)", e);
                                }
                            }, "expedition-slice").start();
                            return 1;
                        }))))
            .then(Commands.literal("bot")
                .then(Commands.literal("stop").executes(context -> {
                    String report = WalkBot.stop();
                    context.getSource().sendSuccess(() -> Component.literal(report), false);
                    return 1;
                }))
                .then(Commands.argument("speed", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(0.1, 100))
                    .executes(context -> startBots(context, 1))
                    .then(Commands.argument("count", IntegerArgumentType.integer(1, 16))
                        .executes(context -> startBots(context, IntegerArgumentType.getInteger(context, "count"))))))
            .then(Commands.literal("drops")
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                    .executes(context -> drops(context.getSource(), BlockPosArgument.getBlockPos(context, "pos")))))
            .then(Commands.literal("garrison")
                .then(Commands.literal("scan").then(Commands.argument("pos", BlockPosArgument.blockPos())
                    .executes(context -> {
                        ServerLevel level = context.getSource().getLevel();
                        BlockPos pos = BlockPosArgument.getBlockPos(context, "pos");
                        Garrisons.dungeonsAt(level, pos).forEach(dungeon -> Garrisons.scanDungeon(level, dungeon));
                        Garrisons.dungeonsAt(level, pos).forEach(dungeon -> context.getSource().sendSuccess(() ->
                            Component.literal(dungeon.key() + " eligible=" + Garrisons.eligible(level, dungeon) + " box=" + dungeon.box().getXSpan() + "x"
                                + dungeon.box().getYSpan() + "x" + dungeon.box().getZSpan() + " pieces=" + dungeon.start().getPieces().size() + " floor=" + Garrisons.floorArea(level, dungeon) + " budget=" + Garrisons.budgetFor(Garrisons.floorArea(level, dungeon), 0, 0) + "+loot"), false));
                        return garrison(context.getSource(), pos);
                    })))
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                    .executes(context -> garrison(context.getSource(), BlockPosArgument.getBlockPos(context, "pos"))))));
    }

    private static int topView(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context, Integer y) {
        CommandSourceStack source = context.getSource();
        BlockPos center = BlockPos.containing(source.getPosition());
        try {
            String result = TopView.render(source.getLevel(), center.getX(), center.getZ(),
                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "radius"), y);
            Expedition.LOG.info("biomemap: {}", result);
            source.sendSuccess(() -> Component.literal(result), false);
            return 1;
        } catch (Exception e) {
            Expedition.LOG.error("biomemap failed (topview)", e);
            return 0;
        }
    }

    private static int biomeMap(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context, int y, boolean surface) {
        CommandSourceStack source = context.getSource();
        BlockPos center = BlockPos.containing(source.getPosition());
        int radius = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "radius");
        int step = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "step");
        // Off the server thread: terrain-height sampling of a large map takes longer than the watchdog allows.
        new Thread(() -> {
            try {
                String result = BiomeMap.render(source.getLevel(), center.getX(), center.getZ(), radius, step, y, surface);
                Expedition.LOG.info("biomemap: {}", result);
                source.getServer().execute(() -> source.sendSuccess(() -> Component.literal(result), false));
            } catch (Exception e) {
                Expedition.LOG.error("biomemap failed", e);
            }
        }, "expedition-biomemap").start();
        source.sendSuccess(() -> Component.literal("biomemap started, result in the log (biomemap: ...)"), false);
        return 1;
    }

    private static int place(CommandSourceStack source, Holder.Reference<Structure> structure, BlockPos pos) {
        StructureStart start = StructurePlacer.place(source.getLevel(), structure.value(), pos);
        if (start == null) {
            source.sendFailure(Component.literal("Structure " + structure.key().location() + " can't generate at " + pos.toShortString()));
            return 0;
        }
        var box = start.getBoundingBox();
        source.sendSuccess(() -> Component.literal("Placed " + structure.key().location() + " box " + box.minX() + "," + box.minY() + "," + box.minZ()
            + " .. " + box.maxX() + "," + box.maxY() + "," + box.maxZ() + ", pieces " + start.getPieces().size()), true);
        return 1;
    }

    /** Places every route step's dungeon in turn around {@code center} and logs where (or that it failed). */
    private static int testRoute(CommandSourceStack source, BlockPos center, String only) {
        RouteSteps.all().stream().filter(entry -> net.expedition.route.BossLair.applies(entry.step())
            && (only.isEmpty() || entry.id().getPath().equals(only))).forEach(entry -> {
                ServerLevel level = source.getServer().overworld();
                BlockPos lair = net.expedition.route.BossLair.find(level, entry.step(), center);
                Expedition.LOG.info("Route test {}: lair {} (realm {}), {} blocks away", entry.id().getPath(), lair == null ? "NOT FOUND" : lair.toShortString(),
                    lair == null ? "-" : net.expedition.world.realm.RealmWorld.realmAt(level, lair), lair == null ? 0 : (int) Math.sqrt(lair.distSqr(center)));
            });
        List<RouteSteps.Entry> steps = RouteSteps.all().stream()
            .filter(entry -> !entry.step().structures().isEmpty() && (only.isEmpty() || entry.id().getPath().equals(only))).toList();
        source.sendSuccess(() -> Component.literal("Testing " + steps.size() + " route steps, see the server log"), false);
        testStep(source.getServer(), steps, 0, center);
        return steps.size();
    }

    private static void testStep(MinecraftServer server, List<RouteSteps.Entry> steps, int index, BlockPos center) {
        if (index >= steps.size()) {
            Expedition.LOG.info("Route test: done");
            return;
        }
        RouteSteps.Entry entry = steps.get(index);
        RouteStep step = entry.step();
        ServerLevel level = net.expedition.route.Route.levelFor(server, step);
        var realm = net.expedition.world.realm.RealmWorld.realmFor(level, step.dimension());
        long started = System.currentTimeMillis();
        RoutePlacer.request(level, step, center, realm, result -> {
            long ms = System.currentTimeMillis() - started;
            if (result == null) {
                Expedition.LOG.warn("Route test {}: FAILED ({} ms)", entry.id().getPath(), ms);
            } else {
                // As built: how much of it stands in water (3x3 points over its frame), and its biome.
                var box = Garrisons.dungeonsAt(level, result.pos()).stream().filter(d -> d.key().equals(result.dungeonKey())).findFirst()
                    .map(Garrisons.Dungeon::box).orElse(null);
                int wet = 0;
                // By the terrain before the dungeon (as the placer judges it): a ship or a sunken city covers its own water.
                if (box != null) {
                    wet = RoutePlacer.waterShare(level, box);
                }
                String biome = level.getBiome(result.pos()).unwrapKey().map(key -> key.location().toString()).orElse("?");
                Expedition.LOG.info("Route test {}: {} at {} ({}, realm {}), {} blocks away, {} ms, water {}/9, biome {}", entry.id().getPath(), result.dungeonKey(),
                    result.pos().toShortString(), result.dimension(), net.expedition.world.realm.RealmWorld.realmAt(level, result.pos()),
                    (int) Math.sqrt(result.pos().distSqr(center)), ms, box == null ? "?" : wet, biome);
            }
            testStep(server, steps, index + 1, center);
        });
    }

    /** Every gun's loaded weapon data (NTGL's, by reflection: no compile dependency), to guns.txt: what the game really fires. */
    private static int auditGuns(CommandSourceStack source) {
        StringBuilder out = new StringBuilder();
        int count = 0;
        for (var item : net.minecraft.core.registries.BuiltInRegistries.ITEM) {
            String namespace = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).getNamespace();
            if (!namespace.equals("cgs") && !namespace.equals("ntgl")) {
                continue; // looking up methods loads classes: client-only ones of other mods' items log errors on a server
            }
            try {
                var getConfig = item.getClass().getMethod("getConfig");
                Object config = getConfig.invoke(item);
                if (config == null) {
                    continue;
                }
                Object tag = config.getClass().getMethod("serializeNBT", net.minecraft.core.HolderLookup.Provider.class)
                    .invoke(config, source.getServer().registryAccess());
                out.append(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item)).append('\t').append(tag).append('\n');
                count++;
            } catch (NoSuchMethodException ignored) {
                // not a gun
            } catch (ReflectiveOperationException | RuntimeException error) {
                out.append(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item)).append("\tERROR ").append(error).append('\n');
            }
        }
        try {
            java.nio.file.Files.writeString(source.getServer().getServerDirectory().resolve("guns.txt"), out);
        } catch (java.io.IOException error) {
            throw new RuntimeException(error);
        }
        int guns = count;
        source.sendSuccess(() -> Component.literal("guns.txt: " + guns + " guns"), false);
        return guns;
    }

    /** Rolls a garrison mob's part drop 1000 times at a position and prints what came out. */
    private static int drops(CommandSourceStack source, BlockPos pos) {
        ServerLevel level = source.getLevel();
        double distance = ChestLoot.tierDistance(level, Vec3.atCenterOf(pos));
        java.util.Map<String, Integer> totals = new java.util.TreeMap<>();
        int empty = 0;
        for (int i = 0; i < 1000; i++) {
            ItemStack stack = GarrisonDrops.roll(level.random, distance);
            if (stack.isEmpty()) {
                empty++;
            } else {
                totals.merge(stack.getItem().toString(), stack.getCount(), Integer::sum);
            }
        }
        int nothing = empty;
        source.sendSuccess(() -> Component.literal("Distance " + (int) distance + ": nothing " + nothing + "/1000, items " + totals), false);
        return 1;
    }

    private static int garrison(CommandSourceStack source, BlockPos pos) throws CommandSyntaxException {
        ServerLevel level = source.getLevel();
        var dungeons = Garrisons.dungeonsAt(level, pos);
        if (dungeons.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No dungeon at " + pos.toShortString()), false);
            return 0;
        }
        for (Garrisons.Dungeon dungeon : dungeons) {
            GarrisonRecord record = GarrisonData.get(level).find(dungeon.key());
            String state = record == null ? "unseen"
                : record.state + " killed=" + record.killed + "/" + record.total + " alive=" + record.alive + " spawners=" + record.spawners
                + " chests=" + record.chests + " types=" + record.spawnerTypes;
            var mobs = Garrisons.mobWeights(level, record != null ? record : new GarrisonRecord(), dungeon, pos);
            net.expedition.Expedition.LOG.info("Garrison {}: {} fields {}", dungeon.key(), state, mobs);
            source.sendSuccess(() -> Component.literal(dungeon.key() + ": " + state + ", protected=" + Garrisons.isProtected(level, pos) + ", mobs " + mobs), false);
        }
        return dungeons.size();
    }

    /** Writes every item's combat stats (attack damage/speed, armor, toughness) and how it is obtained to gear_audit.tsv. */
    /** Mods whose things are Create's world: their parts count as Create in a recipe. */
    private static boolean createWorld(String namespace) {
        return namespace.startsWith("create") || namespace.equals("tfmg") || namespace.equals("aeronautics") || namespace.equals("simulated")
            || namespace.equals("offroad") || namespace.equals("railways") || namespace.equals("rubberworks") || namespace.equals("copycats")
            || namespace.equals("bits_n_bobs") || namespace.equals("expedition") || namespace.equals("cosmonautics") || namespace.equals("rocketnautics")
            || namespace.equals("aeronautics_utility_objects") || namespace.equals("dndesires") || namespace.equals("interiors") || namespace.equals("createbigcannons");
    }

    /**
     * Every item some recipe makes and how it is tied to Create: made on a Create machine, made of Create's (or its
     * addons') parts, or neither (a plain table or furnace craft of plain things). -> recipe_audit.tsv
     */
    private static int auditRecipes(CommandSourceStack source) {
        var server = source.getServer();
        var access = server.registryAccess();
        java.util.Map<net.minecraft.world.item.Item, String[]> verdict = new java.util.TreeMap<>(
            java.util.Comparator.comparing(item -> net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).toString()));
        for (var holder : server.getRecipeManager().getRecipes()) {
            var result = holder.value().getResultItem(access);
            if (result.isEmpty()) {
                continue;
            }
            String type = String.valueOf(net.minecraft.core.registries.BuiltInRegistries.RECIPE_TYPE.getKey(holder.value().getType()));
            boolean machine = createWorld(type.split(":")[0]);
            boolean parts = false;
            for (var ingredient : holder.value().getIngredients()) {
                for (var stack : ingredient.getItems()) {
                    parts |= createWorld(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace());
                }
            }
            String level = machine ? "machine" : parts ? "parts" : "plain";
            String[] old = verdict.get(result.getItem());
            int rank = level.equals("machine") ? 2 : level.equals("parts") ? 1 : 0;
            int oldRank = old == null ? -1 : old[0].equals("machine") ? 2 : old[0].equals("parts") ? 1 : 0;
            if (rank > oldRank) {
                verdict.put(result.getItem(), new String[] {level, type, holder.id().toString()});
            } else if (old != null && !old[1].contains(type)) {
                old[1] = old[1] + "," + type;
            }
        }
        StringBuilder out = new StringBuilder("item\tlink\ttypes\trecipe\tblock\n");
        verdict.forEach((item, v) -> out.append(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item)).append('\t').append(v[0]).append('\t')
            .append(v[1]).append('\t').append(v[2]).append('\t').append(item instanceof net.minecraft.world.item.BlockItem).append('\n'));
        try {
            java.nio.file.Files.writeString(server.getServerDirectory().resolve("recipe_audit.tsv"), out.toString());
        } catch (java.io.IOException error) {
            source.sendFailure(Component.literal(error.toString()));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Recipe audit: " + verdict.size() + " items -> recipe_audit.tsv"), false);
        return verdict.size();
    }

    private static int startBots(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context, int count) {
        var source = context.getSource();
        String report;
        try {
            report = WalkBot.start(source.getLevel(), source.getPosition().x, source.getPosition().z,
                com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(context, "speed"), count);
        } catch (RuntimeException e) {
            Expedition.LOG.error("walk-bot failed", e);
            throw e;
        }
        source.sendSuccess(() -> Component.literal(report), false);
        return 1;
    }

    /** Loaded entities by type over every level, most first, and how many of them are persistent (never despawn). */
    /** Every dungeon garrison the world has started: its size, state and mob table (garrisons.tsv), and a summary. */
    private static int auditGarrisons(CommandSourceStack source) {
        StringBuilder out = new StringBuilder("dungeon\tstate\ttotal\talive\tkilled\tspawners\tchests\tng\tmobs\n");
        java.util.Map<String, Integer> states = new java.util.TreeMap<>();
        int empty = 0;
        for (ServerLevel level : source.getServer().getAllLevels()) {
            for (var e : GarrisonData.get(level).all().entrySet()) {
                GarrisonRecord r = e.getValue();
                states.merge(r.state.name(), 1, Integer::sum);
                if (r.state != GarrisonRecord.State.PENDING && r.total == 0) {
                    empty++;
                }
                out.append(e.getKey()).append('\t').append(r.state).append('\t').append(r.total).append('\t').append(r.alive).append('\t')
                    .append(r.killed).append('\t').append(r.spawners).append('\t').append(r.chests).append('\t').append(r.ng).append('\t')
                    .append(r.mobs).append('\n');
            }
        }
        try {
            java.nio.file.Files.writeString(source.getServer().getServerDirectory().resolve("garrisons.tsv"), out);
        } catch (java.io.IOException e) {
            Expedition.LOG.error("Can't write garrisons.tsv", e);
        }
        String summary = "garrisons: " + states + ", started with no mobs: " + empty + " -> garrisons.tsv";
        Expedition.LOG.info("Audit: {}", summary);
        source.sendSuccess(() -> Component.literal(summary), false);
        return 1;
    }

    private static int auditEntities(CommandSourceStack source) {
        var counts = new java.util.HashMap<String, int[]>();
        for (ServerLevel level : source.getServer().getAllLevels()) {
            for (var entity : level.getAllEntities()) {
                int[] c = counts.computeIfAbsent(net.minecraft.world.entity.EntityType.getKey(entity.getType()).toString(), k -> new int[2]);
                c[0]++;
                if (entity instanceof net.minecraft.world.entity.Mob mob && (mob.isPersistenceRequired() || mob.requiresCustomPersistence())) {
                    c[1]++;
                }
            }
        }
        StringBuilder out = new StringBuilder("entities: " + counts.values().stream().mapToInt(c -> c[0]).sum());
        counts.entrySet().stream().sorted((x, y) -> y.getValue()[0] - x.getValue()[0]).limit(25)
            .forEach(e -> out.append("\n").append(e.getValue()[0]).append(" ").append(e.getKey()).append(" (persistent ").append(e.getValue()[1]).append(")"));
        Expedition.LOG.info("Audit: {}", out);
        source.sendSuccess(() -> Component.literal(out.toString()), false);
        return 1;
    }

    private static int auditGear(CommandSourceStack source) {
        var server = source.getServer();
        var made = new java.util.HashMap<net.minecraft.world.item.Item, String>();
        var types = new java.util.HashMap<net.minecraft.world.item.Item, java.util.TreeSet<String>>();
        for (var holder : server.getRecipeManager().getRecipes()) {
            var result = holder.value().getResultItem(server.registryAccess()).getItem();
            made.merge(result, holder.id().toString(), (a, b) -> a.length() <= b.length() ? a : b);
            types.computeIfAbsent(result, k -> new java.util.TreeSet<>()).add(String.valueOf(net.minecraft.core.registries.BuiltInRegistries.RECIPE_SERIALIZER.getKey(holder.value().getSerializer())));
        }
        var removed = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM,
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(net.expedition.Expedition.MOD_ID, "removed"));
        StringBuilder out = new StringBuilder("item\tdamage\tspeed\tarmor\ttoughness\tdurability\tsource\trecipe_types\n");
        int count = 0;
        for (var item : net.minecraft.core.registries.BuiltInRegistries.ITEM) {
            var stack = item.getDefaultInstance();
            var modifiers = stack.getAttributeModifiers();
            double damage = 0, speed = 0, armor = 0, toughness = 0;
            for (var entry : modifiers.modifiers()) {
                double amount = entry.modifier().amount();
                if (entry.attribute().is(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)) damage += amount;
                else if (entry.attribute().is(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED)) speed += amount;
                else if (entry.attribute().is(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR)) armor += amount;
                else if (entry.attribute().is(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS)) toughness += amount;
            }
            if (damage == 0 && armor == 0) {
                continue;
            }
            String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).toString();
            String how = stack.is(removed) ? "REMOVED" : made.getOrDefault(item, "none");
            out.append(String.format(java.util.Locale.ROOT, "%s\t%.1f\t%.2f\t%.1f\t%.1f\t%d\t%s\t%s%n", id, damage + (damage > 0 ? 1 : 0), 4 + speed, armor, toughness, stack.getMaxDamage(), how,
                String.join(",", types.getOrDefault(item, new java.util.TreeSet<>()))));
            count++;
        }
        try {
            java.nio.file.Files.writeString(server.getServerDirectory().resolve("gear_audit.tsv"), out);
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
        int written = count;
        source.sendSuccess(() -> Component.literal("Gear audit: " + written + " items -> gear_audit.tsv"), false);
        return count;
    }

    /**
     * Simulates 1000 dungeon chests (6 rolls) per realm and tier and writes loot_sim.tsv: per item and realm/tier, the
     * share of chests holding it (%) and the mean amount there. Then loot_needs.tsv: every single-item ingredient of the
     * pack's own recipes (its gear, artifacts, ammunition), how much a recipe takes, and where it comes from (a recipe, a
     * story boss, chests: the best realm/tier and the chance per chest), MISSING if from nowhere.
     */
    private static int auditChests(CommandSourceStack source) {
        double[] distances = {500, 2000, 4000, 6000};
        var realms = net.expedition.world.realm.Realm.values();
        int columns = realms.length * distances.length;
        java.util.Map<net.minecraft.world.item.Item, int[]> chests = new java.util.HashMap<>();
        java.util.Map<net.minecraft.world.item.Item, long[]> amounts = new java.util.HashMap<>();
        var random = net.minecraft.util.RandomSource.create(42);
        for (int r = 0; r < realms.length; r++) {
            for (int tier = 0; tier < distances.length; tier++) {
                int column = r * distances.length + tier;
                for (int chest = 0; chest < 1000; chest++) {
                    for (ItemStack stack : net.expedition.content.DungeonLoot.generate(random, distances[tier], 6, net.expedition.content.DungeonLoot.EPIC,
                        realms[r], 0, "", java.util.Set.of())) {
                        chests.computeIfAbsent(stack.getItem(), k -> new int[columns])[column]++;
                        amounts.computeIfAbsent(stack.getItem(), k -> new long[columns])[column] += stack.getCount();
                    }
                }
            }
        }
        String[] names = {"junk", "common", "uncommon", "rare", "epic"};
        java.util.function.Function<net.minecraft.world.item.Item, String> valueOf = item -> {
            for (int v = 0; v < names.length; v++) {
                if (net.expedition.content.DungeonLoot.pool(v).contains(item)) {
                    return names[v];
                }
            }
            return "-";
        };
        StringBuilder sim = new StringBuilder("item\tvalue");
        for (var realm : realms) {
            for (int tier = 0; tier < distances.length; tier++) {
                sim.append('\t').append(realm.name().toLowerCase()).append("_t").append(tier).append("%/avg");
            }
        }
        sim.append('\n');
        chests.keySet().stream().sorted(java.util.Comparator.comparing(item -> net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).toString()))
            .forEach(item -> {
                sim.append(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item)).append('\t').append(valueOf.apply(item));
                int[] counts = chests.get(item);
                long[] sums = amounts.get(item);
                for (int c = 0; c < columns; c++) {
                    sim.append('\t').append(counts[c] / 10.0).append('/').append(counts[c] == 0 ? 0 : Math.round(10.0 * sums[c] / counts[c]) / 10.0);
                }
                sim.append('\n');
            });
        // Where each ingredient of the pack's recipes comes from.
        MinecraftServer server = source.getServer();
        var registries = server.registryAccess();
        java.util.Set<net.minecraft.world.item.Item> made = new java.util.HashSet<>();
        server.getRecipeManager().getRecipes().forEach(holder -> made.add(holder.value().getResultItem(registries).getItem()));
        java.util.Map<net.minecraft.world.item.Item, java.util.Set<String>> bosses = new java.util.HashMap<>();
        for (var entry : RouteSteps.all()) {
            String step = entry.id().getPath();
            entry.step().heart().forEach(id -> bosses.computeIfAbsent(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id), k -> new java.util.TreeSet<>()).add(step));
            entry.step().loot().forEach(id -> bosses.computeIfAbsent(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id), k -> new java.util.TreeSet<>()).add(step));
        }
        java.util.Map<net.minecraft.world.item.Item, int[]> needs = new java.util.TreeMap<>(
            java.util.Comparator.comparing(item -> net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).toString()));
        for (var holder : server.getRecipeManager().getRecipes()) {
            if (!holder.id().getNamespace().equals(Expedition.MOD_ID)) {
                continue;
            }
            java.util.Map<net.minecraft.world.item.Item, Integer> inThis = new java.util.HashMap<>();
            for (var ingredient : holder.value().getIngredients()) {
                ItemStack[] options = ingredient.getItems();
                if (options.length == 1) {
                    inThis.merge(options[0].getItem(), options[0].getCount(), Integer::sum);
                }
            }
            inThis.forEach((item, count) -> {
                int[] need = needs.computeIfAbsent(item, k -> new int[2]);
                need[0]++;
                need[1] = Math.max(need[1], count);
            });
        }
        StringBuilder out = new StringBuilder("item\trecipes\tmax_per_recipe\tsource\tbest_chest\tchance_per_chest%\n");
        int missing = 0;
        for (var entry : needs.entrySet()) {
            var item = entry.getKey();
            int[] counts = chests.get(item);
            int best = -1;
            if (counts != null) {
                for (int c = 0; c < columns; c++) {
                    if (best < 0 || counts[c] > counts[best]) {
                        best = c;
                    }
                }
            }
            java.util.List<String> sources = new java.util.ArrayList<>();
            if (made.contains(item)) {
                sources.add("recipe");
            }
            if (bosses.containsKey(item)) {
                sources.add("boss:" + String.join(",", bosses.get(item)));
            }
            if (counts != null) {
                sources.add("chest");
            }
            if (sources.isEmpty()) {
                sources.add("MISSING");
                missing++;
            }
            out.append(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item)).append('\t').append(entry.getValue()[0]).append('\t')
                .append(entry.getValue()[1]).append('\t').append(String.join(" ", sources)).append('\t')
                .append(best < 0 ? "-" : realms[best / distances.length].name().toLowerCase() + "_t" + best % distances.length).append('\t')
                .append(best < 0 ? "0" : String.valueOf(counts[best] / 10.0)).append('\n');
        }
        try {
            java.nio.file.Files.writeString(server.getServerDirectory().resolve("loot_sim.tsv"), sim);
            java.nio.file.Files.writeString(server.getServerDirectory().resolve("loot_needs.tsv"), out);
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
        int lacking = missing;
        source.sendSuccess(() -> Component.literal("Loot simulation: " + chests.size() + " items -> loot_sim.tsv; " + needs.size()
            + " recipe ingredients, " + lacking + " from nowhere -> loot_needs.tsv"), false);
        return chests.size();
    }

    /** Writes the dungeon loot pool (item and value) to loot_pool.tsv. */
    private static int auditLoot(CommandSourceStack source) {
        StringBuilder out = new StringBuilder("value\titem\tweight\tchance_in_value%\n");
        String[] names = {"junk", "common", "uncommon", "rare", "epic"};
        int count = 0;
        for (int value = 0; value < names.length; value++) {
            var items = net.expedition.content.DungeonLoot.pool(value);
            double total = items.stream().mapToInt(net.expedition.content.DungeonLoot::weight).sum();
            for (var item : items) {
                int weight = net.expedition.content.DungeonLoot.weight(item);
                out.append(names[value]).append('\t').append(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item)).append('\t')
                    .append(weight).append('\t').append(String.format("%.2f", 100 * weight / total)).append('\n');
                count++;
            }
        }
        StringBuilder left = new StringBuilder("item\treason\trecipes\tresults\tresult_mods\n");
        // Items only: blocks (planks, slabs, locometal...) are a different question.
        net.expedition.content.DungeonLoot.rejected().entrySet().stream()
            .filter(e -> !(e.getKey() instanceof net.minecraft.world.item.BlockItem) || e.getKey() instanceof net.minecraft.world.item.ItemNameBlockItem)
            .sorted(java.util.Comparator.comparingInt((java.util.Map.Entry<net.minecraft.world.item.Item, String> e) -> -net.expedition.content.DungeonLoot.usage(e.getKey())[1]))
            .forEach(e -> {
                int[] use = net.expedition.content.DungeonLoot.usage(e.getKey());
                left.append(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(e.getKey())).append('\t').append(e.getValue())
                    .append('\t').append(use[0]).append('\t').append(use[1]).append('\t').append(use[2]).append('\n');
            });
        try {
            java.nio.file.Files.writeString(source.getServer().getServerDirectory().resolve("loot_rejected.tsv"), left);
            java.nio.file.Files.writeString(source.getServer().getServerDirectory().resolve("loot_pool.tsv"), out);
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
        int written = count;
        source.sendSuccess(() -> Component.literal("Loot pool: " + written + " items -> loot_pool.tsv"), false);
        return count;
    }

    /**
     * Every item against what dungeon chests should hold (ingredients people use up, not things already made, not mob
     * drops): distinct recipe results it goes into and their mods, whether a recipe makes it, whether a mob drops it and
     * whether anything else (a block, a recipe) gives it, whether it is a placed machine (a block with a block entity),
     * its furnace burn time, food. -> ingredients_audit.tsv
     */
    private static int auditIngredients(CommandSourceStack source) {
        var server = source.getServer();
        var registries = server.registryAccess();
        java.util.Map<net.minecraft.world.item.Item, java.util.Set<net.minecraft.world.item.Item>> results = new java.util.HashMap<>();
        java.util.Map<net.minecraft.world.item.Item, java.util.Set<String>> mods = new java.util.HashMap<>();
        java.util.Set<net.minecraft.world.item.Item> made = new java.util.HashSet<>();
        for (var holder : server.getRecipeManager().getRecipes()) {
            ItemStack result = holder.value().getResultItem(registries);
            if (!result.isEmpty()) {
                made.add(result.getItem());
            }
            java.util.Set<net.minecraft.world.item.Item> in = new java.util.HashSet<>();
            for (var ingredient : holder.value().getIngredients()) {
                for (ItemStack stack : ingredient.getItems()) {
                    in.add(stack.getItem());
                }
            }
            for (var item : in) {
                if (!result.isEmpty() && item != result.getItem()) {
                    results.computeIfAbsent(item, k -> new java.util.HashSet<>()).add(result.getItem());
                    mods.computeIfAbsent(item, k -> new java.util.HashSet<>()).add(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(result.getItem()).getNamespace());
                }
            }
        }
        // Loot tables as written (any mod or datapack): items named in entity tables and in block tables.
        java.util.Set<String> mobDrops = new java.util.HashSet<>();
        java.util.Set<String> blockDrops = new java.util.HashSet<>();
        java.util.regex.Pattern name = java.util.regex.Pattern.compile("\"name\"\\s*:\\s*\"([a-z0-9_.-]+:[a-z0-9_/.-]+)\"");
        server.getResourceManager().listResources("loot_table", id -> id.getPath().endsWith(".json")).forEach((id, resource) -> {
            boolean entity = id.getPath().startsWith("loot_table/entities/");
            boolean block = id.getPath().startsWith("loot_table/blocks/");
            if (!entity && !block) {
                return;
            }
            try (var reader = resource.openAsReader()) {
                var matcher = name.matcher(reader.lines().collect(java.util.stream.Collectors.joining("\n")));
                while (matcher.find()) {
                    (entity ? mobDrops : blockDrops).add(matcher.group(1));
                }
            } catch (java.io.IOException ignored) {
            }
        });
        StringBuilder out = new StringBuilder("item\tuses\tresult_mods\tcraftable\tmob_drop\tother_source\tmachine\tburn\tfood\tloot_weight\n");
        java.util.List<net.minecraft.world.item.Item> items = new java.util.ArrayList<>();
        net.minecraft.core.registries.BuiltInRegistries.ITEM.forEach(items::add);
        items.sort(java.util.Comparator.comparingInt((net.minecraft.world.item.Item item) -> -results.getOrDefault(item, java.util.Set.of()).size()));
        for (var item : items) {
            String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).toString();
            boolean machine = item instanceof net.minecraft.world.item.BlockItem blockItem && blockItem.getBlock() instanceof net.minecraft.world.level.block.EntityBlock;
            int burn = new ItemStack(item).getBurnTime(net.minecraft.world.item.crafting.RecipeType.SMELTING);
            out.append(id).append('\t').append(results.getOrDefault(item, java.util.Set.of()).size()).append('\t').append(mods.getOrDefault(item, java.util.Set.of()).size())
                .append('\t').append(made.contains(item) ? 1 : 0).append('\t').append(mobDrops.contains(id) ? 1 : 0)
                .append('\t').append(blockDrops.contains(id) || made.contains(item) ? 1 : 0).append('\t').append(machine ? 1 : 0)
                .append('\t').append(burn).append('\t').append(new ItemStack(item).has(net.minecraft.core.component.DataComponents.FOOD) ? 1 : 0)
                .append('\t').append(net.expedition.content.DungeonLoot.weight(item)).append('\n');
        }
        try {
            java.nio.file.Files.writeString(server.getServerDirectory().resolve("ingredients_audit.tsv"), out);
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
        source.sendSuccess(() -> Component.literal("ingredients_audit.tsv: " + items.size() + " items"), false);
        return items.size();
    }

    /**
     * Every monster's drops (its loot table rolled 200 times, killed by a player) against the gear they make: per mob and
     * item, the drop rate, how many gear items (armour, weapons, tools: things with attack or armour) it goes into
     * within three recipe steps and an example, and whether only mobs give it (no recipe, no block, no chest). With the
     * route steps the mob is the boss or guardian of. -> mob_drops.tsv
     */
    private static int auditMobDrops(CommandSourceStack source) {
        var server = source.getServer();
        var registries = server.registryAccess();
        ServerLevel level = server.overworld();
        java.util.Map<net.minecraft.world.item.Item, java.util.Set<net.minecraft.world.item.Item>> into = new java.util.HashMap<>();
        java.util.Set<net.minecraft.world.item.Item> made = new java.util.HashSet<>();
        for (var holder : server.getRecipeManager().getRecipes()) {
            ItemStack result = holder.value().getResultItem(registries);
            if (result.isEmpty()) {
                continue;
            }
            made.add(result.getItem());
            for (var ingredient : holder.value().getIngredients()) {
                for (ItemStack stack : ingredient.getItems()) {
                    if (stack.getItem() != result.getItem()) {
                        into.computeIfAbsent(stack.getItem(), k -> new java.util.HashSet<>()).add(result.getItem());
                    }
                }
            }
        }
        java.util.function.Predicate<net.minecraft.world.item.Item> gear = item -> item instanceof net.minecraft.world.item.ArmorItem
            || item instanceof net.minecraft.world.item.ProjectileWeaponItem || item instanceof net.minecraft.world.item.TieredItem
            || !new ItemStack(item).getAttributeModifiers().modifiers().isEmpty();
        // Chests and blocks: items any chest or block loot table names.
        java.util.Set<String> elsewhere = new java.util.HashSet<>();
        java.util.regex.Pattern name = java.util.regex.Pattern.compile("\"name\"\\s*:\\s*\"([a-z0-9_.-]+:[a-z0-9_/.-]+)\"");
        server.getResourceManager().listResources("loot_table", id -> id.getPath().endsWith(".json")).forEach((id, resource) -> {
            if (id.getPath().startsWith("loot_table/entities/")) {
                return;
            }
            try (var reader = resource.openAsReader()) {
                var matcher = name.matcher(reader.lines().collect(java.util.stream.Collectors.joining("\n")));
                while (matcher.find()) {
                    elsewhere.add(matcher.group(1));
                }
            } catch (java.io.IOException ignored) {
            }
        });
        java.util.Map<String, java.util.List<String>> roles = new java.util.HashMap<>();
        for (RouteSteps.Entry entry : RouteSteps.all()) {
            String step = entry.id().getPath();
            entry.step().boss().ifPresent(id -> roles.computeIfAbsent(id.toString(), k -> new java.util.ArrayList<>()).add("boss:" + step));
            entry.step().guardian().ifPresent(g -> roles.computeIfAbsent(g.entity().toString(), k -> new java.util.ArrayList<>()).add("guardian:" + step));
            entry.step().garrison().forEach(id -> roles.computeIfAbsent(id.toString(), k -> new java.util.ArrayList<>()).add("garrison:" + step));
        }
        var player = net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(level);
        StringBuilder out = new StringBuilder("entity\thealth\tsoldier\titem\trate\tgear_uses\tgear_example\tonly_mobs\troute\n");
        int rows = 0;
        for (var type : net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE) {
            if (type.getCategory() != net.minecraft.world.entity.MobCategory.MONSTER || !type.canSummon()) {
                continue;
            }
            net.minecraft.world.entity.Entity created;
            try {
                created = type.create(level);
            } catch (RuntimeException e) {
                continue;
            }
            if (!(created instanceof net.minecraft.world.entity.LivingEntity living)) {
                continue;
            }
            living.moveTo(0, 100, 0);
            var table = server.reloadableRegistries().getLootTable(living.getLootTable());
            java.util.Map<net.minecraft.world.item.Item, Integer> counts = new java.util.HashMap<>();
            for (int roll = 0; roll < 200; roll++) {
                try {
                    var params = new net.minecraft.world.level.storage.loot.LootParams.Builder(level)
                        .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.THIS_ENTITY, living)
                        .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN, living.position())
                        .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.DAMAGE_SOURCE, level.damageSources().playerAttack(player))
                        .withOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ATTACKING_ENTITY, player)
                        .withOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.DIRECT_ATTACKING_ENTITY, player)
                        .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.LAST_DAMAGE_PLAYER, player)
                        .create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.ENTITY);
                    table.getRandomItems(params).forEach(stack -> counts.merge(stack.getItem(), 1, Integer::sum));
                } catch (RuntimeException e) {
                    break;
                }
            }
            living.discard();
            String entity = net.minecraft.world.entity.EntityType.getKey(type).toString();
            boolean soldier = Garrisons.soldiers().getOrDefault(entity.substring(0, entity.indexOf(':')), List.of()).contains(entity);
            for (var drop : counts.entrySet()) {
                // Gear it goes into, up to three recipe steps away.
                java.util.Set<net.minecraft.world.item.Item> seen = new java.util.HashSet<>(java.util.Set.of(drop.getKey()));
                java.util.List<net.minecraft.world.item.Item> frontier = List.of(drop.getKey());
                java.util.List<net.minecraft.world.item.Item> gearFound = new java.util.ArrayList<>();
                for (int depth = 0; depth < 3 && !frontier.isEmpty(); depth++) {
                    java.util.List<net.minecraft.world.item.Item> next = new java.util.ArrayList<>();
                    for (var item : frontier) {
                        for (var result : into.getOrDefault(item, java.util.Set.of())) {
                            if (seen.add(result)) {
                                next.add(result);
                                if (gear.test(result)) {
                                    gearFound.add(result);
                                }
                            }
                        }
                    }
                    frontier = next;
                }
                String item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(drop.getKey()).toString();
                boolean onlyMobs = !made.contains(drop.getKey()) && !elsewhere.contains(item);
                out.append(entity).append('\t').append((int) living.getMaxHealth()).append('\t').append(soldier ? 1 : 0).append('\t').append(item)
                    .append('\t').append(String.format(java.util.Locale.ROOT, "%.2f", drop.getValue() / 200.0)).append('\t').append(gearFound.size())
                    .append('\t').append(gearFound.isEmpty() ? "-" : net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(gearFound.get(0)).toString())
                    .append('\t').append(onlyMobs ? 1 : 0).append('\t').append(String.join(",", roles.getOrDefault(entity, List.of()))).append('\n');
                rows++;
            }
        }
        try {
            java.nio.file.Files.writeString(server.getServerDirectory().resolve("mob_drops.tsv"), out);
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
        int written = rows;
        source.sendSuccess(() -> Component.literal("mob_drops.tsv: " + written + " drops"), false);
        return rows;
    }

    /**
     * Can every piece of gear be made? The obtainable things: what any loot table outside chests names (mobs, blocks,
     * fishing, archaeology), the dungeon loot pool, boss hearts; then, until nothing more comes, whatever some recipe
     * makes of obtainable things (Create's machines too). Every item with combat stats or a curio's use that stays out
     * -> obtainable.tsv, with the ingredients its recipes lack.
     */
    private static int auditObtainable(CommandSourceStack source) {
        var server = source.getServer();
        var registries = server.registryAccess();
        java.util.Set<net.minecraft.world.item.Item> have = new java.util.HashSet<>();
        java.util.regex.Pattern name = java.util.regex.Pattern.compile("\"name\"\\s*:\\s*\"([a-z0-9_.-]+:[a-z0-9_/.-]+)\"");
        server.getResourceManager().listResources("loot_table", id -> id.getPath().endsWith(".json")).forEach((id, resource) -> {
            if (id.getPath().contains("/chests/")) {
                return;
            }
            try (var reader = resource.openAsReader()) {
                var matcher = name.matcher(reader.lines().collect(java.util.stream.Collectors.joining("\n")));
                while (matcher.find()) {
                    net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(net.minecraft.resources.ResourceLocation.tryParse(matcher.group(1))).ifPresent(have::add);
                }
            } catch (java.io.IOException ignored) {
            }
        });
        for (int value = 0; value <= 4; value++) {
            have.addAll(net.expedition.content.DungeonLoot.pool(value));
        }
        have.add(net.expedition.ExpeditionRegistry.BOSS_HEART.get());
        // What the game gives in code: garrison mobs' trophies and Create parts (GarrisonDrops), the chapters' shards.
        net.minecraft.core.registries.BuiltInRegistries.ITEM.getTagOrEmpty(net.minecraft.tags.TagKey.create(Registries.ITEM,
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Expedition.MOD_ID, "trophies"))).forEach(holder -> have.add(holder.value()));
        for (String id : List.of("create:andesite_alloy", "create:iron_sheet", "create:cogwheel", "create:shaft", "create:brass_nugget", "create:electron_tube",
            "create:copper_sheet", "minecraft:gunpowder", "create:brass_ingot", "create:brass_sheet", "createbigcannons:powder_charge", "create:sturdy_sheet",
            "create:precision_mechanism", "create:experience_nugget")) {
            net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(net.minecraft.resources.ResourceLocation.parse(id)).ifPresent(have::add);
        }
        have.remove(net.minecraft.world.item.Items.AIR);
        var recipes = server.getRecipeManager().getRecipes().stream().filter(holder -> !holder.value().getResultItem(registries).isEmpty()).toList();
        boolean grew = true;
        while (grew) {
            grew = false;
            for (var holder : recipes) {
                var result = holder.value().getResultItem(registries).getItem();
                if (have.contains(result)) {
                    continue;
                }
                boolean all = ingredients(holder.value()).stream().allMatch(ingredient -> ingredient.isEmpty()
                    || java.util.Arrays.stream(ingredient.getItems()).anyMatch(stack -> have.contains(stack.getItem())));
                if (all) {
                    have.add(result);
                    grew = true;
                }
            }
        }
        StringBuilder out = new StringBuilder("item\tmissing\n");
        int missing = 0;
        for (var item : net.minecraft.core.registries.BuiltInRegistries.ITEM) {
            boolean gear = item instanceof net.minecraft.world.item.ArmorItem || item instanceof net.minecraft.world.item.ProjectileWeaponItem
                || item instanceof net.minecraft.world.item.TieredItem || !new ItemStack(item).getAttributeModifiers().modifiers().isEmpty()
                || net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).getNamespace().matches("relics|artifacts|curios");
            if (!gear || have.contains(item) || item.builtInRegistryHolder().is(net.expedition.content.RemovedItems.TAG)) {
                continue;
            }
            // What its recipes lack: the ingredients no recipe of it can get.
            java.util.Set<String> lacking = new java.util.TreeSet<>();
            boolean made = false;
            for (var holder : recipes) {
                if (holder.value().getResultItem(registries).getItem() != item) {
                    continue;
                }
                made = true;
                for (var ingredient : ingredients(holder.value())) {
                    if (!ingredient.isEmpty() && java.util.Arrays.stream(ingredient.getItems()).noneMatch(stack -> have.contains(stack.getItem()))) {
                        ItemStack[] options = ingredient.getItems();
                        lacking.add(options.length == 0 ? "?" : net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(options[0].getItem()).toString());
                    }
                }
            }
            out.append(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item)).append('\t').append(made ? String.join(" ", lacking) : "no recipe, no source").append('\n');
            missing++;
        }
        // Everything else no one can get (not only gear): by mod, for a look at what a mod brings that the pack never gives.
        StringBuilder all = new StringBuilder("item\tmod\n");
        for (var item : net.minecraft.core.registries.BuiltInRegistries.ITEM) {
            var key = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item);
            if (have.contains(item) || item instanceof net.minecraft.world.item.SpawnEggItem || key.getPath().endsWith("spawn_egg")
                || item.builtInRegistryHolder().is(net.expedition.content.RemovedItems.TAG) || key.getNamespace().equals("minecraft")) {
                continue;
            }
            all.append(key).append('\t').append(key.getNamespace()).append('\n');
        }
        try {
            java.nio.file.Files.writeString(server.getServerDirectory().resolve("obtainable_all.tsv"), all);
            java.nio.file.Files.writeString(server.getServerDirectory().resolve("obtainable.tsv"), out);
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
        int count = missing;
        source.sendSuccess(() -> Component.literal("obtainable.tsv: " + count + " pieces of gear can't be made (" + have.size() + " things obtainable)"), false);
        return missing;
    }

    /** A recipe's ingredients; a smithing table's too (vanilla gives none for them: template, base, addition). */
    private static List<net.minecraft.world.item.crafting.Ingredient> ingredients(net.minecraft.world.item.crafting.Recipe<?> recipe) {
        if (recipe instanceof net.minecraft.world.item.crafting.SmithingTransformRecipe smithing) {
            List<net.minecraft.world.item.crafting.Ingredient> parts = new java.util.ArrayList<>();
            for (String field : List.of("template", "base", "addition")) {
                try {
                    var f = net.minecraft.world.item.crafting.SmithingTransformRecipe.class.getDeclaredField(field);
                    f.setAccessible(true);
                    parts.add((net.minecraft.world.item.crafting.Ingredient) f.get(smithing));
                } catch (ReflectiveOperationException ignored) {
                }
            }
            return parts;
        }
        return recipe.getIngredients();
    }
}
