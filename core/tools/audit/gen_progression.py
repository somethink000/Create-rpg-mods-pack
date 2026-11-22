# Generates the Create recipes of the progression: trophies -> Apotheosis rarity materials, gems and Relics.
# Usage: gen_progression.py <resources dir> <Apotheosis jar>
import json, os, sys, zipfile, shutil, glob
res, apo = sys.argv[1], zipfile.ZipFile(sys.argv[2])
out = f'{res}/data/expedition/recipe/progression'
shutil.rmtree(out, ignore_errors=True)
for d in ('materials', 'gems', 'relics'):
    os.makedirs(f'{out}/{d}')

def ing(x):
    if isinstance(x, list):
        return [ing(i) for i in x]
    return {'tag': x[1:]} if x.startswith('#') else {'item': x}

def write(path, data):
    json.dump(data, open(f'{out}/{path}.json', 'w'), indent=2)

def mixing(path, ingredients, result, count=1, heat=None):
    data = {'type': 'create:mixing', 'ingredients': [ing(i) for i in ingredients], 'results': [{'id': result, 'count': count}]}
    if heat:
        data['heat_requirement'] = heat
    write(path, data)

def crafting(path, pattern, key, result):
    write(path, {'type': 'create:mechanical_crafting', 'accept_mirrored': False, 'category': 'misc', 'show_notification': False,
                 'key': {k: ing(v) for k, v in key.items()}, 'pattern': pattern, 'result': result})

T = lambda name: f'expedition:{name}'
SCRAP, FABRIC, SHARD, SANDS, PEARL = ('apotheosis:mysterious_scrap_metal', 'apotheosis:timeworn_fabric', 'apotheosis:luminous_crystal_shard',
                                      'apotheosis:arcane_sands', 'apotheosis:godforged_pearl')

# Rarity materials: each tier needs trophies from farther dungeons and the previous material.
mixing('materials/mysterious_scrap_metal', ['#expedition:trophies', 'create:iron_sheet', 'minecraft:iron_nugget'], SCRAP, 2)
mixing('materials/timeworn_fabric', [[T('chitin_plate'), T('feral_fang'), T('grave_dust')], SCRAP, 'minecraft:string', 'minecraft:string', 'minecraft:leather'], FABRIC, 1, 'heated')
mixing('materials/luminous_crystal_shard', [[T('abyssal_scale'), T('raider_insignia')], FABRIC, 'create:polished_rose_quartz', 'minecraft:glowstone_dust', 'minecraft:glowstone_dust'], SHARD, 1, 'heated')
mixing('materials/arcane_sands', [T('cinder_core'), T('void_shard'), SHARD, 'minecraft:redstone', 'create:experience_nugget', 'create:experience_nugget'], SANDS, 1, 'superheated')
# Boss hearts by route tier: the strongest crafts need a late boss's heart, not any heart.
ROUTE = [json.load(open(f)) for f in sorted(glob.glob(f'{res}/data/expedition/expedition_route/*.json'))]
def hearts(min_tier):
    return sorted({r['boss'] for r in ROUTE if r.get('boss') and int(r['tier'][1:]) >= min_tier})
def heart(boss):
    return {'type': 'neoforge:components', 'items': 'expedition:boss_heart', 'components': {'expedition:boss': boss}}
for boss in hearts(5):
    write(f"materials/godforged_pearl_{boss.replace(':', '_')}", {'type': 'create:mechanical_crafting', 'accept_mirrored': False, 'category': 'misc',
          'show_notification': False, 'pattern': ['SVS', 'VHV', 'SVS'], 'key': {'S': ing(SANDS), 'V': ing(T('void_shard')), 'H': heart(boss)},
          'result': {'id': PEARL, 'count': 2}})
for name, src, chance in (('gem_dust_from_shard', SHARD, 0.5), ('gem_dust_from_sands', SANDS, 1.0)):
    write(f'materials/{name}', {'type': 'create:crushing', 'processing_time': 200, 'ingredients': [ing(src)],
                                'results': [{'id': 'apotheosis:gem_dust', 'count': 2}, {'id': 'apotheosis:gem_dust', 'chance': chance}]})

