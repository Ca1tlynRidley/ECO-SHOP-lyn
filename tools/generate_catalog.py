"""Generate editable prices from the official 26.2 item definitions and the project's exclusion policy."""
import argparse, json, pathlib, re, zipfile
from decimal import Decimal
root = pathlib.Path(__file__).resolve().parent.parent
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--client-jar',type=pathlib.Path,default=pathlib.Path.home()/'.gradle/caches/fabric-loom/26.2/minecraft-client.jar')
parser.add_argument('--old-client-jar',type=pathlib.Path,default=pathlib.Path.home()/'.gradle/caches/fabric-loom/1.20.1/minecraft-client.jar')
args=parser.parse_args()
with zipfile.ZipFile(args.client_jar) as z:
    catalog=[{'id':'minecraft:'+pathlib.PurePosixPath(p).stem} for p in sorted(z.namelist()) if p.startswith('assets/minecraft/items/') and p.endswith('.json')]
policy = (root/'src/main/java/dev/lyn/shop/CatalogPolicy.java').read_text(encoding='utf-8')
excluded = set(re.findall(r'"([a-z0-9_]+)"', policy.split('EXCLUDED = Set.of(')[1].split(');')[0]))
oldjar = args.old_client_jar
with zipfile.ZipFile(oldjar) as z:
    old = {pathlib.PurePosixPath(p).stem for p in z.namelist() if p.startswith('assets/minecraft/models/item/') and p.endswith('.json')}
def allowed(p):
    return p not in excluded and 'netherite' not in p and not p.endswith(('_spawn_egg','_pottery_sherd','_armor_trim_smithing_template','_shulker_box')) and not p.startswith(('music_disc_','infested_')) and 'sherd' not in p
special = {
    'totem_of_undying':('300','1.00'), 'diamond':('5000','25'), 'emerald':('7500','0.25'), 'iron_ingot':('150','0.50'), 'gold_ingot':('250','0.75'), 'copper_ingot':('30','0.10'),
    'coal':('20','0.05'), 'charcoal':('20','0.05'), 'redstone':('25','0.05'), 'lapis_lazuli':('40','0.05'), 'quartz':('50','0.05'),
    'diamond_block':('45000','225'), 'emerald_block':('67500','2.25'), 'iron_block':('1350','4.50'), 'gold_block':('2250','6.75'), 'copper_block':('270','0.90'),
    'raw_iron':('100','0.35'), 'raw_gold':('175','0.50'), 'raw_copper':('20','0.05'), 'raw_iron_block':('900','3.15'), 'raw_gold_block':('1575','4.50'), 'raw_copper_block':('180','0.45'),
    'gold_nugget':('30','0.08'), 'iron_nugget':('20','0.05'), 'egg':('15','0.02'), 'blue_egg':('20','0.02'), 'brown_egg':('20','0.02'),
    'wheat':('20','0.02'), 'carrot':('20','0.02'), 'potato':('20','0.02'), 'beetroot':('20','0.02'), 'sugar_cane':('25','0.01'), 'bamboo':('10','0.01'), 'kelp':('10','0.01'), 'cactus':('20','0.01'), 'stick':('2','0.01'),
    'cobblestone':('5','0.01'), 'stone':('8','0.01'), 'dirt':('5','0.01'), 'sand':('10','0.01'), 'gravel':('10','0.01'), 'netherrack':('10','0.01'),
    'apple':('30','0.02'), 'bread':('60','0.05'), 'cooked_beef':('100','0.10'), 'cooked_porkchop':('100','0.10'), 'golden_apple':('3000','0.50'), 'golden_carrot':('500','0.10'),
    'experience_bottle':('1500','0.05'), 'name_tag':('2500','0.05'), 'saddle':('3000','0.05'), 'ender_pearl':('1000','0.10'), 'blaze_rod':('1500','0.10'),
    'gunpowder':('100','0.02'), 'slime_ball':('150','0.02'), 'leather':('100','0.05'), 'string':('25','0.01'), 'bone':('25','0.01'),
}
for material,price,sell in [('diamond',5000,5),('iron',150,.1),('golden',250,.1),('copper',30,.05),('stone',15,.01),('wooden',10,.01),('chainmail',300,.1),('leather',100,.02)]:
    for part,count in [('helmet',5),('chestplate',8),('leggings',7),('boots',4),('sword',2),('pickaxe',3),('axe',3),('shovel',1),('hoe',2),('spear',3)]:
        special[f'{material}_{part}']=(str(price*count*1.2),str(sell))
items = {}
for item in catalog:
    name=item['id'].split(':')[1]
    if not allowed(name): continue
    category='blocks'; buy,sell='100','0.01'
    if name.endswith(('_log','_wood','_stem','_hyphae')): buy='25'
    if name.endswith('_planks'): buy='8'
    if name.endswith(('_sword','_spear','_pickaxe','_axe','_shovel','_hoe','_helmet','_chestplate','_leggings','_boots')) or name in ('bow','crossbow','shield','arrow','totem_of_undying'): category='gear'; buy='250'
    elif name in ('redstone','repeater','comparator','hopper','piston','sticky_piston','observer','dispenser','dropper','crafter') or name.endswith(('_button','_pressure_plate','_rail')): category='redstone'; buy='500'
    elif any(word in name for word in ('seeds','sapling','flower','leaves','coral','bush','mushroom','grass','tulip','daisy','fern','moss','vines')): category='nature'; buy='25'
    elif name.endswith(('_ingot','_nugget','_dye','_shard')) or name in ('diamond','emerald','coal','charcoal','quartz','lapis_lazuli','string','leather','bone','gunpowder','slime_ball','stick','paper','blaze_rod'): category='ingredients'
    elif name in ('egg','blue_egg','brown_egg','wheat','carrot','potato','beetroot','apple','golden_apple','golden_carrot','bread','cooked_beef','cooked_porkchop','beef','porkchop','chicken','cooked_chicken','melon_slice','pumpkin_pie','rabbit','cooked_rabbit','mutton','cooked_mutton','cod','cooked_cod','salmon','cooked_salmon','cookie','honey_bottle','sweet_berries','glow_berries','cake','dried_kelp'): category='food'
    elif name.endswith(('_bucket','_boat','_raft','_minecart','_chest_boat','_bundle','_harness')) or name in ('bucket','chest','ender_chest','crafting_table','furnace','smoker','blast_furnace','anvil','grindstone','enchanting_table','smithing_table','cartography_table','loom','stonecutter','saddle','name_tag','compass','clock','shears','brush','flint_and_steel','fishing_rod','bundle'): category='utility';buy='500'
    if name not in old: category='new'
    if name in special: buy,sell=special[name]
    items[item['id']]={'category':category,'buy':f'{Decimal(buy):.2f}','sell':f'{Decimal(sell):.2f}','enabled':True}
data={'schemaVersion':1,'catalogRevision':1,'maxPurchase':2304,'sellCooldownSeconds':{'iron':120,'gold':90,'diamond':45},'items':dict(sorted(items.items()))}
destination=root/'src/main/resources/default-prices.json'
destination.write_text(json.dumps(data,indent=2)+'\n',encoding='utf-8')
(root/'config').mkdir(exist_ok=True)
(root/'config/prices.json').write_bytes(destination.read_bytes())
print(f'{len(items)} products; {sum(x["category"]=="new" for x in items.values())} post-1.20.1 items. Diamonds: $5,000; emeralds: $7,500. No excluded rare/modded items.')
