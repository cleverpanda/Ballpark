# Ballpark

A ProjectE addon for Minecraft 1.21.1 (NeoForge). ProjectE works out EMC from recipes, starting from the values it
knows. Modded items made from things ProjectE doesn't know end up with no EMC, and so does everything crafted from
them. Ballpark gives those items a ballpark value from a few datapack rules, and ProjectE carries it into every
recipe that uses them.

In a test with Base Metals, Modern Metals, Create, Applied Energistics 2, Mystical Agriculture and Hephaestus Forge
(3,328 items that should have EMC, leaving out ores, fluids, creative items and the like), ProjectE alone priced 54%;
with Ballpark, 87%.

Requires ProjectE 1.1.x. Everything below is for pack makers; players don't need to do anything.

## How it decides

For each item, Ballpark tries the rules from the highest priority down and uses the first one that matches and can
price it. A rule either estimates a value or skips the item. A skipped item is left to ProjectE, which still values it
from recipes if it can.

An estimate doesn't replace anything. It's one more way for ProjectE to make the item, and ProjectE keeps whichever is
cheaper, the estimate or the recipes. Fixed values beat both: ProjectE's defaults (which already cover tin, copper,
silver, lead, nickel, platinum and a few more), your `custom_emc.json`, and fixed values from other mods. Ores and raw
materials stay at 0, as ProjectE intends.

Ballpark runs whenever ProjectE recalculates EMC, which happens when data is sent to players: when someone joins a
freshly started server, and on every `/reload`. Edit your datapack, run `/reload`, and check the result in game.

## The workflow

1. Install it and start the pack.
2. `/ballpark gaps` lists modded items that still have no EMC, counted per mod. Add a mod id to narrow it down:
   `/ballpark gaps somemod`.
3. `/ballpark export` writes every item's EMC and the rule behind it to `logs/ballpark-report.csv`. Open it in a
   spreadsheet and sort by mod or by rule.
4. Fix what you don't like with the datapack files below, then `/reload`.
5. Hold an item and run `/ballpark explain` (or `/ballpark explain somemod:thing`) to see its final EMC and which
   rule estimated it.

The commands need permission level 2 (operators).

## Common jobs

All examples go in a datapack (`world/datapacks/<yourpack>/data/...`, or wherever your pack loads datapacks from,
such as KubeJS or OpenLoader).

### Start from the shipped values

`ballpark-values-<version>.zip`, released next to the mod, is a datapack with a copy of every shipped rule, material
value and the exclusion tag. Drop it in `world/datapacks/`, edit the numbers, and `/reload`. A file in it replaces the
mod's file at the same path. Delete the files you don't change, so that mod updates still reach them.

### Stop Ballpark estimating specific items

Add them to the `ballpark:excluded` item tag. This beats every other rule.

`data/ballpark/tags/item/excluded.json`
```json
{
  "values": [
    "somemod:creative_battery",
    "#somemod:dev_tools"
  ]
}
```

It's an ordinary item tag, so KubeJS works too:
```js
ServerEvents.tags('item', event => {
  event.add('ballpark:excluded', 'somemod:creative_battery')
})
```

Excluding an item only stops the estimate. To force it to 0 or to a set value, use ProjectE's own `custom_emc.json`.

### Change the value of a material

Material values are per ingot or gem, in `data/<namespace>/ballpark/materials/<material>.json`. The file name is the
material: the part after `c:ingots/`, `c:gems/`, `c:dusts/` and so on.

`data/mypack/ballpark/materials/zinc.json`
```json
{
  "emc": 300
}
```

A value in your own namespace beats the one Ballpark ships. Adding a material that isn't listed works the same way.

When the material's ingot or gem already has EMC (from ProjectE or a recipe), the nugget, dust, block, plate, gear and
rod forms follow it automatically. The table value is only used when nothing else prices that material.

