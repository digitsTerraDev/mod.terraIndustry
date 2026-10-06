package com.digitscodecompendium.terraindustry.compat.jei;

import com.digitscodecompendium.terraindustry.refinery.BlockStateSpec;
import net.minecraft.resources.ResourceLocation;

/** One JEI hint for a possible catalyst conversion outcome. */
record RefineryJeiRecipe(ResourceLocation refineryId, BlockStateSpec input, BlockStateSpec output, double chance) { }
