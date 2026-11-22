// Dungeon garrisons: spawners are a farm, not a fight. Spawners are removed, and every dungeon gets a fixed,
// saved budget of mobs instead. The mobs trickle in around players exploring it: a few at a time, out of sight,
// partly at the spots where the spawners stood. Dead mobs never come back; when the budget is spent and the last
// garrison mob dies, the dungeon is cleared. Until then its blocks can't be broken or blown up.
//
// Which mobs, in which proportions:
//   1. the dungeon's own spawners (3 skeleton spawners + 1 zombie spawner = 75% skeletons);
//   2. otherwise the structure's own monster spawns (nether fortress: blazes, wither skeletons; outpost: pillagers);
//   3. otherwise the biome's monsters at the player.
// A structure gets a garrison only if it had spawners, loot chests or own monster spawns (decorative ruins don't).
// Spawners outside structures (vanilla monster rooms) become a small one-time pack of their mob.
// Also: every hostile ground mob can open wooden doors, so a door no longer protects from anything.
//
// Java collections are walked with plain loops: JS array helpers (.some/.filter) don't exist on them in Rhino.
// shortcut: runs as a KubeJS script; if it shows up in spark profiles, port it to a small NeoForge mod.

const $Registries = Java.loadClass('net.minecraft.core.registries.Registries')
const $BlockPos = Java.loadClass('net.minecraft.core.BlockPos')
const $Direction = Java.loadClass('net.minecraft.core.Direction')
const $AABB = Java.loadClass('net.minecraft.world.phys.AABB')
const $Vec3 = Java.loadClass('net.minecraft.world.phys.Vec3')
const $HitResultType = Java.loadClass('net.minecraft.world.phys.HitResult$Type')
const $ClipContext = Java.loadClass('net.minecraft.world.level.ClipContext')
const $ClipBlock = Java.loadClass('net.minecraft.world.level.ClipContext$Block')
const $ClipFluid = Java.loadClass('net.minecraft.world.level.ClipContext$Fluid')
const $CompoundTag = Java.loadClass('net.minecraft.nbt.CompoundTag')
const $SpawnerBlockEntity = Java.loadClass('net.minecraft.world.level.block.entity.SpawnerBlockEntity')
const $LootContainer = Java.loadClass('net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity')
const $EntityType = Java.loadClass('net.minecraft.world.entity.EntityType')
const $MobCategory = Java.loadClass('net.minecraft.world.entity.MobCategory')
const $Mob = Java.loadClass('net.minecraft.world.entity.Mob')
const $Player = Java.loadClass('net.minecraft.world.entity.player.Player')
const $Enemy = Java.loadClass('net.minecraft.world.entity.monster.Enemy')
const $MobSpawnType = Java.loadClass('net.minecraft.world.entity.MobSpawnType')
const $EventHooks = Java.loadClass('net.neoforged.neoforge.event.EventHooks')
const $GroundPathNavigation = Java.loadClass('net.minecraft.world.entity.ai.navigation.GroundPathNavigation')
const $OpenDoorGoal = Java.loadClass('net.minecraft.world.entity.ai.goal.OpenDoorGoal')

// Structures that are not dungeons: no garrison, no protection (spawners in them are still removed).
const GARRISON_EXCLUDED_KEYWORDS = ['village', 'town', 'ruined_portal', 'buried_treasure', 'trail_ruins', 'igloo']

// Garrison budget = BASE + per piece + per spawner + per loot chest, capped.
const BUDGET_BASE = 2
const BUDGET_PER_PIECE = 1
const BUDGET_PER_SPAWNER = 4
const BUDGET_PER_CHEST = 2
const BUDGET_MAX = 40

// Trickle spawning around a player inside the dungeon.
const SPAWN_INTERVAL_TICKS = 60
const SPAWN_PER_WAVE = 2
const MAX_ALIVE_NEAR_PLAYER = 6
const ALIVE_COUNT_RADIUS = 32
const SPAWN_MIN_DISTANCE = 8
const SPAWN_MAX_DISTANCE = 20
const SPAWN_MAX_HEIGHT_DIFF = 6
const ANCHOR_RADIUS = 24
const ANCHOR_CHANCE = 0.5