Shipped values: adamantine, aerogel, aluminium, ambrosium, antimony, apatite, awakened_draconium, beryllium, bismuth,
boron, brass, cadmium, charcoal, cinnabar, coal, cobalt, coldiron, constantan, continuum, draconium, electrum, elementium,
enchanted_gravitite, enderium, fluorite, golden_amber, gravitite, holystone, ice_stone, icestone, invar, iridium, lumium,
manasteel, mercury, mithril, nichrome, niter, plutonium, rutile, salt, saltpeter, signalum, skyroot, stainlesssteel,
starsteel, steel, sulfur, swet, tantalum, terrasteel, thorium, titanium, tungsten, uraninite, zanite, zinc, zirconium. (Tin, copper, bronze, silver, lead, nickel, platinum, osmium, aluminum, uranium, ruby, sapphire and
peridot are priced by ProjectE itself.)

### Give a mod's items a set value

`data/mypack/ballpark/rules/somemod_gems.json`
```json
{
  "priority": 2000,
  "match": { "items": "#somemod:magic_gems" },
  "estimate": { "type": "fixed", "emc": 4096 }
}
```

### Switch off a shipped rule

Put a file at the same path with `"enabled": false`. For example, to stop food estimates:

`data/ballpark/ballpark/rules/food.json`
```json
{
  "enabled": false
}
```

Or keep the rule but only for foods nothing crafts:
```json
{
  "priority": 100,
  "match": { "components": ["minecraft:food"], "without_recipe": true },
  "estimate": { "type": "food" }
}
```

### Price a new material form

`data/mypack/ballpark/rules/coins.json` (one coin = 1/9 of an ingot)
```json
{
  "priority": 400,
  "match": { "tag_prefix": "c:coins/" },
  "estimate": { "type": "material", "units": 0.111111 }
}
```

### Enchanted books and gear

Ballpark adds a ProjectE data component processor, "Ballpark Enchantments", that does two things for anything
carrying enchantments (enchanted books, and enchanted tools, weapons and armor):

- Each enchantment adds `emc_per_level ÷ weight × level` on top of the item's own value. Rarer enchantments have a
  lower weight, so they're worth more. With the default 5,000, a Mending book is a plain book plus 2,500, and a
  Sharpness V diamond sword is the sword plus 2,500.
- The enchantments are kept when the item is learned in a Transmutation Table or copied by an Energy Condenser, so a
  learned Mending book comes back out as a Mending book.

ProjectE has a processor like this too, but it's switched off in its code. Ours is set in
`config/ProjectE/mapping.toml` under `[processors.Ballpark-Enchantments]`:

| Setting | Default | Meaning |
|---|---|---|
| `enabled` | `true` | Add EMC for enchantments. |
| `persistent` | `true` | Keep enchantments when learned or copied. Off: learned items come back plain. |
| `emc_per_level` | `5000` | Value per level, before dividing by the enchantment's weight. |

The plain enchanted book needs a value of its own first, so the shipped `enchanted_book` rule makes it worth one book.

This is a balance call. Once enchantments are worth EMC, an XP farm and an enchanting table become a source of EMC.
A lower `emc_per_level` shrinks that, but makes learned books cheaper to copy.

### Items that carry data (Apotheosis gems and the like)

Some mods make one item and tell its variants apart with data components. Every Apotheosis gem is `apotheosis:gem`,
with the gem type and purity stored on it. ProjectE drops components it doesn't know about when an item is learned,
so a learned gem comes back blank and can't be taken out of the table.

A file in `data/<namespace>/ballpark/components/` names the components to keep, and can scale the EMC by one
component's value. The shipped one for Apotheosis:

`data/ballpark/ballpark/components/apotheosis_gems.json`
```json
{
  "neoforge:conditions": [{ "type": "neoforge:mod_loaded", "modid": "apotheosis" }],
  "items": "apotheosis:gem",
  "keep": ["apotheosis:gem", "apotheosis:purity"],
  "scale": {
    "component": "apotheosis:purity",
    "values": { "cracked": 1, "chipped": 2, "flawed": 4, "normal": 8, "flawless": 16, "perfect": 32 }
  }
}
```

