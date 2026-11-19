# Ballpark values

This datapack holds a copy of every rule, material value and the exclusion tag that Ballpark ships. A file here
replaces the mod's file at the same path, so you change a value by editing it here.

1. Put this zip (or the unzipped folder) in `world/datapacks/`, or wherever your pack loads datapacks from.
2. Edit what you want to change:
   - `data/ballpark/ballpark/materials/<material>.json`: EMC for one ingot or gem of that material. Nuggets,
     dusts, blocks, plates, gears, rods, casings and dense plates follow it. Add a file to price a new material.
   - `data/ballpark/ballpark/rules/<rule>.json`: what each rule matches and how it estimates. Set
     `"enabled": false` to switch one off.
   - `data/ballpark/ballpark/components/<name>.json`: data components kept when an item is learned (such as
     Apotheosis gem type and purity), and how they scale its EMC.
   - `data/ballpark/tags/item/excluded.json`: items Ballpark must never estimate.
3. Run `/reload`, then check with `/ballpark explain <item>` and `/ballpark gaps`.

Delete the files you didn't change. A file left here keeps its old contents when a new Ballpark version changes
that file in the mod.

The full guide is the README on the mod page.