const MONSTER_ROOM_PACK = 3
const SCAN_INTERVAL_TICKS = 20
const SCAN_CHUNK_RADIUS = 3
const STUCK_CHECK_INTERVAL_TICKS = 1200

const STATE_PENDING = 0
const STATE_ACTIVE = 1
const STATE_CLEARED = 2

const GARRISON_TAG = 'garrison'
const MONSTER_ROOM_KEY = 'monster_room'

// ---------- helpers ----------

function toArray(javaCollection) {
  const result = []
  const iterator = javaCollection.iterator()
  while (iterator.hasNext()) {
    result.push(iterator.next())
  }
  return result
}

function pickWeighted(weights) {
  const ids = Object.keys(weights)
  let total = 0
  ids.forEach(id => { total += weights[id] })
  let roll = Math.random() * total
  for (let i = 0; i < ids.length; i++) {
    roll -= weights[ids[i]]
    if (roll < 0) {
      return ids[i]
    }
  }
  return ids.length > 0 ? ids[ids.length - 1] : null
}

function spawnerDataWeights(spawnerDataList) {
  const weights = {}
  toArray(spawnerDataList).forEach(data => {
    const id = String($EntityType.getKey(data.type))
    weights[id] = (weights[id] || 0) + data.getWeight().asInt()
  })
  return weights
}

function garrisonStore(server) {
  const data = server.persistentData
  if (!data.contains('garrisons')) {
    data.put('garrisons', new $CompoundTag())
  }
  return data.getCompound('garrisons')
}

function structureId(level, structure) {
  return String(level.registryAccess().registryOrThrow($Registries.STRUCTURE).getKey(structure))
}

function dimensionId(level) {
  return String(level.dimension().location())
}

function isExcludedStructure(id) {
  return GARRISON_EXCLUDED_KEYWORDS.some(keyword => id.indexOf(keyword) >= 0)
}

function structureMonsterWeights(structure) {
  const override = structure.spawnOverrides().get($MobCategory.MONSTER)
  return override ? spawnerDataWeights(override.spawns().unwrap()) : {}
}

// Dungeon structure starts whose area covers the chunk at pos.
function dungeonStartsAt(level, pos) {
  const manager = level.structureManager()
  const result = []
  const references = manager.getAllStructuresAt(pos)
  toArray(references.keySet()).forEach(structure => {
    const id = structureId(level, structure)
    if (isExcludedStructure(id)) {
      return
    }
    manager.fillStartsForStructure(structure, references.get(structure), start => {
      if (start.isValid()) {
        const chunk = start.getChunkPos()
        result.push({ start: start, structure: structure, id: id, key: `${dimensionId(level)}|${id}|${chunk.x},${chunk.z}` })
      }
    })
  })
  return result
}

function inPiece(level, entry, pos) {
  return level.structureManager().structureHasPieceAt(pos, entry.start)
}

// ---------- saved records ----------
// state, budget (still to spawn), alive (spawned, not dead), spawners, chests,
// spawnerTypes ("id=count;..."), anchors (spawner positions), scanned (chunks already counted for chests).

function getRecord(server, key) {
  const store = garrisonStore(server)
  return store.contains(key) ? store.getCompound(key) : null
}

function ensureRecord(server, key) {
  const store = garrisonStore(server)
  if (!store.contains(key)) {
    const record = new $CompoundTag()
    record.putInt('state', STATE_PENDING)
    record.putInt('budget', 0)
    record.putInt('alive', 0)
    record.putInt('spawners', 0)
    record.putInt('chests', 0)
    record.putString('spawnerTypes', '')
    record.putString('anchors', '')
    record.put('scanned', new $CompoundTag())
    store.put(key, record)
  }
  return store.getCompound(key)
}