The `values` keys are the component's value as you'd write it in `/give`. Only single strings and numbers can be
matched. With the shipped `apotheosis_gem` rule (512 EMC), a cracked gem is 512 and a perfect one 16,384.

`items` limits the file to those items (ids or a `#tag`); without it, it applies to every item. Always set it for a
component that can hold contents. The shipped `spawners.json` keeps `minecraft:block_entity_data`, which holds the
mob, only on `minecraft:spawner`. Many machines store their inventory and energy in that same component, so keeping it
everywhere would let players copy a full machine.

Be careful what you keep. Keeping Apotheosis's `affixes` would let players copy affixed loot for the price of the
plain item.

These files are applied by the "Ballpark Components" processor in `config/ProjectE/mapping.toml`, which can be
switched off or made non-persistent there.

### Files for mods that may not be installed

Every rule, material and component file can carry NeoForge's conditions. A rule that names another mod's item fails
to load without that mod, so guard it:

```json
{
  "neoforge:conditions": [{ "type": "neoforge:mod_loaded", "modid": "apotheosis" }],
  "priority": 1100,
  "match": { "items": "apotheosis:gem" },
  "estimate": { "type": "fixed", "emc": 512 }
}
```

## Commands

All need permission level 2 (operators).

| Command | Does |
|---|---|
| `/ballpark explain` | Final EMC of the item in your main hand, and the rule that estimated or skipped it. |
| `/ballpark explain <item>` | The same for any item, e.g. `/ballpark explain basemetals:zinc_ingot`. Components work too: `/ballpark explain minecraft:enchanted_book[stored_enchantments={levels:{"minecraft:mending":1}}]` also lists what's kept when it's learned. The command's result is the EMC, for `execute store`. |
| `/ballpark gaps` | Count of items with no EMC per mod, and the first 10 of them. Items a rule skips aren't counted. |
| `/ballpark gaps <modid>` | The same, for one mod. |
| `/ballpark export` | Writes every item's EMC, rule and estimate to `logs/ballpark-report.csv`. |

## Rule reference

Rules are files in `data/<namespace>/ballpark/rules/`. A file with the same path in a later datapack replaces the
earlier one.

| Field | Default | Meaning |
|---|---|---|
| `priority` | `0` | Higher runs first. |
| `match` | everything | Which items the rule applies to (below). |
| `estimate` | `skip` | What to do with them (below). |
| `enabled` | `true` | `false` switches the rule off. |

### `match`

Every condition you give must hold.

| Field | Example | Meaning |
|---|---|---|
| `items` | `"#c:ingots"`, `["somemod:a", "somemod:b"]` | Item ids or one tag. |
| `namespaces` | `["somemod"]` | Mod ids. |
| `tag_prefix` | `"c:ingots/"` | The item has a tag starting with this; the rest of the tag (`tin` in `c:ingots/tin`) is the material. With several such tags (`c:dusts/chrome` and `c:dusts/chromium`), each material is tried until one prices. |
| `components` | `["minecraft:food"]` | Data components the item has by default. |
| `without_recipe` | `true` | Only items no recipe makes. |

### `estimate`

| `type` | Fields | Value |
|---|---|---|
| `skip` | | Don't estimate. |
| `fixed` | `emc` | That many EMC. |
| `material` | `units` | `units` of the matched material: converted from its `c:ingots/` or `c:gems/` tag, plus the materials table value if there is one. Use whole numbers or 1/n (`0.5`, `0.111111`). |
| `repair_material` | `units` | `units` of the item's own repair item (tool tier or armor material). |
| `from_item` | `item`, `count` (1) | Worth `count` of another item, at whatever ProjectE values it. |
| `food` | `base` (32), `per_nutrition` (8), `per_saturation` (4) | `base + nutrition × per_nutrition + saturation × per_saturation`. |

### Shipped rules

