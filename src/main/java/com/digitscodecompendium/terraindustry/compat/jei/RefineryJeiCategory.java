package com.digitscodecompendium.terraindustry.compat.jei;

import com.digitscodecompendium.terraindustry.ModBlocks;
import com.digitscodecompendium.terraindustry.TerraIndustry;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.drawable.IDrawableStatic;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

final class RefineryJeiCategory implements IRecipeCategory<RefineryJeiRecipe> {
    static final RecipeType<RefineryJeiRecipe> TYPE = new RecipeType<>(
            ResourceLocation.fromNamespaceAndPath(TerraIndustry.MODID, "refining"), RefineryJeiRecipe.class);
    private final IDrawable icon;
    private final IDrawableStatic arrow;

    RefineryJeiCategory(IGuiHelper gui) {
        icon = gui.createDrawableItemStack(new ItemStack(ModBlocks.CATALYST_BLOCK.get()));
        arrow = gui.getRecipeArrow();
    }

    @Override public RecipeType<RefineryJeiRecipe> getRecipeType() { return TYPE; }
    @Override public Component getTitle() { return Component.translatable("jei.terraindustry.refining"); }
    @Override public IDrawable getIcon() { return icon; }
    @Override public int getWidth() { return 160; }
    @Override public int getHeight() { return 58; }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, RefineryJeiRecipe recipe, mezz.jei.api.recipe.IFocusGroup focuses) {
        builder.addInputSlot(10, 28).addItemStack(new ItemStack(recipe.input().createState().getBlock()))
                .addRichTooltipCallback((view, tooltip) -> tooltip.add(Component.literal(recipe.input().asString())));
        builder.addOutputSlot(132, 28).addItemStack(new ItemStack(recipe.output().createState().getBlock()))
                .addRichTooltipCallback((view, tooltip) -> tooltip.add(Component.literal(recipe.output().asString())));
    }

    @Override
    public void draw(RefineryJeiRecipe recipe, mezz.jei.api.gui.ingredient.IRecipeSlotsView slots,
                     GuiGraphics graphics, double mouseX, double mouseY) {
        arrow.draw(graphics, 68, 29);
        graphics.drawString(Minecraft.getInstance().font,
                Component.translatable("jei.terraindustry.refinery", recipe.refineryId().toString()), 2, 2, 0x404040, false);
        graphics.drawString(Minecraft.getInstance().font,
                Component.translatable("jei.terraindustry.chance", String.format(java.util.Locale.ROOT, "%.1f%%", recipe.chance() * 100)),
                2, 14, 0x404040, false);
    }
}