function readSpawnerTypes(record) {
  const weights = {}
  String(record.getString('spawnerTypes')).split(';').forEach(part => {
    const split = part.split('=')
    if (split.length === 2) {
      weights[split[0]] = Number(split[1])
    }
  })
  return weights
}

function addSpawnerType(record, typeId) {
  const weights = readSpawnerTypes(record)
  weights[typeId] = (weights[typeId] || 0) + 1
  record.putString('spawnerTypes', Object.keys(weights).map(id => `${id}=${weights[id]}`).join(';'))
}

// Spawner positions as "x,y,z;x,y,z".
function readAnchors(record) {
  return String(record.getString('anchors')).split(';').filter(part => part.length > 0).map(part => {
    const xyz = part.split(',').map(Number)
    return new $BlockPos(xyz[0], xyz[1], xyz[2])
  })
}

function addAnchor(record, pos) {
  const anchors = String(record.getString('anchors'))
  record.putString('anchors', `${anchors}${anchors.length > 0 ? ';' : ''}${pos.x},${pos.y},${pos.z}`)
}

function isEligible(record, entry) {
  return record.getInt('spawners') > 0 || record.getInt('chests') > 0 || Object.keys(structureMonsterWeights(entry.structure)).length > 0
}

function isProtected(server, entry) {
  const record = getRecord(server, entry.key)
  if (!record || record.getInt('state') === STATE_CLEARED) {
    return false
  }
  return record.getInt('state') === STATE_ACTIVE || isEligible(record, entry)
}

function blockIsInProtectedPiece(level, pos, entries) {
  const server = level.getServer()
  return entries.some(entry => inPiece(level, entry, pos) && isProtected(server, entry))
}

// Spawners first, then the structure's own monsters, then the biome's monsters.
function mobWeightsFor(level, record, entry, pos) {
  const fromSpawners = readSpawnerTypes(record)
  if (Object.keys(fromSpawners).length > 0) {
    return fromSpawners
  }
  const fromStructure = structureMonsterWeights(entry.structure)
  if (Object.keys(fromStructure).length > 0) {
    return fromStructure
  }
  return spawnerDataWeights(level.getBiome(pos).value().getMobSettings().getMobs($MobCategory.MONSTER).unwrap())
}

// ---------- scanning: spawners and loot chests ----------

function readSpawnerType(level, blockEntity) {
  const tag = blockEntity.saveWithoutMetadata(level.registryAccess())
  const id = String(tag.getCompound('SpawnData').getCompound('entity').getString('id'))
  return id.length > 0 ? id : null
}

function scanChunk(level, server, chunk) {
  const chunkKey = `${chunk.getPos().x},${chunk.getPos().z}`
  const spawners = []
  const chests = []
  toArray(chunk.getBlockEntities().values()).forEach(blockEntity => {
    if (blockEntity instanceof $SpawnerBlockEntity) {
      spawners.push({ pos: blockEntity.getBlockPos(), type: readSpawnerType(level, blockEntity) })
    } else if (blockEntity instanceof $LootContainer && blockEntity.getLootTable() !== null) {
      chests.push(blockEntity.getBlockPos())
    }
  })
  const countedChunks = {}
  chests.forEach(pos => {
    const owner = dungeonStartsAt(level, pos).find(entry => inPiece(level, entry, pos))
    if (!owner) {
      return
    }
    const record = ensureRecord(server, owner.key)
    if (record.getCompound('scanned').contains(chunkKey)) {
      return
    }
    countedChunks[owner.key] = record
    record.putInt('chests', record.getInt('chests') + 1)
    if (record.getInt('state') === STATE_ACTIVE) {
      record.putInt('budget', record.getInt('budget') + BUDGET_PER_CHEST)
    }
  })
  Object.keys(countedChunks).forEach(key => countedChunks[key].getCompound('scanned').putBoolean(chunkKey, true))

  spawners.forEach(spawner => {
    level.removeBlock(spawner.pos, false)
    const owner = dungeonStartsAt(level, spawner.pos).find(entry => entry.start.getBoundingBox().isInside(spawner.pos))
    if (!owner) {
      if (spawner.type) {
        for (let i = 0; i < MONSTER_ROOM_PACK; i++) {
          const spot = randomFloorNear(level, spawner.pos, 4)
          if (spot) {
            spawnGarrisonMob(level, spawner.type, spot, MONSTER_ROOM_KEY)
          }
        }
      }
      return
    }
    const record = ensureRecord(server, owner.key)
    record.putInt('spawners', record.getInt('spawners') + 1)
    addAnchor(record, spawner.pos)
    if (spawner.type) {
      addSpawnerType(record, spawner.type)
    }
    if (record.getInt('state') === STATE_ACTIVE) {
      record.putInt('budget', record.getInt('budget') + BUDGET_PER_SPAWNER)
    }
  })
}

