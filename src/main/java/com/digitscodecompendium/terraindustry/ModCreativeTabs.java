package com.digitscodecompendium.terraindustry;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** A single discoverable home for all player-placeable refinery components. */
public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, TerraIndustry.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TERRA_INDUSTRY =
            CREATIVE_MODE_TABS.register("terra_industry", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.terraindustry.terra_industry"))
                    .icon(() -> new ItemStack(ModBlocks.IRON_REFINERY.get()))
                    .displayItems((parameters, output) -> {
                        ModBlocks.refineryControllerBlocks().forEach(output::accept);
                        output.accept(ModBlocks.FUEL_PORT.get());
                        output.accept(ModBlocks.MODIFIER_PORT.get());
                        output.accept(ModBlocks.COOLANT_PORT.get());
                        output.accept(ModBlocks.CATALYST_BLOCK.get());
                        output.accept(ModBlocks.EFFECTS_BLOCK.get());
                        output.accept(ModBlocks.ACCELERATION_MODIFIER.get());
                        output.accept(ModBlocks.SABOTAGE_MODIFIER.get());
                        output.accept(ModBlocks.CRYSTALLIZATION_MODIFIER.get());
                    }).build());

    private ModCreativeTabs() { }
}
