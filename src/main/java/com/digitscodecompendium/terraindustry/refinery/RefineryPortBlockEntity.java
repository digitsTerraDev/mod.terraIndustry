package com.digitscodecompendium.terraindustry.refinery;

import com.digitscodecompendium.terraindustry.ModBlockEntities;
import com.digitscodecompendium.terraindustry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Objects;

/** A passive port. The controller grants it an owner only while it shares that owner's chunk. */
public class RefineryPortBlockEntity extends BlockEntity {
    public static final int FLUID_CAPACITY = 16_000;
    public static final int ENERGY_CAPACITY = 100_000;
    public static final int TIMED_MODIFIER_DURATION_TICKS = 10 * 60 * 20;
    private @Nullable BlockPos controllerPos;
    private final ItemStackHandler items;
    private int modifierActivationProgress;
    private RefineryModifierType activeModifier = RefineryModifierType.NONE;
    private int activeModifierTicksRemaining;
    private final FluidTank fluid = new FluidTank(FLUID_CAPACITY) {
        @Override public boolean isFluidValid(net.neoforged.neoforge.fluids.FluidStack stack) {
            RefineryResource expected = expectedResource();
            return expected != null && expected.kind() == RefineryResource.Kind.FLUID
                    && stack.getFluid() == BuiltInRegistries.FLUID.get(expected.id());
        }

        @Override protected void onContentsChanged() { refineryContentsChanged(); }
    };
    private final EnergyStorage energy = new EnergyStorage(ENERGY_CAPACITY, ENERGY_CAPACITY, ENERGY_CAPACITY) {
        @Override public int receiveEnergy(int amount, boolean simulate) {
            int accepted = super.receiveEnergy(amount, simulate);
            if (accepted > 0 && !simulate) refineryContentsChanged();
            return accepted;
        }
        @Override public int extractEnergy(int amount, boolean simulate) {
            int extracted = super.extractEnergy(amount, simulate);
            if (extracted > 0 && !simulate) refineryContentsChanged();
            return extracted;
        }
    };