function scanAroundPlayer(level, server, player) {
  const center = player.chunkPosition()
  for (let dx = -SCAN_CHUNK_RADIUS; dx <= SCAN_CHUNK_RADIUS; dx++) {
    for (let dz = -SCAN_CHUNK_RADIUS; dz <= SCAN_CHUNK_RADIUS; dz++) {
      const chunk = level.getChunkSource().getChunkNow(center.x + dx, center.z + dz)
      if (chunk) {
        scanChunk(level, server, chunk)
      }
    }
  }
}

// ---------- spawning ----------

function isFloorSpot(level, pos) {
  if (!level.isLoaded(pos)) {
    return false
  }
  const below = pos.below()
  const head = pos.above()
  const feetState = level.getBlockState(pos)
  const headState = level.getBlockState(head)
  return feetState.getCollisionShape(level, pos).isEmpty() && feetState.getFluidState().isEmpty()
    && headState.getCollisionShape(level, head).isEmpty() && headState.getFluidState().isEmpty()
    && level.getBlockState(below).isFaceSturdy(level, below, $Direction.UP)
}

// A floor spot near (x, y, z), searching up and down a few blocks.
function floorAt(level, x, y, z, maxDy) {
  for (let dy = 0; dy <= maxDy; dy++) {
    const signs = [1, -1]
    for (let i = 0; i < signs.length; i++) {
      const pos = new $BlockPos(x, y + signs[i] * dy, z)
      if (isFloorSpot(level, pos)) {
        return pos
      }
    }
  }
  return null
}

function randomFloorNear(level, center, radius) {
  for (let attempt = 0; attempt < 8; attempt++) {
    const pos = floorAt(level,
      center.x + Math.floor(Math.random() * (radius * 2 + 1)) - radius,
      center.y,
      center.z + Math.floor(Math.random() * (radius * 2 + 1)) - radius, 3)
    if (pos) {
      return pos
    }
  }
  return null
}

function canPlayerSee(level, player, pos) {
  const eye = player.getEyePosition()
  const target = new $Vec3(pos.x + 0.5, pos.y + 1.5, pos.z + 0.5)
  const hit = level.clip(new $ClipContext(eye, target, $ClipBlock.COLLIDER, $ClipFluid.NONE, player))
  return hit.getType() === $HitResultType.MISS
}

function goodSpawnSpot(level, entry, player, pos) {
  if (!pos || !inPiece(level, entry, pos)) {
    return false
  }
  const distance = Math.sqrt(player.distanceToSqr(pos.x + 0.5, pos.y, pos.z + 0.5))
  return distance >= SPAWN_MIN_DISTANCE && distance <= SPAWN_MAX_DISTANCE + ANCHOR_RADIUS && !canPlayerSee(level, player, pos)
}

function anchorSpot(level, record, player) {
  const near = readAnchors(record).filter(pos => player.distanceToSqr(pos.x, pos.y, pos.z) <= ANCHOR_RADIUS * ANCHOR_RADIUS)
  if (near.length === 0) {
    return null
  }
  return randomFloorNear(level, near[Math.floor(Math.random() * near.length)], 3)
}

