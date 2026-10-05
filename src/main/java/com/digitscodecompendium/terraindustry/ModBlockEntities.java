package com.digitscodecompendium.terraindustry;

import com.digitscodecompendium.terraindustry.refinery.RefineryControllerBlockEntity;
import com.digitscodecompendium.terraindustry.refinery.RefineryPortBlockEntity;
import com.digitscodecompendium.terraindustry.effects.EffectsBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, TerraIndustry.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RefineryControllerBlockEntity>> REFINERY_CONTROLLER =
            BLOCK_ENTITIES.register("refinery_controller", () -> BlockEntityType.Builder.of(
                    RefineryControllerBlockEntity::new,
                    ModBlocks.refineryControllerBlocks().toArray(net.minecraft.world.level.block.Block[]::new)).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RefineryPortBlockEntity>> REFINERY_PORT =
            BLOCK_ENTITIES.register("refinery_port", () -> BlockEntityType.Builder.of(RefineryPortBlockEntity::new,
                    ModBlocks.FUEL_PORT.get(), ModBlocks.MODIFIER_PORT.get(), ModBlocks.COOLANT_PORT.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<EffectsBlockEntity>> EFFECTS_BLOCK =
            BLOCK_ENTITIES.register("effects_block", () -> BlockEntityType.Builder.of(
                    EffectsBlockEntity::new, ModBlocks.EFFECTS_BLOCK.get()).build(null));

    private ModBlockEntities() { }
}
