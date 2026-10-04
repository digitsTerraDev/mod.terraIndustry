package com.digitscodecompendium.terraindustry.refinery;

import com.digitscodecompendium.terraindustry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jetbrains.annotations.Nullable;

/** Server-backed storage menu; role and location are sent when opening it. */
public class RefineryPortMenu extends AbstractContainerMenu {
    private final @Nullable RefineryPortBlockEntity port;
    private final RefineryPortType type;
    private int fluidAmount;
    private int fluidTypeId;
    private int modifierActivationProgress;
    private int modifierActivationTicks;
    private int activeModifier;
    private int activeModifierTicks;
    private int portSlotCount;
    private final @Nullable RefineryOperatingRate configuredRate;

    public RefineryPortMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory, readOpeningData(inventory.player, buffer));
    }

    private RefineryPortMenu(int id, Inventory inventory, OpeningData openingData) {
        this(id, inventory, openingData.port(), openingData.type(), openingData.hasItemInventory(), openingData.rate());
    }

    public RefineryPortMenu(int id, Inventory inventory, @Nullable RefineryPortBlockEntity port, RefineryPortType type) {
        this(id, inventory, port, type, port != null && port.hasItemInventory(), port == null ? null : port.operatingRate());
    }

    private RefineryPortMenu(int id, Inventory inventory, @Nullable RefineryPortBlockEntity port, RefineryPortType type,
                             boolean hasItemInventory, @Nullable RefineryOperatingRate configuredRate) {
        super(ModMenus.REFINERY_PORT.get(), id);
        this.port = port;
        this.type = type;
        this.configuredRate = configuredRate;
        if (port != null && hasItemInventory) {
            addPortSlots(port);
        }
        addFluidDataSlots();
        addModifierDataSlots();
        addPlayerInventory(inventory);
    }

    private void addPortSlots(RefineryPortBlockEntity port) {
        if (type == RefineryPortType.MODIFIER) {
            addSlot(new SlotItemHandler(port.itemStorage(), 0, 80, 58) {
                @Override public boolean mayPlace(net.minecraft.world.item.ItemStack stack) {
                    return super.mayPlace(stack);
                }
            });
            portSlotCount = 1;
            return;
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                int slotIndex = column + row * 3;
                addSlot(new SlotItemHandler(port.itemStorage(), slotIndex, 62 + column * 18, 58 + row * 18) {
                    @Override
                    public boolean mayPlace(net.minecraft.world.item.ItemStack stack) {
                        return super.mayPlace(stack);
                    }
                });
            }
        }
        portSlotCount = 9;
    }

    private void addFluidDataSlots() {
        addDataSlot(new DataSlot() {
            @Override
            public int get() {
                var storage = port == null ? null : port.fluidStorage();
                return storage == null ? 0 : storage.getFluidInTank(0).getAmount();
            }

            @Override
            public void set(int value) {
                fluidAmount = value;
            }
        });
        addDataSlot(new DataSlot() {
            @Override
            public int get() {
                var storage = port == null ? null : port.fluidStorage();
                return storage == null ? 0 : BuiltInRegistries.FLUID.getId(storage.getFluidInTank(0).getFluid());
            }

            @Override
            public void set(int value) {
                fluidTypeId = value;
            }
        });
    }

    private void addModifierDataSlots() {
        addDataSlot(new DataSlot() {
            @Override public int get() { return port == null ? 0 : port.modifierActivationProgress(); }
            @Override public void set(int value) { modifierActivationProgress = value; }
        });
        addDataSlot(new DataSlot() {
            @Override public int get() { return port == null ? 0 : port.modifierActivationTicks(); }
            @Override public void set(int value) { modifierActivationTicks = value; }
        });
        addDataSlot(new DataSlot() {
            @Override public int get() { return port == null ? 0 : port.activeModifier().ordinal(); }
            @Override public void set(int value) { activeModifier = value; }
        });
        addDataSlot(new DataSlot() {
            @Override public int get() { return port == null ? 0 : port.activeModifierTicksRemaining(); }
            @Override public void set(int value) { activeModifierTicks = value; }
        });
    }

    private static @Nullable RefineryPortBlockEntity findPort(Player player, BlockPos pos) {
        return player.level().getBlockEntity(pos) instanceof RefineryPortBlockEntity port ? port : null;
    }

    public RefineryPortType portType() {
        return type;
    }

    public @Nullable RefineryPortBlockEntity port() {
        return port;
    }

    public int fluidAmount() {
        return fluidAmount;
    }

    public int fluidTypeId() {
        return fluidTypeId;
    }

    public int modifierActivationProgress() { return modifierActivationProgress; }
    public int modifierActivationTicks() { return modifierActivationTicks; }
    public int activeModifierTicks() { return activeModifierTicks; }
    public RefineryModifierType activeModifier() {
        RefineryModifierType[] values = RefineryModifierType.values();
        return activeModifier >= 0 && activeModifier < values.length
                ? values[activeModifier] : RefineryModifierType.NONE;
    }

    public @Nullable RefineryOperatingRate operatingRate() {
        return configuredRate;
    }

    public @Nullable RefineryResource expectedResource() {
        RefineryOperatingRate rate = operatingRate();
        return rate == null ? null : rate.resource();
    }

    private static OpeningData readOpeningData(Player player, RegistryFriendlyByteBuf buffer) {
        RefineryPortBlockEntity port = findPort(player, buffer.readBlockPos());
        RefineryPortType type = RefineryPortType.values()[buffer.readVarInt()];
        boolean hasItemInventory = buffer.readBoolean();
        RefineryOperatingRate rate = null;
        if (buffer.readBoolean()) {
            RefineryResource.Kind kind = RefineryResource.Kind.values()[buffer.readVarInt()];
            String id = buffer.readUtf();
            int amount = buffer.readVarInt();
            int intervalTicks = buffer.readVarInt();
            RefineryResource resource = kind == RefineryResource.Kind.ENERGY
                    ? RefineryResource.energy(amount)
                    : new RefineryResource(kind, net.minecraft.resources.ResourceLocation.parse(id), amount);
            rate = RefineryOperatingRate.everyTicks(resource, intervalTicks);
        }
        return new OpeningData(port, type, hasItemInventory, rate);
    }

    private record OpeningData(@Nullable RefineryPortBlockEntity port, RefineryPortType type, boolean hasItemInventory,
                               @Nullable RefineryOperatingRate rate) { }

    private void addPlayerInventory(Inventory inventory) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new net.minecraft.world.inventory.Slot(inventory, column + row * 9 + 9,
                        8 + column * 18, 140 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new net.minecraft.world.inventory.Slot(inventory, column, 8 + column * 18, 198));
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return port != null && player.distanceToSqr(port.getBlockPos().getCenter()) <= 64.0D;
    }

    @Override
    public net.minecraft.world.item.ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) {
            return net.minecraft.world.item.ItemStack.EMPTY;
        }

        net.minecraft.world.inventory.Slot source = slots.get(index);
        if (!source.hasItem()) {
            return net.minecraft.world.item.ItemStack.EMPTY;
        }

        net.minecraft.world.item.ItemStack stack = source.getItem();
        net.minecraft.world.item.ItemStack original = stack.copy();
        if (index < portSlotCount) {
            if (!moveItemStackTo(stack, portSlotCount, slots.size(), true)) {
                return net.minecraft.world.item.ItemStack.EMPTY;
            }
        } else if (portSlotCount == 0 || !moveItemStackTo(stack, 0, portSlotCount, false)) {
            return net.minecraft.world.item.ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            source.set(net.minecraft.world.item.ItemStack.EMPTY);
        } else {
            source.setChanged();
        }
        return original;
    }
}