function spotAroundPlayer(level, player) {
  const angle = Math.random() * Math.PI * 2
  const distance = SPAWN_MIN_DISTANCE + Math.random() * (SPAWN_MAX_DISTANCE - SPAWN_MIN_DISTANCE)
  const origin = player.blockPosition()
  return floorAt(level,
    Math.floor(origin.x + Math.cos(angle) * distance),
    origin.y,
    Math.floor(origin.z + Math.sin(angle) * distance), SPAWN_MAX_HEIGHT_DIFF)
}

// Returns true if the mob was spawned.
function spawnGarrisonMob(level, typeId, pos, key) {
  try {
    const entity = level.createEntity(typeId)
    if (!entity || !(entity instanceof $Mob)) {
      return false
    }
    entity.moveTo(pos.x + 0.5, pos.y, pos.z + 0.5, Math.random() * 360, 0)
    $EventHooks.finalizeMobSpawn(entity, level, level.getCurrentDifficultyAt(pos), $MobSpawnType.SPAWNER, null)
    entity.setPersistenceRequired()
    entity.persistentData.putString(GARRISON_TAG, key)
    return level.addFreshEntity(entity)
  } catch (error) {
    console.warn(`[garrisons] can't spawn ${typeId}: ${error}`)
    return false
  }
}

function garrisonMobsNear(level, player, key) {
  const area = player.getBoundingBox().inflate(ALIVE_COUNT_RADIUS)
  return toArray(level.getEntitiesWithin(area)).filter(entity => String(entity.persistentData.getString(GARRISON_TAG)) === key).length
}

function startGarrison(level, server, entry, record, player) {
  if (!isEligible(record, entry)) {
    return false
  }
  const budget = BUDGET_BASE + BUDGET_PER_PIECE * entry.start.getPieces().size()
    + BUDGET_PER_SPAWNER * record.getInt('spawners') + BUDGET_PER_CHEST * record.getInt('chests')
  record.putInt('state', STATE_ACTIVE)
  record.putInt('budget', Math.min(BUDGET_MAX, budget))
  console.info(`[garrisons] ${entry.key}: budget ${record.getInt('budget')}`)
  player.tell(Text.gold(`⚔ Здесь гарнизон (~${record.getInt('budget')} врагов). Пока данж не зачищен, его блоки не ломаются и не взрываются.`))
  return true
}

function spawnWave(level, server, entry, record, player) {
  const weights = mobWeightsFor(level, record, entry, player.blockPosition())
  if (Object.keys(weights).length === 0) {
    return
  }
  let room = Math.min(SPAWN_PER_WAVE, MAX_ALIVE_NEAR_PLAYER - garrisonMobsNear(level, player, entry.key))
  for (let attempt = 0; attempt < 10 && room > 0 && record.getInt('budget') > 0; attempt++) {
    const pos = Math.random() < ANCHOR_CHANCE ? (anchorSpot(level, record, player) || spotAroundPlayer(level, player)) : spotAroundPlayer(level, player)
    if (goodSpawnSpot(level, entry, player, pos) && spawnGarrisonMob(level, pickWeighted(weights), pos, entry.key)) {
      record.putInt('budget', record.getInt('budget') - 1)
      record.putInt('alive', record.getInt('alive') + 1)
      room--
    }
  }
}

// ---------- clearing ----------

function tryClear(level, server, key, start) {
  const record = getRecord(server, key)
  if (!record || record.getInt('state') !== STATE_ACTIVE || record.getInt('budget') > 0 || record.getInt('alive') > 0) {
    return
  }
  record.putInt('state', STATE_CLEARED)
  console.info(`[garrisons] ${key}: cleared`)
  if (!start) {
    return
  }
  const area = start.getBoundingBox().inflatedBy(64)
  toArray(level.players()).forEach(player => {
    if (area.isInside(player.blockPosition())) {
      player.tell(Text.green('✔ Данж зачищен. Теперь его блоки можно ломать.'))
    }
  })
}

