# The guide book (Patchouli, expedition:guide): categories and entries, texts as (ru, en) pairs, pages split by '\n\n'.
# Patchouli markup: $(br) line break, $(l) bold-ish highlight ... $() reset; $(item)...$() for item names.

BOOK = {
    'name': ('Путеводитель экспедиции', 'Expedition Guide'),
    'landing': ('Путеводитель по сшитому миру: что это за мир, что делать сначала, как растёт снаряжение и куда ведёт сюжет.$(br2)Сюжетные главы открываются по мере игры.',
                'A guide to the stitched world: what this world is, what to do first, how your gear grows and where the story leads.$(br2)Story chapters open as you play.'),
}

# category id -> (icon, (name), (description), sortnum)
CATEGORIES = {
    'start': ('minecraft:compass', ('Начало', 'Getting started'), ('Мир, сюжет и первые шаги', 'The world, the story and first steps'), 0),
    'world': ('minecraft:filled_map', ('Мир', 'The world'), ('Края, корабли и карта', 'Realms, airships and the map'), 1),
    'dungeons': ('minecraft:iron_sword', ('Данжи и боссы', 'Dungeons and bosses'), ('Гарнизоны, сердца, лут', 'Garrisons, hearts, loot'), 2),
    'gear': ('create:precision_mechanism', ('Снаряжение и развитие', 'Gear and progression'), ('Create, реликвии, Apotheosis', 'Create, relics, Apotheosis'), 3),
    'story': ('expedition:seam_compass', ('Сюжет', 'Story'), ('Главы открываются по ходу игры', 'Chapters open as you play'), 4),
}

