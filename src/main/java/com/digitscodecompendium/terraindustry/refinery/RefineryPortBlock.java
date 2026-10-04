package com.digitscodecompendium.terraindustry.refinery;

import com.digitscodecompendium.terraindustry.ModBlocks;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RefineryPortBlock extends BaseEntityBlock {
    private final RefineryPortType portType;
    public RefineryPortBlock(Properties properties, RefineryPortType portType) {
        super(properties.strength(-1.0F, 3_600_000.0F));
        this.portType = portType;
    }

    public RefineryPortType portType() {
        return portType;
    }

    @Override
    public @NotNull RenderShape getRenderShape(@NotNull BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected @NotNull MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(properties -> new RefineryPortBlock(properties, RefineryPortType.FUEL));
    }

    @Override
    public float getDestroyProgress(@NotNull BlockState state, @NotNull Player player,
                                    @NotNull BlockGetter level, @NotNull BlockPos pos) {
        return -1.0F;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new RefineryPortBlockEntity(pos, state);
    }

    @Override
    protected @NotNull ItemInteractionResult useItemOn(@NotNull ItemStack stack, @NotNull BlockState state,
                                                       Level level, @NotNull BlockPos pos, @NotNull Player player,
                                                       @NotNull InteractionHand hand, @NotNull BlockHitResult hit) {
        if (portType != RefineryPortType.MODIFIER || !stack.is(ModBlocks.SABOTAGE_MODIFIER.get())) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof RefineryPortBlockEntity port) {
            ItemStack displaced = port.forceInstallSabotage();
            if (!displaced.isEmpty() && !player.getInventory().add(displaced)) player.drop(displaced, false);
            if (!player.hasInfiniteMaterials()) stack.shrink(1);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected @NotNull InteractionResult useWithoutItem(@NotNull BlockState state, Level level, @NotNull BlockPos pos,
                                                        @NotNull Player player, @NotNull BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof RefineryPortBlockEntity port) {
            serverPlayer.openMenu(new SimpleMenuProvider(
                    (id, inventory, ignored) -> new RefineryPortMenu(id, inventory, port, portType),
                    getName()),
                    buffer -> {
                        buffer.writeBlockPos(pos);
                        buffer.writeVarInt(portType.ordinal());
                        buffer.writeBoolean(port.hasItemInventory());
                        var rate = port.operatingRate();
                        buffer.writeBoolean(rate != null);
                        if (rate != null) {
                            var resource = rate.resource();
                            buffer.writeVarInt(resource.kind().ordinal());
                            buffer.writeUtf(resource.id() == null ? "" : resource.id().toString());
                            buffer.writeVarInt(resource.amount());
                            buffer.writeVarInt(rate.intervalTicks());
                        }
                    });
        }
        return InteractionResult.SUCCESS;
    }
}
