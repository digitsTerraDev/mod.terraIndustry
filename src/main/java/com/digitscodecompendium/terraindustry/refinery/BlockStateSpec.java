package com.digitscodecompendium.terraindustry.refinery;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** A block id with optional exact state-property requirements, such as {@code mod:ore[rock=granite]}. */
public record BlockStateSpec(ResourceLocation blockId, Map<String, String> properties) {
    public BlockStateSpec {
        Objects.requireNonNull(blockId, "blockId");
        properties = Map.copyOf(properties);
    }

    public static BlockStateSpec parse(String value) {
        int stateStart = value.indexOf('[');
        if (stateStart < 0) return new BlockStateSpec(ResourceLocation.parse(value), Map.of());
        if (!value.endsWith("]") || stateStart == 0) {
            throw new IllegalArgumentException("Invalid block state: " + value);
        }
        Map<String, String> properties = new LinkedHashMap<>();
        String contents = value.substring(stateStart + 1, value.length() - 1);
        if (contents.isBlank()) throw new IllegalArgumentException("Block state has no properties: " + value);
        for (String assignment : contents.split(",")) {
            String[] parts = assignment.split("=", 2);
            if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()
                    || properties.putIfAbsent(parts[0], parts[1]) != null) {
                throw new IllegalArgumentException("Invalid block-state property in: " + value);
            }
        }
        return new BlockStateSpec(ResourceLocation.parse(value.substring(0, stateStart)), properties);
    }

    public boolean matches(BlockState state) {
        if (!BuiltInRegistries.BLOCK.getKey(state.getBlock()).equals(blockId)) return false;
        return properties.entrySet().stream().allMatch(entry -> matchesProperty(state, entry.getKey(), entry.getValue()));
    }

    /** Human-readable script syntax, used in JEI to distinguish state-specific variants. */
    public String asString() {
        if (properties.isEmpty()) return blockId.toString();
        return blockId + "[" + properties.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(java.util.stream.Collectors.joining(",")) + "]";
    }

    /** Creates the configured state, failing clearly if a script names an unavailable property or value. */
    public BlockState createState() {
        BlockState state = BuiltInRegistries.BLOCK.get(blockId).defaultBlockState();
        if (state.isAir() && !blockId.equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()))) {
            throw new IllegalArgumentException("Unknown block in refinery state: " + blockId);
        }
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            Property<?> property = state.getBlock().getStateDefinition().getProperty(entry.getKey());
            if (property == null) {
                throw new IllegalArgumentException("Block " + blockId + " has no '" + entry.getKey() + "' property");
            }
            state = setProperty(state, property, entry.getValue(), blockId);
        }
        return state;
    }

    private static boolean matchesProperty(BlockState state, String name, String expected) {
        Property<?> property = state.getBlock().getStateDefinition().getProperty(name);
        return property != null && propertyName(state, property).equals(expected);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static String propertyName(BlockState state, Property<?> property) {
        return ((Property) property).getName((Comparable) state.getValue((Property) property));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static BlockState setProperty(BlockState state, Property<?> property, String value, ResourceLocation blockId) {
        Object candidate = ((Property) property).getValue(value).orElse(null);
        if (!(candidate instanceof Comparable parsed)) {
            throw new IllegalArgumentException("Invalid value '" + value + "' for "
                    + blockId + " property '" + property.getName() + "'");
        }
        return state.setValue((Property) property, parsed);
    }
}