# entry id -> (category, icon, (title), (text), sortnum, advancement lock or None)
ENTRIES = {
    'welcome': ('start', 'minecraft:compass', ('Сшитый мир', 'The stitched world'), (
        'Когда-то миры стояли порознь: Верхний мир, Сумеречный лес, Незер, Иная сторона, Край и Эфир. Разлом сшил их в один. Теперь это $(l)края$() одного мира, и их стражи-боссы обезумели. Сшитый мир кончается у ледяного хребта: за ним Разлом не пускает никого.\n\n'
        'Сюжет идёт $(l)главами$(). В главе открывается сразу несколько данжей и боссов. Проходить их можно в любом порядке. Дорогу показывает $(l)огнесемя$(), семя Древа: брось его в горящий костёр: семя заберёт огонь (костёр погаснет), и пламя прорастёт к стражу. Чтобы бросить ещё одно, костёр снова разжигают. Огнесемена делаются из семян, красной пыли и угля.$(br2)'
        'У каждого данжа главы свой страж. Когда он пал, место данжа отмечается черепом на карте у всех игроков, а огнесемя ведёт уже к следующему. Падут три четверти стражей главы, и сама откроется следующая. Огнесемя ведёт от лёгких стражей к трудным.\n\nИстория на сервере одна на всех. Пройденный данж можно пройти снова: $(l)маяк сброса$() возродит его сильнее (NG+).\n\n'
        'Прогресс сюжета виден в достижениях, во вкладке «Сшитый мир». Там же порядок глав и все стражи. Раздел «Сюжет» этой книги пополняется с каждой главой.',
        'The worlds once stood apart: the Overworld, the Twilight Forest, the Nether, the Otherside, the End and the Aether. The Rift stitched them into one. Now they are $(l)realms$() of one world, and their guardian bosses have gone mad. The stitched world ends at a ridge of ice: beyond it the Rift lets no one pass.\n\n'
        'The story comes in $(l)chapters$(). A chapter opens several dungeons and bosses at once. They can be done in any order. The way is shown by a $(l)fireseed$(), a seed of the Tree: throw it into a lit campfire: the seed takes the fire (the campfire goes out) and the flame grows towards a guardian. Light the campfire again for the next one. Fireseeds are made from seeds, redstone and coal.$(br2)'
        'Every dungeon of a chapter has its guardian. When it falls, the dungeon\'s place is marked with a skull on every player\'s map, and the fireseed leads on to the next. When three quarters of a chapter\'s guardians have fallen, the next chapter opens by itself. The fireseed leads from the easy guardians to the hard.\n\nThe server has one story for everyone. A dungeon done can be done again: the $(l)reset beacon$() revives it stronger (NG+).\n\n'
        'Story progress shows in the advancements, tab "The Stitched World", along with the chapter order and every guardian. The Story section of this book grows with each chapter.'), 0, None),
    'first_steps': ('start', 'create:andesite_alloy', ('Первые шаги', 'First steps'), (
        'Дерево, камень, укрытие: как обычно. Дальше всё держится на $(l)Create$().$(br2)'
        '1. Андезитовый сплав: андезит и железные самородки.$(br)2. Водяное колесо или ветряк, валы, шестерни.$(br)3. Жернова и дробилка: руда даёт больше.$(br)4. Пресс: листы для снаряжения.$(br)5. Миксер и чаша.\n\n'
        'Железные и золотые инструменты и броня делаются из $(l)листов$() Create на верстаке. Алмазное и выше требует механического крафтера и точных механизмов.$(br2)'
        'Сундуки данжей дают ходовые материалы, а не готовые вещи: всё полезное делается своими руками и машинами.',
        'Wood, stone, shelter: as usual. After that everything rests on $(l)Create$().$(br2)'
        '1. Andesite alloy: andesite and iron nuggets.$(br)2. A water wheel or windmill, shafts, cogs.$(br)3. Millstone and crusher: ore gives more.$(br)4. Press: sheets for gear.$(br)5. Mixer and basin.\n\n'
        'Iron and gold tools and armour are made from Create $(l)sheets$() at a crafting table. Diamond and up needs the mechanical crafter and precision mechanisms.$(br2)'
        'Dungeon chests give common materials, not finished items: everything worth having is made by hand and machine.'), 1, None),
    'realms': ('world', 'minecraft:grass_block', ('Края', 'Realms'), (
        'Мир состоит из огромных областей. Обычные биомы занимают километры. Между ними лежат $(l)края$(): каждый — один биом бывшего мира, такой же большой.\n\n'
        '$(l)Сумеречный лес$(): вечные сумерки, свои стражи.$(br)$(l)Незер$(): выжженная земля и лавовые озёра под открытым небом.$(br)$(l)Иная сторона$(): скалковые земли.$(br)'
        '$(l)Край$(): острова над пустотой, падать нельзя.$(br)$(l)Эфир$(): острова высоко в небе, под ними пустота.\n\n'
        'Порталов больше нет: до любого края можно дойти или долететь. Вокруг места высадки всегда обычный мир, а все края лежат в нескольких километрах от него.',
        'The world is made of huge areas. Ordinary biomes span kilometres. Between them lie $(l)realms$(): each is one biome of a former world, just as big.\n\n'
        '$(l)Twilight Forest$(): eternal dusk, guardians of its own.$(br)$(l)Nether$(): scorched land and lava lakes under the open sky.$(br)$(l)Otherside$(): sculk lands.$(br)'
        '$(l)End$(): islands over the void, do not fall.$(br)$(l)Aether$(): islands high in the sky, void below.\n\n'
        'There are no portals any more: every realm can be reached on foot or by air. The landing site is always surrounded by ordinary land; all realms lie a few kilometres away.'), 0, None),
    'airships': ('world', 'aeronautics:white_envelope', ('Корабли', 'Airships'), (
        'Расстояния тут большие, а Край и Эфир висят над пустотой. Поэтому главное средство передвижения — $(l)корабли Create Aeronautics$().\n\n'
        'Подъём даёт горячий воздух ($(item)горелка$() и $(item)оболочки$() из шерсти) или $(item)левитит$(). Тягу дают $(item)пропеллеры$() на подшипниках, питание идёт от вращения Create.$(br2)'
        'Первый корабль реально построить к главе II, когда появится латунь.\n\n'
        'Шерсть в постройках данжей заменена ветхой парусиной без дропа. Шерсть берут с овец и находят в сундуках.',
        'Distances are long here, and the End and the Aether hang over the void. So the main way to travel is $(l)Create Aeronautics airships$().\n\n'
        'Lift comes from hot air (a $(item)burner$() and wool $(item)envelopes$()) or $(item)levitite$(). Thrust comes from $(item)propellers$() on bearings, powered by Create rotation.$(br2)'
        'A first ship is realistic by chapter II, once you have brass.\n\n'
        'Wool in dungeon buildings is replaced by worn canvas that drops nothing. Get wool from sheep and chests.'), 1, None),
    'map': ('world', 'minecraft:map', ('Карта', 'The map'), (
        'Миникарта всегда в углу экрана, полная карта открывается клавишей M.$(br2)'
        'Разведанное показано в цвете, по блоку на точку. Неразведанное — бледным наброском биомов: так видно, где лес, где море, где край.\n\n'
        'Наведи курсор на карту, чтобы увидеть название биома. Красные кресты — данжи и логова глав сюжета: каждый подписан.',
        'The minimap is always in the corner; the full map opens with M.$(br2)'
        'Explored land is drawn in colour, one dot per block. Unexplored land is a faint sketch of biomes: you can see where forest, sea or a realm lies.\n\n'
        'Hover over the map to see the biome\'s name. Red crosses are the dungeons and lairs of the story chapters, each one labelled.'), 2, None),
    'garrisons': ('dungeons', 'minecraft:spawner', ('Гарнизоны', 'Garrisons'), (
        'Спавнеров в данжах нет. У каждого данжа свой $(l)гарнизон$(): запас врагов, который зависит от размера постройки и числа сундуков. Враги подходят по нескольку, пока запас не кончится.\n\n'
        'Пока гарнизон жив, стены данжа не ломаются и не взрываются, а внутри нельзя строить. Когда пал последний враг, данж $(l)зачищен$(): над экраном видна полоса гарнизона.$(br2)'
        'Враги гарнизона роняют $(l)трофеи$(): прах, хитин, знаки рейдеров, чешую, пепельные ядра, осколки пустоты, клыки. На трофеях держится почти всё продвинутое снаряжение.',
        'Dungeons have no spawners. Each dungeon has its own $(l)garrison$(): a stock of enemies sized by the building and its chests. They come a few at a time until the stock runs out.\n\n'
        'While the garrison lives, the dungeon\'s walls cannot be broken or blown up and nothing can be built inside. When the last enemy falls, the dungeon is $(l)cleared$(): the garrison bar is shown on screen.$(br2)'
        'Garrison enemies drop $(l)trophies$(): dust, chitin, raider insignia, scales, cinder cores, void shards, fangs. Almost all advanced gear rests on trophies.'), 0, None),
    'bosses': ('dungeons', 'expedition:boss_heart', ('Боссы и сердца', 'Bosses and hearts'), (
        'Боссы не роняют готовое оружие. Каждый оставляет $(l)сердце$() со своим именем.$(br2)'
        'Из сердец на машинах Create делается снаряжение босса, редкие материалы Apotheosis и реликвии.\n\n'
        '$(l)Маяк сброса$() возрождает зачищенный данж: тот же гарнизон, но сильнее. Так можно добыть сердце ещё раз.',
        'Bosses do not drop finished weapons. Each leaves a $(l)heart$() bearing its name.$(br2)'
        'On Create machines hearts become the boss\'s gear, rare Apotheosis materials and relics.\n\n'
        'The $(l)reset beacon$() revives a cleared dungeon: the same garrison, only stronger. That is how you get a heart again.'), 1, None),
    'loot': ('dungeons', 'minecraft:chest', ('Лут', 'Loot'), (
        'Сундуки дают ходовые материалы: нитки, кожу, бумагу, андезит, брёвна, самородки, сырую руду, порох, красную пыль. Чем дальше от места высадки, тем ценнее: кварц и жемчуг, потом алмазы, незерит, осколки эха.\n\n'
        'Готового снаряжения, артефактов и реликвий в сундуках нет: их делают сами.',
        'Chests hold common materials: string, leather, paper, andesite, logs, nuggets, raw ore, gunpowder, redstone. The farther from the landing site, the more valuable: quartz and pearls, then diamonds, netherite, echo shards.\n\n'
        'Chests hold no finished gear, artifacts or relics: those are made, not found.'), 2, None),
    'create_pipeline': ('gear', 'create:mechanical_crafter', ('Путь Create', 'The Create path'), (
        '$(l)Андезит$(): сплав, валы, пресс, жернова. Железные и золотые вещи из листов.$(br2)'
        '$(l)Латунь$(): нужна горелка ифрита (стержни из крепостей Незера). Латунь даёт механический крафтер, электронные трубки и корабли.\n\n'
        '$(l)Точный механизм$() (последовательная сборка): алмазное снаряжение.$(br2)'
        '$(l)Прочные листы$() (порошок обсидиана) и $(l)сталь TFMG$(): снаряжение из незерита и мощнейшее оружие боссов.\n\n'
        'Если не знаешь, как сделать вещь, ищи её в JEI: у каждого рецепта показана машина.',
        '$(l)Andesite$(): alloy, shafts, press, millstone. Iron and gold items from sheets.$(br2)'
        '$(l)Brass$(): needs a blaze burner (rods from Nether fortresses). Brass gives the mechanical crafter, electron tubes and airships.\n\n'
        '$(l)Precision mechanism$() (sequenced assembly): diamond gear.$(br2)'
        '$(l)Sturdy sheets$() (obsidian powder) and $(l)TFMG steel$(): netherite gear and the strongest boss weapons.\n\n'
        'If you do not know how something is made, look it up in JEI: every recipe shows its machine.'), 0, None),
    'relics': ('gear', 'relics:chorus_staff', ('Реликвии и артефакты', 'Relics and artifacts'), (
        '$(l)Реликвии$() (Relics) и $(l)артефакты$() не выпадают ни из сундуков, ни с мобов. Их собирают на $(l)механическом крафтере$(): основа, два трофея, два материала редкости и детали Create.\n\n'
        'Реликвии растут в уровне от использования. Открывай их способности по мере роста.$(br2)Самые сильные реликвии и артефакты требуют сердец боссов поздних глав.',
        '$(l)Relics$() and $(l)artifacts$() drop from neither chests nor mobs. They are built on the $(l)mechanical crafter$(): a base, two trophies, two rarity materials and Create parts.\n\n'
        'Relics level up as you use them. Unlock their abilities as they grow.$(br2)The strongest relics and artifacts need hearts of late-chapter bosses.'), 1, None),
    'apotheosis': ('gear', 'minecraft:enchanting_table', ('Зачарование и аффиксы', 'Enchanting and affixes'), (
        'Снаряжение с аффиксами и самоцветы Apotheosis не выпадают. Материалы редкости делаются через Create:$(br2)'
        'Загадочный лом: трофей и железный лист в миксере.$(br)Ветхая ткань: нагрев (прах, хитин, клык).$(br)Сияющий кристалл: нагрев (чешуя, знак рейдера).$(br)Магические пески: сильный нагрев (пепельное ядро, осколок пустоты).$(br)Жемчуг богов: сердце босса на механическом крафтере.\n\n'
        'Самоцветы делаются на механическом крафтере, улучшаются на гранильном столе. Аффиксы — на столе перековки за материалы редкости.',
        'Affixed gear and Apotheosis gems do not drop. Rarity materials are made through Create:$(br2)'
        'Mysterious scrap: a trophy and an iron sheet in a mixer.$(br)Timeworn fabric: heated (dust, chitin, fang).$(br)Luminous crystal: heated (scale, raider insignia).$(br)Arcane sands: superheated (cinder core, void shard).$(br)Godforged pearl: a boss heart on the mechanical crafter.\n\n'
        'Gems are made on the mechanical crafter and upgraded at the gem cutting table. Affixes come from the reforging table, paid in rarity materials.'), 2, None),
}

