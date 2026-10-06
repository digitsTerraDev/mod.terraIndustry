package com.digitscodecompendium.terraindustry.compat.jei;

import com.digitscodecompendium.terraindustry.ModBlocks;
import com.digitscodecompendium.terraindustry.TerraIndustry;
import com.digitscodecompendium.terraindustry.refinery.RefineryDefinitions;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

import java.util.List;

/** Optional JEI bridge. It is loaded only when JEI discovers this plugin. */
@JeiPlugin
public final class TerraIndustryJeiPlugin implements IModPlugin {
    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(TerraIndustry.MODID, "jei_plugin");

    @Override public ResourceLocation getPluginUid() { return UID; }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new RefineryJeiCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        List<RefineryJeiRecipe> recipes = RefineryDefinitions.all().stream()
                .flatMap(definition -> definition.catalystRecipes().stream().flatMap(transformation ->
                        transformation.outputs().stream().map(outcome -> new RefineryJeiRecipe(
                                definition.id(), transformation.inputBlock(), outcome.outputBlock(), outcome.chance()))))
                .toList();
        registration.addRecipes(RefineryJeiCategory.TYPE, recipes);
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalysts(RefineryJeiCategory.TYPE,
                ModBlocks.refineryControllerBlocks().toArray(Block[]::new));
    }
}
