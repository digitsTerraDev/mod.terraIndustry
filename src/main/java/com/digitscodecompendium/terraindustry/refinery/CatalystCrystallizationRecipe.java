package com.digitscodecompendium.terraindustry.refinery;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** Places a crystal on one exposed face of a matching block when a catalyst cycle completes. */
public record CatalystCrystallizationRecipe(BlockStateSpec inputBlock, ResourceLocation crystalBlock, double chance) {
    public CatalystCrystallizationRecipe {
        Objects.requireNonNull(inputBlock, "inputBlock");
        Objects.requireNonNull(crystalBlock, "crystalBlock");
        if (chance <= 0.0D || chance > 1.0D) throw new IllegalArgumentException("Chance must be in (0, 1]");
    }
    public CatalystCrystallizationRecipe(String inputBlock, String crystalBlock, double chance) {
        this(BlockStateSpec.parse(inputBlock), ResourceLocation.parse(crystalBlock), chance);
    }
}