    public RefineryPortBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.REFINERY_PORT.get(), pos, state);
        int slots = portType() == RefineryPortType.MODIFIER ? 1 : 9;
        items = new ItemStackHandler(slots) {
            @Override protected void onContentsChanged(int slot) {
                if (portType() == RefineryPortType.MODIFIER) {
                    modifierActivationProgress = 0;
                }
                refineryContentsChanged();
            }

            @Override public boolean isItemValid(int slot, ItemStack stack) {
                if (portType() == RefineryPortType.MODIFIER) {
                    return activeModifier == RefineryModifierType.NONE && ModBlocks.isModifierItem(stack);
                }
                RefineryResource expected = expectedResource();
                return expected != null && expected.kind() == RefineryResource.Kind.ITEM
                        && stack.is(BuiltInRegistries.ITEM.get(expected.id()));
            }

            @Override public void deserializeNBT(HolderLookup.Provider registries, CompoundTag tag) {
                super.deserializeNBT(registries, tag);
                if (stacks.size() != slots) {
                    var previous = new ArrayList<>(stacks);
                    stacks = NonNullList.withSize(slots, ItemStack.EMPTY);
                    for (int slot = 0; slot < Math.min(previous.size(), slots); slot++) {
                        ItemStack stack = previous.get(slot);
                        if (isItemValid(slot, stack)) stacks.set(slot, stack);
                    }
                }
                // Resource filters are controller-dependent and may be unavailable while chunks load.
                // Keep previously stored items so a definition change never destroys player inventory.
            }
        };
    }

    public @Nullable BlockPos controllerPos() { return controllerPos; }
    public RefineryPortType portType() { return ((RefineryPortBlock) getBlockState().getBlock()).portType(); }
    public IItemHandler itemStorage() { return items; }
    public @Nullable IFluidHandler fluidStorage() { return portType() == RefineryPortType.MODIFIER ? null : fluid; }
    public @Nullable IEnergyStorage energyStorage() {
        return switch (portType()) {
            case FUEL -> energy;
            default -> null;
        };
    }

    public @Nullable RefineryOperatingRate operatingRate() {
        if (level == null || controllerPos == null
                || !(level.getBlockEntity(controllerPos) instanceof RefineryControllerBlockEntity controller)) {
            return null;
        }
        return controller.operatingRate(portType());
    }

    public @Nullable RefineryResource expectedResource() {
        RefineryOperatingRate rate = operatingRate();
        return rate == null ? null : rate.resource();
    }

    /** Whether this port needs visible item slots for its current controller configuration. */
    public boolean hasItemInventory() {
        return portType() == RefineryPortType.MODIFIER
                || expectedResource() != null && expectedResource().kind() == RefineryResource.Kind.ITEM;
    }

    public RefineryModifierType activeModifier() { return activeModifier; }
    public int modifierActivationProgress() { return modifierActivationProgress; }
    public int modifierActivationTicks() { return modifierType(items.getStackInSlot(0)).activationTicks(); }
    public int activeModifierTicksRemaining() { return activeModifierTicksRemaining; }

    void tickModifier() {
        if (portType() != RefineryPortType.MODIFIER) return;

        if (activeModifier != RefineryModifierType.NONE) {
            if (activeModifierTicksRemaining > 0) activeModifierTicksRemaining--;
            if (activeModifierTicksRemaining <= 0) activeModifier = RefineryModifierType.NONE;
            setChanged();
            return;
        }

        ItemStack stack = items.getStackInSlot(0);
        RefineryModifierType pending = modifierType(stack);
        if (pending == RefineryModifierType.NONE) {
            if (modifierActivationProgress != 0) {
                modifierActivationProgress = 0;
                setChanged();
            }
            return;
        }

        modifierActivationProgress++;
        if (modifierActivationProgress < pending.activationTicks()) {
            setChanged();
            return;
        }

        modifierActivationProgress = 0;
        items.extractItem(0, 1, false);
        activeModifier = pending;
        activeModifierTicksRemaining = TIMED_MODIFIER_DURATION_TICKS;
        setChanged();
    }

    public ItemStack forceInstallSabotage() {
        ItemStack displaced = items.extractItem(0, items.getStackInSlot(0).getCount(), false);
        activeModifier = RefineryModifierType.NONE;
        activeModifierTicksRemaining = 0;
        modifierActivationProgress = 0;
        items.insertItem(0, new ItemStack(ModBlocks.SABOTAGE_MODIFIER.get()), false);
        setChanged();
        return displaced;
    }

    private RefineryModifierType modifierType(ItemStack stack) {
        if (stack.is(ModBlocks.ACCELERATION_MODIFIER.get())) return RefineryModifierType.ACCELERATION;
        if (stack.is(ModBlocks.SABOTAGE_MODIFIER.get())) return RefineryModifierType.SABOTAGE;
        if (stack.is(ModBlocks.CRYSTALLIZATION_MODIFIER.get())) return RefineryModifierType.CRYSTALLIZATION;
        return RefineryModifierType.NONE;
    }

    void linkTo(@Nullable BlockPos controllerPos) {
        if (!Objects.equals(this.controllerPos, controllerPos)) {
            this.controllerPos = controllerPos;
            setChanged();
        }
    }

    private void refineryContentsChanged() { setChanged(); }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (controllerPos != null) tag.putLong("Controller", controllerPos.asLong());
        tag.put("Items", items.serializeNBT(registries));
        tag.put("Fluid", fluid.writeToNBT(registries, new CompoundTag()));
        tag.put("Energy", energy.serializeNBT(registries));
        tag.putInt("ModifierActivation", modifierActivationProgress);
        tag.putInt("ActiveModifier", activeModifier.ordinal());
        tag.putInt("ActiveModifierTicks", activeModifierTicksRemaining);
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        controllerPos = tag.contains("Controller") ? BlockPos.of(tag.getLong("Controller")) : null;
        if (tag.contains("Items")) items.deserializeNBT(registries, tag.getCompound("Items"));
        if (tag.contains("Fluid")) fluid.readFromNBT(registries, tag.getCompound("Fluid"));
        if (tag.contains("Energy")) energy.deserializeNBT(registries, tag.get("Energy"));
        modifierActivationProgress = tag.getInt("ModifierActivation");
        int modifierOrdinal = tag.getInt("ActiveModifier");
        RefineryModifierType[] modifierTypes = RefineryModifierType.values();
        activeModifier = modifierOrdinal >= 0 && modifierOrdinal < modifierTypes.length
                ? modifierTypes[modifierOrdinal] : RefineryModifierType.NONE;
        activeModifierTicksRemaining = tag.getInt("ActiveModifierTicks");
    }
}
