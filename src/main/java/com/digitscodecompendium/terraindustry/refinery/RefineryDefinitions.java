package com.digitscodecompendium.terraindustry.refinery;

import com.digitscodecompendium.terraindustry.ModBlocks;
import net.minecraft.resources.ResourceLocation;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/** Public Java bridge intended for KubeJS startup scripts; no KubeJS dependency is required. */
public final class RefineryDefinitions {
    private static final Map<ResourceLocation, RefineryDefinition> DEFINITIONS = new ConcurrentHashMap<>();
    public static void register(RefineryDefinition definition) { DEFINITIONS.put(definition.id(), definition); }

    /**
     * Starts a KubeJS-friendly refinery definition. This is the preferred scripting API:
     * {@code RefineryDefinitions.refinery("example:iron").fuelItem("minecraft:coal", 1, 20).register()}.
     */
    public static Builder refinery(String id) { return new Builder(id); }

    /**
     * KubeJS-friendly registration method. {@code startsAt} is an ISO-8601 UTC instant or null;
     * each period is {@code HH:mm-HH:mm} UTC. Use {@code 00:00-00:00} for all day.
     */
    public static void register(String id, String startsAt, String[] periods) {
        ResourceLocation key = ResourceLocation.parse(id);
        Instant start = startsAt == null || startsAt.isBlank() ? null : Instant.parse(startsAt);
        var windows = Arrays.stream(periods).map(RefineryDefinitions::parsePeriod).toList();
        register(new RefineryDefinition(key, start, windows));
    }

    /** Registers a catalyst refinery and its independent operating streams. */
    public static void register(String id, String startsAt, String[] periods, int cycleTicks,
                                CatalystTransformationRecipe[] catalystRecipes,
                                RefineryOperatingRate fuel, RefineryOperatingRate coolant,
                                CatalystCrystallizationRecipe[] crystallizationRecipes) {
        ResourceLocation key = ResourceLocation.parse(id);
        Instant start = startsAt == null || startsAt.isBlank() ? null : Instant.parse(startsAt);
        var windows = Arrays.stream(periods).map(RefineryDefinitions::parsePeriod).toList();
        register(new RefineryDefinition(key, start, windows, cycleTicks, Arrays.asList(catalystRecipes), fuel, coolant,
                Arrays.asList(crystallizationRecipes)));
    }

    private static RefineryDefinition.DailyWindow parsePeriod(String period) {
        String[] parts = period.split("-", -1);
        if (parts.length != 2) throw new IllegalArgumentException("Invalid refinery UTC period: " + period);
        return new RefineryDefinition.DailyWindow(LocalTime.parse(parts[0]), LocalTime.parse(parts[1]));
    }
    public static Optional<RefineryDefinition> find(ResourceLocation id) { return Optional.ofNullable(DEFINITIONS.get(id)); }
    public static void clear() { DEFINITIONS.clear(); }
    private RefineryDefinitions() { }

    /**
     * Fluent configuration object designed to be called directly from KubeJS. It deliberately
     * uses strings and primitives so scripts do not need to construct Java arrays or records.
     */
    public static final class Builder {
        private final String id;
        private String startsAt;
        private String controllerName;
        private final List<String> periods = new ArrayList<>();
        private int cycleTicks = 20;
        private final Map<String, List<CatalystTransformationRecipe.Outcome>> transformations = new LinkedHashMap<>();
        private final List<CatalystCrystallizationRecipe> crystallizations = new ArrayList<>();
        private RefineryOperatingRate fuel;
        private RefineryOperatingRate coolant;

        private Builder(String id) {
            ResourceLocation.parse(id); // Fail at the script line that supplied the bad id.
            this.id = id;
        }

        /** Delays operation until this ISO-8601 UTC instant. Omit this for no delayed start. */
        public Builder startsAt(String startsAt) { this.startsAt = startsAt; return this; }

