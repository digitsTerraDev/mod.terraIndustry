package com.digitscodecompendium.terraindustry;

import com.digitscodecompendium.terraindustry.refinery.BuiltinRefineries;
import com.digitscodecompendium.terraindustry.effects.EffectsBlock;
import com.digitscodecompendium.terraindustry.refinery.RefineryControllerBlock;
import com.digitscodecompendium.terraindustry.refinery.RefineryPortBlock;
import com.digitscodecompendium.terraindustry.refinery.RefineryPortType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;

/** All refinery pieces are intentionally unbreakable; only their controller owns behaviour. */
public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(TerraIndustry.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(TerraIndustry.MODID);

    public static final DeferredBlock<RefineryControllerBlock> IRON_REFINERY = registerController("iron_refinery", ResourceLocation.parse(BuiltinRefineries.IRON_REFINERY));
    private static final List<DeferredBlock<RefineryControllerBlock>> REFINERY_CONTROLLERS = new ArrayList<>(List.of(
            IRON_REFINERY));
    public static final DeferredBlock<RefineryPortBlock> FUEL_PORT = registerPort("fuel_port", RefineryPortType.FUEL);
    public static final DeferredBlock<RefineryPortBlock> MODIFIER_PORT = registerPort("modifier_port", RefineryPortType.MODIFIER);
    public static final DeferredBlock<RefineryPortBlock> COOLANT_PORT = registerPort("coolant_port", RefineryPortType.COOLANT);
    public static final DeferredBlock<Block> CATALYST_BLOCK = registerSimpleBlock("catalyst_block");
    public static final DeferredBlock<EffectsBlock> EFFECTS_BLOCK = registerEffectsBlock("effects_block");
    public static final DeferredItem<Item> ACCELERATION_MODIFIER =
            ITEMS.registerSimpleItem("acceleration_modifier", new Item.Properties().stacksTo(1));
    public static final DeferredItem<Item> SABOTAGE_MODIFIER =
            ITEMS.registerSimpleItem("sabotage_modifier", new Item.Properties().stacksTo(1));
    public static final DeferredItem<Item> CRYSTALLIZATION_MODIFIER =
            ITEMS.registerSimpleItem("crystallization_modifier", new Item.Properties().stacksTo(1));

    public static boolean isModifierItem(ItemStack stack) {
        return stack.is(ACCELERATION_MODIFIER.get())
                || stack.is(SABOTAGE_MODIFIER.get())
                || stack.is(CRYSTALLIZATION_MODIFIER.get());
    }

    private static DeferredBlock<RefineryControllerBlock> registerController(String name) {
        DeferredBlock<RefineryControllerBlock> block = BLOCKS.registerBlock(name, RefineryControllerBlock::new);
        ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
        return block;
    }

    private static DeferredBlock<RefineryControllerBlock> registerController(String name, ResourceLocation definitionId) {
        DeferredBlock<RefineryControllerBlock> block = BLOCKS.registerBlock(name, properties -> new RefineryControllerBlock(properties, definitionId));
        ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
        return block;
    }

    /**
     * Registers a pack-defined controller under the Terra Industry namespace. This must be called
     * from a KubeJS startup script, before block registries are frozen.
     */
    public static synchronized void registerRefineryController(String name, ResourceLocation definitionId) {
        if (!ResourceLocation.isValidPath(name)) {
            throw new IllegalArgumentException("Invalid refinery controller path: " + name);
        }
        if (REFINERY_CONTROLLERS.stream().anyMatch(controller -> controller.getId().getPath().equals(name))) {
            throw new IllegalArgumentException("A refinery controller named '" + name + "' is already registered");
        }
        REFINERY_CONTROLLERS.add(registerController(name, definitionId));
    }

    /** Blocks that may host a refinery controller block entity. */
    public static synchronized List<Block> refineryControllerBlocks() {
        return REFINERY_CONTROLLERS.stream().<Block>map(DeferredBlock::get).toList();
    }

    private static DeferredBlock<RefineryPortBlock> registerPort(String name, RefineryPortType type) {
        DeferredBlock<RefineryPortBlock> block = BLOCKS.registerBlock(name, properties -> new RefineryPortBlock(properties, type));
        ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
        return block;
    }

    private static DeferredBlock<Block> registerSimpleBlock(String name) {
        DeferredBlock<Block> block = BLOCKS.registerBlock(name, properties -> new Block(properties.strength(-1.0F, 3_600_000.0F)));
        ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
        return block;
    }

    private static DeferredBlock<EffectsBlock> registerEffectsBlock(String name) {
        DeferredBlock<EffectsBlock> block = BLOCKS.registerBlock(name, EffectsBlock::new);
        ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
        return block;
    }

    private ModBlocks() { }
}