| Rule | Priority | Does |
|---|---|---|
| `excluded` | 10000 | Skips `#ballpark:excluded`. |
| `enchanted_book` | 1100 | Makes `minecraft:enchanted_book` worth one book, so the enchantment processor has a base to add to. |
| `apotheosis_gem` | 1100 | Apotheosis gems are 512 before purity scaling. Loads only with Apotheosis. |
| `spawner` | 1100 | Monster spawners are 65,536 (eight diamonds), whatever mob they hold; learned spawners keep their mob. |
| `vanilla_and_projecte` | 1000 | Skips `minecraft` and `projecte` items; ProjectE already values them. |
| `ores`, `raw_materials` | 900 | Skips `#c:ores` and `#c:raw_materials`; ProjectE keeps these at 0. |
| `ingots`, `gems` | 600 | 1 unit of material. |
| `dusts`, `plates` | 500 | 1 unit. |
| `blends` | 500 | 1 unit (`c:blends/`, the dust mixes that smelt into alloys). |
| `nuggets`, `storage_blocks`, `gears`, `rods`, `casings`, `dense_plates` | 400 | 1/9, 9, 4, 1/2, 1/2 and 9 units. |
| `untagged_casings`, `untagged_dense_plates` | 400 | The same, for Modern Metals' casings and dense plates, which have no `c:` tags. Ballpark tags them `ballpark:casings/<metal>` and `ballpark:dense_plates/<metal>`. |
| `natural_stones` | 200 | 1 EMC for `#c:stones` blocks nothing crafts. |
| `create_limestone` | 200 | 1 EMC for Create's limestone, which only generates in the world. Loads only with Create. |
| `pickaxes`, `axes`, `swords`, `shovels`, `hoes`, `head_armor`, `chest_armor`, `leg_armor`, `foot_armor` | 300 | 3, 3, 2, 1, 2, 5, 8, 7 and 4 repair items; only gear nothing crafts (loot-only gear). |
| `food` | 100 | Food formula with the defaults above. |

Nothing ships for "everything else". One flat value would make rare items cheap to transmute and cap the price of
expensive ones, so use `/ballpark gaps` and price what matters in your pack.

## Gotchas

Watch for free EMC. A low estimate is harmless until some recipe turns the item back into what it's made of
(recycling, uncrafting). Then players can make EMC from nothing, and ProjectE responds by setting the item to 0.
ProjectE logs some of these as `EMC Exploit` warnings. If a whole family of items suddenly reads 0, look for a recipe
like that, then exclude the item or add `"without_recipe": true` to the rule. Base Metals' crackhammer recycling
recipes are what turned this up: pricing crackhammers as 3 ingots zeroed every zinc and steel item.

ProjectE lists Ballpark in `config/ProjectE/mapping.toml` with its own mappers, so you can turn it off there.

On a dedicated server, players' tooltips show a component-scaled item (a perfect gem) at its unscaled value. The
server's value is the one the table and condenser use.

With ProjectE's `usePregenerated` on, no mappers run at all, Ballpark included. `explain` and `gaps` tell you so.

Ballpark's rules can't see conversions from ProjectE's custom conversion files or other mods' mappers. ProjectE still
weighs those against the estimate as usual.

ProjectE only reads crafting, smelting, stonecutting and smithing recipes. Anything made in a mod's own machine
(an inscriber, an infusion altar, a casting table) gets no EMC from that recipe, and neither does everything made
from it. ProjectE Integration adds readers for many mods' machines, Applied Energistics 2 and Mystical Agriculture
among them; install it first, then run `/ballpark gaps` to see what's left. When a mod moves a basic item into its
own machine (glass from a glassblowing table instead of a furnace, say), that item and everything made from it lose
their EMC. Give it a value in `custom_emc.json`.

## Building

```bash
./gradlew build
```

This makes the mod jar and the values datapack, both in `build/libs/`. ProjectE isn't on a maven this build can reach; its jar lives in `libs/` (`projecte_jar` in `gradle.properties`).
Releases follow the MMD template: tag a commit with the Minecraft and mod version, for example `1.21.1-1.0.0`.