# Gems: thematic trophy + base + material, at the gem's lowest purity; the gem cutting table upgrades them.
GEMS = {
    'overworld/earth': ('feral_fang', 'minecraft:emerald', FABRIC), 'overworld/royalty': ('raider_insignia', 'minecraft:gold_ingot', SHARD),
    'overworld/verdant_ruin': ('chitin_plate', 'minecraft:moss_block', FABRIC), 'the_end/endersurge': ('void_shard', 'minecraft:ender_eye', SANDS),
    'the_end/mageslayer': ('void_shard', 'minecraft:amethyst_shard', SANDS), 'the_nether/blood_lord': ('cinder_core', 'minecraft:crimson_fungus', SANDS),
    'the_nether/inferno': ('cinder_core', 'minecraft:blaze_powder', SHARD), 'the_nether/molten_breach': ('cinder_core', 'minecraft:magma_cream', SANDS),
    'twilight/forest': ('feral_fang', 'minecraft:oak_sapling', SHARD), 'twilight/queen': ('grave_dust', 'minecraft:blue_ice', SANDS),
    'core/ballast': ('chitin_plate', 'minecraft:iron_ingot', FABRIC), 'core/brawlers': ('feral_fang', 'minecraft:leather', FABRIC),
    'core/breach': ('raider_insignia', 'minecraft:flint', FABRIC), 'core/combatant': ('raider_insignia', 'minecraft:quartz', SHARD),
    'core/guardian': ('abyssal_scale', 'minecraft:prismarine_crystals', SHARD), 'core/lightning': ('abyssal_scale', 'minecraft:lightning_rod', SHARD),
    'core/lunar': ('grave_dust', 'minecraft:phantom_membrane', SHARD), 'core/samurai': ('feral_fang', 'minecraft:bamboo', SHARD),
    'core/slipstream': ('chitin_plate', 'minecraft:feather', FABRIC), 'core/solar': ('cinder_core', 'minecraft:glowstone_dust', SHARD),
    'core/splendor': ('raider_insignia', 'minecraft:emerald', SANDS), 'core/tyrannical': ('grave_dust', 'minecraft:obsidian', SANDS),
    'core/warlord': ('raider_insignia', 'minecraft:crying_obsidian', SANDS)}
for gem, (trophy, base, material) in GEMS.items():
    spec = json.loads(apo.read(f'data/apotheosis/gems/{gem}.json'))
    purity = spec.get('min_purity', 'cracked')
    crafting(f'gems/{gem.replace("/", "_")}', ['DMD', 'TBT', 'DMD'], {'D': 'apotheosis:gem_dust', 'M': material, 'T': T(trophy), 'B': base},
             {'id': 'apotheosis:gem', 'count': 1, 'components': {'apotheosis:gem': f'apotheosis:{gem}', 'apotheosis:purity': purity}})