# Story chapter entries, locked by the act advancements; texts reuse the act titles and add the goal.
STORY_ENTRIES = {
    1: ('Место высадки окружено первыми стражами: Старое подземелье, Склеп, Кованая палата Железного рыцаря и Подводный монумент Древнего стража.$(br2)Цель: $(l)Компас шва$() из сердец Железного рыцаря и Древнего стража (верстак: компас, листы, сплав).',
        'The landing site is ringed by the first guardians: the Old Dungeon, the Crypt, the Wrought Chamber of the Ferrous Wroughtnaut and the Ocean Monument of the Elder Guardian.$(br2)Goal: the $(l)Seam Compass$() from the Wroughtnaut\'s and Elder Guardian\'s hearts (crafting table: compass, sheets, alloy).'),
    2: ('Огонь под швами: корабль Корнелии во льдах, крепость Незера (стержни ифрита — латунь!), гнездо грибуса, роща Умвутаны, мшистый храм Колосса, двор Наги в Сумеречном лесу.$(br2)Цель: $(l)Латунная астролябия$() из сердец Корнелии и Колосса (механический крафтер).',
        'Fire beneath the seams: Cornelia\'s ship in the ice, a Nether fortress (blaze rods mean brass!), the fungussus nest, the Umvuthana grove, the Colossus\'s mossy temple, the Naga courtyard in the Twilight Forest.$(br2)Goal: the $(l)Brass Astrolabe$() from Cornelia\'s and the Colossus\'s hearts (mechanical crafter).'),
    3: ('Небесный путь: пустыни, ледяной храм, литейная, крепость, арена Подземного рыцаря, Сумеречный лич, бронзовый данж Эфира и дракон Края на своём острове.$(br2)Цель: $(l)Око разлома$() из сердец дракона и Подземного рыцаря (точные механизмы).',
        'The sky road: deserts, an ice temple, a foundry, a stronghold, the Underworld Knight\'s arena, the Twilight Lich, the Aether bronze dungeon and the dragon of the End on its island.$(br2)Goal: the $(l)Rift Eye$() from the dragon\'s and the Underworld Knight\'s hearts (precision mechanisms).'),
    4: ('Разорванные земли: йети, Фростмо, скелетозавр, крепость душ, небесный кит, башня Лича, рукавица Незера, инфернальный дракон, Кракен, цитадель Стража Края, лабиринт Минотавра, Гидра, серебряный данж Эфира.$(br2)Цель: $(l)Небесный якорь$() из сердец Стража Края и Лича.',
        'The torn lands: the yeti, Frostmaw, the skeletosaurus, the soul fortress, the sky whale, the Lich tower, the Nether gauntlet, the infernal dragon, the Kraken, the Ender Guardian\'s citadel, the Minoshroom labyrinth, the Hydra, the Aether silver dungeon.$(br2)Goal: the $(l)Sky Anchor$() from the Ender Guardian\'s and the Lich\'s hearts.'),
    5: ('Кузни и пустота: кузня душ, огненная арена Игниса, облачный храм, павшее королевство, пустотный цветок, Обсидилит, космическая станция, крепость рыцарей-призраков, Тёмная башня, золотой данж Эфира.$(br2)Цель: $(l)Горнило пустоты$() из сердец Игниса и Монстра из незерита.',
        'Forges and the void: the soul forge, Ignis\'s burning arena, the cloud temple, the fallen kingdom, the void blossom, the Obsidilith, the space station, the knight phantom stronghold, the Dark Tower, the Aether gold dungeon.$(br2)Goal: the $(l)Void Crucible$() from Ignis\'s and the Netherite Monstrosity\'s hearts.'),
    6: ('Древние: древняя фабрика Предвестника, затонувший город Левиафана, Иссушитель в святилище Незера, проклятая пирамида, ледяная тюрьма, тюрьма Лича, руины древней башни, пещера йети и дворец Авроры.$(br2)Цель: $(l)Семя Древа$() из сердец Предвестника, Левиафана и Иссушителя и звезды Незера.',
        'The Ancients: the Harbinger\'s ancient factory, the Leviathan\'s sunken city, the Wither in its Nether sanctum, the cursed pyramid, the frosted prison, the Lich prison, the ancient tower ruins, the yeti cave and the Aurora palace.$(br2)Goal: the $(l)Seed of the Tree$() from the Harbinger\'s, Leviathan\'s and Wither\'s hearts and a Nether star.'),
    7: ('Древо: Акрополь Сциллы и три последних стража — Малкут, Хесед и Гвура.$(br2)Когда падут все трое, шов замкнётся.',
        'The Tree: Scylla\'s Acropolis and the three last guardians, Malkuth, Chesed and Geburah.$(br2)When all three fall, the seam closes.'),
}
