# EMC Ballpark

A ProjectE addon for Minecraft 1.21.1 (NeoForge). ProjectE works out EMC from recipes, starting from the values it
knows. Modded items made from things ProjectE doesn't know end up with no EMC, and so does everything crafted from
them. EMC Ballpark gives those items a ballpark value from a few datapack rules, and ProjectE carries it into every
recipe that uses them.

In a test with Base Metals and a handful of other mods, ProjectE alone gave EMC to 564 of 1,499 modded items; with
EMC Ballpark, 1,125.

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
2. `/emcballpark gaps` lists modded items that still have no EMC, counted per mod. Add a mod id to narrow it down:
   `/emcballpark gaps somemod`.
3. `/emcballpark export` writes every item's EMC and the rule behind it to `logs/emcballpark-report.csv`. Open it in a
   spreadsheet and sort by mod or by rule.
4. Fix what you don't like with the datapack files below, then `/reload`.
5. Hold an item and run `/emcballpark explain` (or `/emcballpark explain somemod:thing`) to see its final EMC and which
   rule estimated it.

The commands need permission level 2 (operators).

## Common jobs

All examples go in a datapack (`world/datapacks/<yourpack>/data/...`, or wherever your pack loads datapacks from,
such as KubeJS or OpenLoader).

### Stop Ballpark estimating specific items

Add them to the `emcballpark:excluded` item tag. This beats every other rule.

`data/emcballpark/tags/item/excluded.json`
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
  event.add('emcballpark:excluded', 'somemod:creative_battery')
})
```

Excluding an item only stops the estimate. To force it to 0 or to a set value, use ProjectE's own `custom_emc.json`.

### Change the value of a material

Material values are per ingot or gem, in `data/<namespace>/emcballpark/materials/<material>.json`. The file name is the
material: the part after `c:ingots/`, `c:gems/`, `c:dusts/` and so on.

`data/mypack/emcballpark/materials/zinc.json`
```json
{
  "emc": 300
}
```

A value in your own namespace beats the one Ballpark ships. Adding a material that isn't listed works the same way.

When the material's ingot or gem already has EMC (from ProjectE or a recipe), the nugget, dust, block, plate, gear and
rod forms follow it automatically. The table value is only used when nothing else prices that material.

Shipped values: adamantine, aluminium, antimony, apatite, bismuth, brass, cinnabar, cobalt, coldiron, constantan,
electrum, fluorite, invar, iridium, mercury, mithril, niter, salt, saltpeter, starsteel, steel, sulfur, titanium,
tungsten, zinc. (Tin, copper, bronze, silver, lead, nickel, platinum, osmium, aluminum, uranium, ruby, sapphire and
peridot are priced by ProjectE itself.)

### Give a mod's items a set value

`data/mypack/emcballpark/rules/somemod_gems.json`
```json
{
  "priority": 2000,
  "match": { "items": "#somemod:magic_gems" },
  "estimate": { "type": "fixed", "emc": 4096 }
}
```

### Switch off a shipped rule

Put a file at the same path with `"enabled": false`. For example, to stop food estimates:

`data/emcballpark/emcballpark/rules/food.json`
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

`data/mypack/emcballpark/rules/coins.json` (one coin = 1/9 of an ingot)
```json
{
  "priority": 400,
  "match": { "tag_prefix": "c:coins/" },
  "estimate": { "type": "material", "units": 0.111111 }
}
```

## Commands

All need permission level 2 (operators).

| Command | Does |
|---|---|
| `/emcballpark explain` | Final EMC of the item in your main hand, and the rule that estimated or skipped it. |
| `/emcballpark explain <item>` | The same for any item, e.g. `/emcballpark explain basemetals:zinc_ingot`. |
| `/emcballpark gaps` | Count of items with no EMC per mod, and the first 10 of them. Items a rule skips aren't counted. |
| `/emcballpark gaps <modid>` | The same, for one mod. |
| `/emcballpark export` | Writes every item's EMC, rule and estimate to `logs/emcballpark-report.csv`. |

## Rule reference

Rules are files in `data/<namespace>/emcballpark/rules/`. A file with the same path in a later datapack replaces the
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
| `tag_prefix` | `"c:ingots/"` | The item has a tag starting with this; the rest of the tag (`tin` in `c:ingots/tin`) is the material. |
| `components` | `["minecraft:food"]` | Data components the item has by default. |
| `without_recipe` | `true` | Only items no recipe makes. |

### `estimate`

| `type` | Fields | Value |
|---|---|---|
| `skip` | | Don't estimate. |
| `fixed` | `emc` | That many EMC. |
| `material` | `units` | `units` of the matched material: converted from its `c:ingots/` or `c:gems/` tag, plus the materials table value if there is one. Use whole numbers or 1/n (`0.5`, `0.111111`). |
| `repair_material` | `units` | `units` of the item's own repair item (tool tier or armor material). |
| `food` | `base` (32), `per_nutrition` (8), `per_saturation` (4) | `base + nutrition × per_nutrition + saturation × per_saturation`. |

### Shipped rules

| Rule | Priority | Does |
|---|---|---|
| `excluded` | 10000 | Skips `#emcballpark:excluded`. |
| `vanilla_and_projecte` | 1000 | Skips `minecraft` and `projecte` items; ProjectE already values them. |
| `ores`, `raw_materials` | 900 | Skips `#c:ores` and `#c:raw_materials`; ProjectE keeps these at 0. |
| `ingots`, `gems` | 600 | 1 unit of material. |
| `dusts`, `plates` | 500 | 1 unit. |
| `nuggets`, `storage_blocks`, `gears`, `rods` | 400 | 1/9, 9, 4 and 1/2 units. |
| `pickaxes`, `axes`, `swords`, `shovels`, `hoes`, `head_armor`, `chest_armor`, `leg_armor`, `foot_armor` | 300 | 3, 3, 2, 1, 2, 5, 8, 7 and 4 repair items; only gear nothing crafts (loot-only gear). |
| `food` | 100 | Food formula with the defaults above. |

Nothing ships for "everything else". One flat value would make rare items cheap to transmute and cap the price of
expensive ones, so use `/emcballpark gaps` and price what matters in your pack.

## Gotchas

Watch for free EMC. A low estimate is harmless until some recipe turns the item back into what it's made of
(recycling, uncrafting). Then players can make EMC from nothing, and ProjectE responds by setting the item to 0.
ProjectE logs some of these as `EMC Exploit` warnings. If a whole family of items suddenly reads 0, look for a recipe
like that, then exclude the item or add `"without_recipe": true` to the rule. Base Metals' crackhammer recycling
recipes are what turned this up: pricing crackhammers as 3 ingots zeroed every zinc and steel item.

ProjectE lists EMC Ballpark in `config/ProjectE/mapping.toml` with its own mappers, so you can turn it off there.

With ProjectE's `usePregenerated` on, no mappers run at all, Ballpark included. `explain` and `gaps` tell you so.

Ballpark's rules can't see conversions from ProjectE's custom conversion files or other mods' mappers. ProjectE still
weighs those against the estimate as usual.

## Building

```bash
./gradlew build
```

ProjectE isn't on a maven this build can reach; its jar lives in `libs/` (`projecte_jar` in `gradle.properties`).
Releases follow the MMD template: tag a commit with the Minecraft and mod version, for example `1.21.1-1.0.0`.