# Relics: base + trophies + material + Create part.
RELICS = {
    'reflective_necklace': ('minecraft:amethyst_shard', 'raider_insignia', SHARD, 'create:brass_sheet'),
    'jellyfish_necklace': ('minecraft:slime_ball', 'abyssal_scale', FABRIC, 'create:copper_sheet'),
    'kinetic_belt': ('create:belt_connector', 'feral_fang', FABRIC, 'create:andesite_alloy'),
    'springy_boot': ('minecraft:slime_ball', 'chitin_plate', FABRIC, 'create:iron_sheet'),
    'leafy_mantle': ('minecraft:moss_block', 'chitin_plate', FABRIC, 'create:andesite_alloy'),
    'roller_skate': ('create:cogwheel', 'feral_fang', SCRAP, 'create:iron_sheet'),
    'midnight_mantle': ('minecraft:phantom_membrane', 'grave_dust', SHARD, 'create:brass_sheet'),
    'chorus_staff': ('minecraft:chorus_fruit', 'void_shard', SANDS, 'create:precision_mechanism'),
    'piglin_mask': ('minecraft:gold_ingot', 'cinder_core', SHARD, 'create:golden_sheet'),
    'cut_glass_boot': ('minecraft:tinted_glass', 'void_shard', SHARD, 'create:brass_sheet'),
    'ring_of_the_seven_deadly_sins': ('expedition:boss_heart', 'cinder_core', SANDS, 'create:precision_mechanism'),
    'sphere_of_self_sacrifice': ('minecraft:heart_of_the_sea', 'abyssal_scale', SANDS, 'create:precision_mechanism'),
    'hunting_belt': ('minecraft:leather', 'feral_fang', FABRIC, 'create:iron_sheet'),
    'rider_flute': ('minecraft:goat_horn', 'feral_fang', FABRIC, 'create:copper_sheet'),
    'clot_of_time': ('minecraft:clock', 'void_shard', SANDS, 'create:precision_mechanism'),
    'chef_hat': ('minecraft:white_wool', 'feral_fang', SCRAP, 'create:iron_sheet'),
    'experience_disperser': ('create:experience_block', 'grave_dust', SHARD, 'create:brass_sheet'),
    'glitchy_mantle': ('minecraft:ender_pearl', 'void_shard', SHARD, 'create:brass_sheet'),
    'ghostly_mantle': ('minecraft:phantom_membrane', 'grave_dust', SHARD, 'create:brass_sheet'),
    'shield_of_retaliation': ('create:sturdy_sheet', 'raider_insignia', SHARD, 'create:brass_sheet')}
for relic, (base, trophy, material, part) in RELICS.items():
    if base == 'expedition:boss_heart':  # one recipe per heart of a T4+ boss
        for boss in hearts(4):
            write(f"relics/{relic}_{boss.replace(':', '_')}", {'type': 'create:mechanical_crafting', 'accept_mirrored': False, 'category': 'misc',
                  'show_notification': False, 'pattern': ['PTP', 'MBM', 'PTP'],
                  'key': {'P': ing(part), 'T': ing(T(trophy)), 'M': ing(material), 'B': heart(boss)}, 'result': {'id': f'relics:{relic}', 'count': 1}})
        continue
    crafting(f'relics/{relic}', ['PTP', 'MBM', 'PTP'], {'P': part, 'T': T(trophy), 'M': material, 'B': base}, {'id': f'relics:{relic}', 'count': 1})