        /**
         * Uses this Terra Industry block path for the controller item. Without this call, the
         * definition id's path is used. For example, {@code controller("copper_refinery")} adds
         * {@code terraindustry:copper_refinery} bound only to this definition.
         */
        public Builder controller(String controllerName) {
            if (!ResourceLocation.isValidPath(controllerName)) {
                throw new IllegalArgumentException("Invalid refinery controller path: " + controllerName);
            }
            this.controllerName = controllerName;
            return this;
        }

        /** Adds an active UTC window such as {@code "06:00-10:00"}. */
        public Builder activeBetween(String period) { periods.add(period); return this; }

        /** Sets the duration of a refinery cycle in ticks. */
        public Builder cycleTicks(int cycleTicks) { this.cycleTicks = cycleTicks; return this; }

        /** Consumes an item from fuel ports every {@code intervalTicks}. */
        public Builder fuelItem(String item, int amount, int intervalTicks) {
            fuel = RefineryOperatingRate.everyTicks(RefineryResource.item(item, amount), intervalTicks);
            return this;
        }

        /** Consumes a fluid from fuel ports every {@code intervalTicks}. */
        public Builder fuelFluid(String fluid, int amount, int intervalTicks) {
            fuel = RefineryOperatingRate.everyTicks(RefineryResource.fluid(fluid, amount), intervalTicks);
            return this;
        }

        /** Consumes Forge energy from fuel ports every {@code intervalTicks}. */
        public Builder fuelEnergy(int amount, int intervalTicks) {
            fuel = RefineryOperatingRate.everyTicks(RefineryResource.energy(amount), intervalTicks);
            return this;
        }

        /** Configures an item coolant stream. Coolant behaviour is reserved for future mechanics. */
        public Builder coolantItem(String item, int amount, int intervalTicks) {
            coolant = RefineryOperatingRate.everyTicks(RefineryResource.item(item, amount), intervalTicks);
            return this;
        }

        /** Configures a fluid coolant stream. Coolant behaviour is reserved for future mechanics. */
        public Builder coolantFluid(String fluid, int amount, int intervalTicks) {
            coolant = RefineryOperatingRate.everyTicks(RefineryResource.fluid(fluid, amount), intervalTicks);
            return this;
        }

        /** Adds an outcome; outcomes for the same input share one weighted recipe. */
        public Builder transform(String inputBlock, String outputBlock, double chance) {
            transformations.computeIfAbsent(inputBlock, ignored -> new ArrayList<>())
                    .add(new CatalystTransformationRecipe.Outcome(outputBlock, chance));
            return this;
        }

        /** Attempts to place a crystal on an exposed face of a matching block each cycle. */
        public Builder crystallize(String inputBlock, String crystalBlock, double chance) {
            crystallizations.add(new CatalystCrystallizationRecipe(inputBlock, crystalBlock, chance));
            return this;
        }

        /** Registers this definition. With no active windows it runs all day. */
        public RefineryDefinition register() {
            String[] configuredPeriods = periods.isEmpty() ? new String[] { "00:00-00:00" } : periods.toArray(String[]::new);
            CatalystTransformationRecipe[] recipes = transformations.entrySet().stream()
                    .map(entry -> new CatalystTransformationRecipe(entry.getKey(),
                            entry.getValue().toArray(CatalystTransformationRecipe.Outcome[]::new)))
                    .toArray(CatalystTransformationRecipe[]::new);
            RefineryDefinition definition = new RefineryDefinition(ResourceLocation.parse(id),
                    startsAt == null || startsAt.isBlank() ? null : Instant.parse(startsAt),
                    Arrays.stream(configuredPeriods).map(RefineryDefinitions::parsePeriod).toList(), cycleTicks,
                    Arrays.asList(recipes), fuel, coolant, List.copyOf(crystallizations));
            RefineryDefinitions.register(definition);
            ModBlocks.registerRefineryController(controllerName == null
                    ? ResourceLocation.parse(id).getPath() : controllerName, definition.id());
            return definition;
        }

    }
}