// Safety net: garrison mobs that vanished without dying (other mods discarding them) must not lock the dungeon forever.
function recountAlive(level, server, entry, record) {
  const box = entry.start.getBoundingBox()
  for (let cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
    for (let cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
      if (!level.getChunkSource().getChunkNow(cx, cz)) {
        return // part of the dungeon is unloaded: its mobs may be there
      }
    }
  }
  const area = new $AABB(box.minX() - 32, box.minY() - 32, box.minZ() - 32, box.maxX() + 33, box.maxY() + 33, box.maxZ() + 33)
  const alive = toArray(level.getEntitiesWithin(area)).filter(entity => String(entity.persistentData.getString(GARRISON_TAG)) === entry.key).length
  if (alive < record.getInt('alive')) {
    record.putInt('alive', alive)
    tryClear(level, server, entry.key, entry.start)
  }
}

// ---------- events ----------

ServerEvents.tick(event => {
  const server = event.server
  const tick = server.getTickCount()
  if (tick % SCAN_INTERVAL_TICKS !== 0) {
    return
  }
  toArray(server.getPlayerList().getPlayers()).forEach(player => {
    if (player.isSpectator()) {
      return
    }
    const level = player.level()
    scanAroundPlayer(level, server, player)
    const pos = player.blockPosition()
    dungeonStartsAt(level, pos).forEach(entry => {
      if (!inPiece(level, entry, pos)) {
        return
      }
      const record = ensureRecord(server, entry.key)
      const state = record.getInt('state')
      if (state === STATE_PENDING && !startGarrison(level, server, entry, record, player)) {
        return
      }
      if (state === STATE_CLEARED) {
        return
      }
      if (tick % SPAWN_INTERVAL_TICKS === 0 && record.getInt('budget') > 0) {
        spawnWave(level, server, entry, record, player)
      }
      if (tick % STUCK_CHECK_INTERVAL_TICKS === 0) {
        recountAlive(level, server, entry, record)
      }
    })
  })
})

EntityEvents.death(event => {
  const entity = event.entity
  const key = String(entity.persistentData.getString(GARRISON_TAG))
  if (key.length === 0 || key === MONSTER_ROOM_KEY) {
    return
  }
  const level = entity.level()
  const server = level.getServer()
  const record = getRecord(server, key)
  if (!record || record.getInt('state') !== STATE_ACTIVE) {
    return
  }
  record.putInt('alive', Math.max(0, record.getInt('alive') - 1))
  const left = record.getInt('alive') + record.getInt('budget')
  const killer = event.source.getEntity()
  if (left > 0 && killer instanceof $Player) {
    killer.setStatusMessage(Text.gold(`Гарнизон: осталось ${left}`))
  }
  const owner = dungeonStartsAt(level, entity.blockPosition()).find(entry => entry.key === key)
  tryClear(level, server, key, owner ? owner.start : null)
})

BlockEvents.broken(event => {
  const player = event.player
  if (player && player.isCreative()) {
    return
  }
  const level = event.level
  const pos = event.block.pos
  if (blockIsInProtectedPiece(level, pos, dungeonStartsAt(level, pos))) {
    if (player) {
      player.setStatusMessage(Text.red('Данж не зачищен — сначала перебейте гарнизон.'))
    }
    event.cancel()
  }
})

LevelEvents.afterExplosion(event => {
  const level = event.level
  const entries = dungeonStartsAt(level, $BlockPos.containing(event.x, event.y, event.z))
  if (entries.length === 0) {
    return
  }
  toArray(event.affectedBlocks).forEach(block => {
    if (blockIsInProtectedPiece(level, block.pos, entries)) {
      event.removeAffectedBlock(block)
    }
  })
})

// Doors stop nothing: hostile ground mobs path through wooden doors and open them (iron doors still need redstone).
EntityEvents.spawned(event => {
  const entity = event.entity
  if (!(entity instanceof $Mob) || !(entity instanceof $Enemy)) {
    return
  }
  const navigation = entity.getNavigation()
  if (!(navigation instanceof $GroundPathNavigation) || navigation.canOpenDoors()) {
    return
  }
  navigation.setCanOpenDoors(true)
  entity.goalSelector.addGoal(2, new $OpenDoorGoal(entity, false))
})
