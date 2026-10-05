// Copy this file to your modpack's kubejs/startup_scripts directory, then restart Minecraft.
// Startup scripts run during loading, which is when refinery definitions must be registered.

const RefineryDefinitions = Java.loadClass(
  'com.digitscodecompendium.terraindustry.refinery.RefineryDefinitions'
)

// A refinery runs all day when no activeBetween calls are supplied.
// It begins operating only after startsAt, if one is supplied. Times are UTC.
RefineryDefinitions.refinery('yourmod:iron_refinery')
  .startsAt('2026-09-01T00:00:00Z') // Optional; remove this line for no delayed start.
  .activeBetween('06:00-10:00')
  .activeBetween('18:30-23:00') // Windows can cross midnight: '22:00-02:00'.
  .cycleTicks(120)
  .fuelItem('minecraft:coal', 1, 120) // item id, amount, ticks between fuel consumption
  .transform('minecraft:stone', 'minecraft:iron_ore', 0.10)
  .crystallize('minecraft:iron_ore', 'minecraft:amethyst_cluster', 0.10)
  .register() // Adds terraindustry:iron_refinery, bound only to this definition.

// Calling transform again for the same input adds another weighted outcome.
// The combined chance for one input must not exceed 1.0.
RefineryDefinitions.refinery('yourmod:ore_upgrader')
  .cycleTicks(200)
  .fuelFluid('minecraft:lava', 250, 200) // fluid id, millibuckets, interval in ticks
  .transform('minecraft:iron_ore', 'minecraft:gold_ore', 0.15)
  .transform('minecraft:iron_ore', 'minecraft:diamond_ore', 0.02)
  .controller('ore_upgrader_controller') // Optional: choose the controller block path.
  .register() // Adds terraindustry:ore_upgrader_controller.

// Other available resource helpers:
//   .fuelEnergy(1000, 20)
//   .coolantItem('minecraft:ice', 1, 200)
//   .coolantFluid('minecraft:water', 1000, 200)
// Coolant configuration is saved now for the upcoming coolant mechanics.
