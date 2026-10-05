# Terra Industry

Terra Industry is a NeoForge 1.21.1 mod for chunk-local refineries. A controller owns
all refinery ports in its chunk; a port in an adjacent chunk is deliberately ignored.
The controller and all ports are unbreakable in survival. Controllers rediscover their
ports every second, which handles ports placed after the controller.

## Included starter refinery

The **Terra Industry** creative tab contains an **Iron Refinery** and one basic version of
every port. Place the Iron Refinery, a Basic Material Port, Basic Fuel Port, and Basic Product
Port, and Catalyst Blocks anywhere in the same chunk. Its refinery-owned operating policy
consumes one coal every 120 ticks. On each completed cycle, every nearby Catalyst Block has a
10% chance to convert one nearby stone block into iron ore.

## Interfaces

Right-click a controller to see its active status, connected-port count, and cycle progress.
Right-click a port to open its storage interface. Item ports show the recipe’s required item
or produced item at the top and the real storage slot below it. Fluid-configured ports instead
show a 16,000 mB gauge, the required/produced fluid, and the currently stored fluid type.

## KubeJS refinery registration

The mod does not require KubeJS, but KubeJS can call its public Java bridge during a
startup script. The fluent interface needs one import only; it delays a refinery until the
stated UTC instant, then runs twice each UTC day:

A fully commented, copy-ready script is included at
[`examples/kubejs/startup_scripts/terraindustry_refineries.js`](examples/kubejs/startup_scripts/terraindustry_refineries.js).
Copy it into a modpack's `kubejs/startup_scripts` directory and replace the example ids.

```js
// kubejs/startup_scripts/terraindustry_refineries.js
const RefineryDefinitions = Java.loadClass(
  'com.digitscodecompendium.terraindustry.refinery.RefineryDefinitions'
)

RefineryDefinitions.refinery('terraindustry:basic_crude')
  .startsAt('2026-09-01T00:00:00Z')
  .activeBetween('06:00-10:00')
  .activeBetween('18:30-23:00')
  .cycleTicks(20)
  .fuelItem('minecraft:coal', 1, 20)
  .transform('minecraft:stone', 'minecraft:iron_ore', 0.10)
  .crystallize('minecraft:iron_ore', 'minecraft:amethyst_cluster', 0.10)
  .register()
```

Omit `startsAt` for no delayed start. With no `activeBetween` calls, the refinery is active
all day; `00:00-00:00` also means all day. Windows such as `22:00-02:00` cross midnight.
Every `register()` call adds a distinct controller block and item, bound permanently to that
definition. The controller's block id is `terraindustry:<definition-path>`; use
`.controller('my_controller_path')` to choose a different path. This lets a pack register, for
example, both `yourmod:iron_refining` and `yourmod:copper_refining` without their conversion
steps being combined. Call
`transform` more than once with the same input to add weighted outputs. A refinery definition contains one or more catalyst-transformation recipes. At
the end of a fueled, scheduled cycle, each Catalyst Block finds one nearby matching input block
for each recipe and rolls the listed output chances. Fuel is an operating property of the
refinery definition, not a transformation recipe input.

Transformation inputs and outputs can include exact block states. For example,
`transform('terra:dense_byzantium_ore[rock=granite]', 'tfc:ore/rich_cassiterite/granite', 1.0)`
only converts the granite variant. State properties in an output are applied to the placed block.

Crystallization recipes select a matching block type and a crystal block. On a successful roll, a
crystallization recipe places its crystal on one random exposed face of the selected block. Vanilla
amethyst clusters are supported out of the box.

Fuel and Modifier Ports expose item storage; the Coolant Port exposes liquid storage. Modifier
and coolant effects are intentionally not processed yet, as their separate mechanics still need
their design pass.

## Effects block

The **Effects Block** is available in the Terra Industry creative tab. It uses the required
[Cascade](https://modrinth.com/mod/cascademc) library to continuously emit a layered sonic-pulse ring.
Only players with permission level 2
(operators/admins) can open its configuration screen. The screen accepts a final radius in blocks,
a fade exponent, the pulse travel time in seconds, and a six-digit hex color such as `#35D4FF`.
Settings are stored on each placed block and validated by the server.