# Artifacts: same pattern as relics.
I, C, G, A, B, M = 'create:iron_sheet', 'create:copper_sheet', 'create:golden_sheet', 'create:andesite_alloy', 'create:brass_sheet', 'create:precision_mechanism'
ARTIFACTS = {
    'anglers_hat': ('minecraft:fishing_rod', 'abyssal_scale', SCRAP, I), 'antidote_vessel': ('minecraft:milk_bucket', 'grave_dust', FABRIC, C),
    'aqua_dashers': ('minecraft:leather', 'abyssal_scale', FABRIC, C), 'bunny_hoppers': ('minecraft:rabbit_foot', 'feral_fang', FABRIC, I),
    'charm_of_shrinking': ('minecraft:amethyst_shard', 'void_shard', SHARD, B), 'charm_of_sinking': ('minecraft:iron_block', 'abyssal_scale', SHARD, I),
    'chorus_totem': ('minecraft:chorus_fruit', 'void_shard', SANDS, M), 'cloud_in_a_bottle': ('minecraft:glass_bottle', 'feral_fang', SHARD, B),
    'cowboy_hat': ('minecraft:leather', 'feral_fang', SCRAP, I), 'cross_necklace': ('minecraft:gold_ingot', 'grave_dust', SHARD, G),
    'crystal_heart': ('minecraft:amethyst_block', 'void_shard', SANDS, M), 'digging_claws': ('minecraft:flint', 'chitin_plate', FABRIC, I),
    'eternal_steak': ('minecraft:cooked_beef', 'feral_fang', SHARD, B), 'everlasting_beef': ('minecraft:beef', 'feral_fang', SHARD, B),
    'feral_claws': ('minecraft:bone', 'feral_fang', FABRIC, I), 'fire_gauntlet': ('minecraft:blaze_rod', 'cinder_core', SHARD, G),
    'flame_pendant': ('minecraft:blaze_powder', 'cinder_core', SHARD, G), 'flippers': ('minecraft:turtle_scute', 'abyssal_scale', FABRIC, C),
    'golden_hook': ('minecraft:gold_ingot', 'raider_insignia', SHARD, G), 'helium_flamingo': ('minecraft:pink_wool', 'feral_fang', FABRIC, C),
    'kitty_slippers': ('minecraft:string', 'feral_fang', SCRAP, I), 'lucky_scarf': ('minecraft:rabbit_foot', 'raider_insignia', SHARD, B),
    'night_vision_goggles': ('create:goggles', 'grave_dust', FABRIC, B), 'novelty_drinking_hat': ('minecraft:glass_bottle', 'feral_fang', FABRIC, C),
    'obsidian_skull': ('minecraft:obsidian', 'cinder_core', SHARD, I), 'onion_ring': ('minecraft:golden_carrot', 'feral_fang', FABRIC, G),
    'panic_necklace': ('minecraft:sugar', 'grave_dust', FABRIC, C), 'pickaxe_heater': ('minecraft:blaze_rod', 'cinder_core', FABRIC, I),
    'plastic_drinking_hat': ('minecraft:glass_bottle', 'abyssal_scale', SCRAP, C), 'pocket_piston': ('minecraft:piston', 'chitin_plate', FABRIC, A),
    'power_glove': ('minecraft:leather', 'raider_insignia', SHARD, B), 'rooted_boots': ('minecraft:moss_block', 'chitin_plate', FABRIC, A),
    'running_shoes': ('minecraft:leather', 'feral_fang', FABRIC, A), 'scarf_of_invisibility': ('minecraft:phantom_membrane', 'void_shard', SANDS, B),
    'shock_pendant': ('minecraft:lightning_rod', 'abyssal_scale', SHARD, C), 'snorkel': ('minecraft:glass', 'abyssal_scale', FABRIC, C),
    'snowshoes': ('minecraft:snow_block', 'grave_dust', FABRIC, I), 'steadfast_spikes': ('minecraft:iron_block', 'chitin_plate', SHARD, I),
    'strider_shoes': ('minecraft:string', 'cinder_core', SHARD, G), 'superstitious_hat': ('minecraft:leather', 'grave_dust', SHARD, I),
    'thorn_pendant': ('minecraft:cactus', 'chitin_plate', SHARD, I), 'umbrella': ('minecraft:red_wool', 'feral_fang', FABRIC, A),
    'universal_attractor': ('minecraft:lodestone', 'raider_insignia', SANDS, M), 'vampiric_glove': ('minecraft:ghast_tear', 'grave_dust', SANDS, B),
    'villager_hat': ('minecraft:wheat', 'raider_insignia', SCRAP, I), 'warp_drive': ('minecraft:ender_eye', 'void_shard', SANDS, M),
    'whoopee_cushion': ('minecraft:slime_ball', 'feral_fang', SCRAP, I), 'withered_bracelet': ('minecraft:wither_rose', 'grave_dust', SANDS, B)}
os.makedirs(f'{out}/artifacts')
for artifact, (base, trophy, material, part) in ARTIFACTS.items():
    crafting(f'artifacts/{artifact}', ['PTP', 'MBM', 'PTP'], {'P': part, 'T': T(trophy), 'M': material, 'B': base}, {'id': f'artifacts:{artifact}', 'count': 1})
print('materials, gems', len(GEMS), 'relics', len(RELICS), 'artifacts', len(ARTIFACTS))
