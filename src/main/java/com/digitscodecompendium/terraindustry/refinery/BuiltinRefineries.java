package com.digitscodecompendium.terraindustry.refinery;

/** Starter content available without KubeJS. Pack scripts may replace the default definition. */
public final class BuiltinRefineries {
    public static final String IRON_REFINERY = "terraindustry:iron_refinery";

    public static void register() {
        // America/Toronto midnight–02:00 during EDT (UTC-4). Schedules themselves are UTC.
        RefineryDefinitions.register(IRON_REFINERY, null, new String[] { "00:00-23:59" }, 120,
                new CatalystTransformationRecipe[] {
                        new CatalystTransformationRecipe("minecraft:stone",
                                new CatalystTransformationRecipe.Outcome("minecraft:iron_ore", 0.10D)),

                        new CatalystTransformationRecipe("minecraft:iron_ore",
                                new CatalystTransformationRecipe.Outcome("minecraft:gold_ore", 0.05D)),

                        new CatalystTransformationRecipe("minecraft:gold_ore",
                                new CatalystTransformationRecipe.Outcome("minecraft:diamond_ore", 0.02D))
                },
                RefineryOperatingRate.everyTicks(RefineryResource.item("minecraft:coal", 1), 120), null,
                new CatalystCrystallizationRecipe[] {
                        new CatalystCrystallizationRecipe("minecraft:diamond_ore", "minecraft:amethyst_cluster", 0.10D)
                });
    }

    private BuiltinRefineries() { }
}
