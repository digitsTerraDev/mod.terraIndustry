package com.digitscodecompendium.terraindustry.refinery;

import com.digitscodecompendium.terraindustry.ModBlockEntities;
import com.digitscodecompendium.terraindustry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The sole owner of a refinery. Discovery is deliberately chunk-scoped: no port in another
 * chunk can be linked, even when it is adjacent to this controller.
 */
public class RefineryControllerBlockEntity extends BlockEntity {
    private static final long DISCOVERY_INTERVAL_TICKS = 20;
    private static final int CATALYST_RANGE = 4;
    private static final double CRYSTALLIZATION_CONVERSION_MULTIPLIER = 0.5D;
    private static final double CRYSTALLIZATION_CHANCE_MULTIPLIER = 2.0D;
    private static final ResourceLocation LEGACY_BASIC_IRON_ID = ResourceLocation.fromNamespaceAndPath("terraindustry", "basic_iron");
    private static final ResourceLocation UNCONFIGURED_ID = ResourceLocation.fromNamespaceAndPath("terraindustry", "unconfigured");

    private ResourceLocation definitionId = UNCONFIGURED_ID;
    private List<RefineryPortBlockEntity> ports = List.of();
    private List<BlockPos> catalysts = List.of();
    /** Unique targets discovered from all catalyst ranges; rebuilt when the refinery refreshes. */
    private Map<BlockPos, CatalystTransformationRecipe> catalystTargets = Map.of();
    private Map<BlockPos, CatalystCrystallizationRecipe> crystallizationTargets = Map.of();
    private final EnumMap<RefineryPortType, Integer> roundRobinCursor = new EnumMap<>(RefineryPortType.class);
    private boolean active;
    private int progress;

    public RefineryControllerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.REFINERY_CONTROLLER.get(), pos, state);
        if (state.getBlock() instanceof RefineryControllerBlock controller && controller.definitionId() != null) {
            definitionId = controller.definitionId();
        }
    }

    public static void serverTick(ServerLevel level, BlockPos pos, BlockState state, RefineryControllerBlockEntity controller) {
        controller.updateDefinitionId();
        if (level.getGameTime() % DISCOVERY_INTERVAL_TICKS == 0) {
            controller.discoverComponents(level);
        }
        controller.ports(RefineryPortType.MODIFIER).forEach(RefineryPortBlockEntity::tickModifier);

        controller.active = RefineryDefinitions.find(controller.definitionId)
                .filter(definition -> definition.isScheduledNow(Instant.now()))
                .map(definition -> controller.runCycleTick(level, definition))
                .orElse(false);
    }

    private void updateDefinitionId() {
        if (definitionId.equals(LEGACY_BASIC_IRON_ID)) {
            setDefinitionId(ResourceLocation.parse(BuiltinRefineries.IRON_REFINERY));
            return;
        }
        if (definitionId.equals(UNCONFIGURED_ID)) {
            RefineryDefinitions.defaultId().ifPresent(this::setDefinitionId);
        }
    }

    private void discoverComponents(ServerLevel level) {
        LevelChunk chunk = level.getChunkAt(worldPosition);
        ports = discoverPorts(chunk);
        ports.forEach(port -> port.linkTo(worldPosition));
        catalysts = discoverCatalysts(level, chunk);
        rebuildCatalystTargets(level);
    }

    private List<RefineryPortBlockEntity> discoverPorts(LevelChunk chunk) {
        Collection<BlockEntity> blockEntities = chunk.getBlockEntities().values();
        return blockEntities.stream()
                .filter(RefineryPortBlockEntity.class::isInstance)
                .map(RefineryPortBlockEntity.class::cast)
                .sorted(Comparator.comparingLong(port -> port.getBlockPos().asLong()))
                .toList();
    }

    private void rebuildCatalystTargets(ServerLevel level) {
        RefineryDefinitions.find(definitionId).ifPresentOrElse(definition -> {
            catalystTargets = discoverCatalystTargets(level, definition.catalystRecipes());
            crystallizationTargets = discoverCrystallizationTargets(
                    level, definition.crystallizationRecipes(), catalystTargets.keySet());
        }, () -> {
            catalystTargets = Map.of();
            crystallizationTargets = Map.of();
        });
    }

    private List<BlockPos> discoverCatalysts(ServerLevel level, LevelChunk chunk) {
        List<BlockPos> found = new ArrayList<>();
        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = level.getMinBuildHeight(); y < level.getMaxBuildHeight(); y++) {
            for (int x = minX; x < minX + 16; x++) {
                for (int z = minZ; z < minZ + 16; z++) {
                    cursor.set(x, y, z);
                    if (level.getBlockState(cursor).is(ModBlocks.CATALYST_BLOCK.get())) {
                        found.add(cursor.immutable());
                    }
                }
            }
        }
        return List.copyOf(found);
    }

    private Map<BlockPos, CatalystTransformationRecipe> discoverCatalystTargets(ServerLevel level,
                                                                                  List<CatalystTransformationRecipe> recipes) {
        Map<BlockPos, CatalystTransformationRecipe> found = new LinkedHashMap<>();
        for (BlockPos catalyst : catalysts) {
            for (CatalystTransformationRecipe recipe : recipes) {
                for (BlockPos target : findNearbyInputs(level, catalyst, recipe.inputBlock())) {
                    // First discovered recipe owns the target for this cycle, preventing overlap double-ticks.
                    found.putIfAbsent(target, recipe);
                }
            }
        }
        return Map.copyOf(found);
    }

    private Map<BlockPos, CatalystCrystallizationRecipe> discoverCrystallizationTargets(ServerLevel level,
                                                                                          List<CatalystCrystallizationRecipe> recipes,
                                                                                          Set<BlockPos> reservedTargets) {
        Map<BlockPos, CatalystCrystallizationRecipe> found = new LinkedHashMap<>();
        for (BlockPos catalyst : catalysts) {
            for (CatalystCrystallizationRecipe recipe : recipes) {
                for (BlockPos target : findNearbyInputs(level, catalyst, recipe.inputBlock())) {
                    if (!reservedTargets.contains(target)) {
                        found.putIfAbsent(target, recipe);
                    }
                }
            }
        }
        return Map.copyOf(found);
    }

    public void setDefinitionId(ResourceLocation definitionId) {
        this.definitionId = definitionId;
        setChanged();
    }

    public boolean isActive() {
        return active;
    }

    public int progress() {
        return progress;
    }

    public int cycleTicks() {
        return RefineryDefinitions.find(definitionId).map(RefineryDefinition::cycleTicks).orElse(0);
    }
    public int activeHourMask() {
        return RefineryDefinitions.find(definitionId).map(definition -> {
            int mask = 0;
            for (int hour = 0; hour < 24; hour++) {
                LocalTime time = LocalTime.of(hour, 0);
                if (definition.activeWindows().stream().anyMatch(window -> window.contains(time))) mask |= 1 << hour;
            }
            return mask;
        }).orElse(0);
    }
    public List<RefineryPortBlockEntity> ports() {
        return ports;
    }

    /** Returns the resource accepted by ports of this role, or {@code null} when none is configured. */
    public @Nullable RefineryOperatingRate operatingRate(RefineryPortType portType) {
        return RefineryDefinitions.find(definitionId).map(definition -> switch (portType) {
            case FUEL -> definition.fuel();
            case COOLANT -> definition.coolant();
            case MODIFIER -> null;
        }).orElse(null);
    }

    public RefineryModifierType activeModifier() {
        if (modifierCount(RefineryModifierType.SABOTAGE) > 0) return RefineryModifierType.SABOTAGE;
        if (modifierCount(RefineryModifierType.ACCELERATION) > 0) return RefineryModifierType.ACCELERATION;
        if (modifierCount(RefineryModifierType.CRYSTALLIZATION) > 0) return RefineryModifierType.CRYSTALLIZATION;
        return RefineryModifierType.NONE;
    }

    public int modifierCount(RefineryModifierType type) {
        return (int) ports(RefineryPortType.MODIFIER).stream()
                .filter(port -> port.activeModifier() == type)
                .count();
    }

    public int modifierTicksRemaining(RefineryModifierType type) {
        return ports(RefineryPortType.MODIFIER).stream()
                .filter(port -> port.activeModifier() == type)
                .mapToInt(RefineryPortBlockEntity::activeModifierTicksRemaining)
                .max().orElse(0);
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("Definition", definitionId.toString());
        tag.putInt("Progress", progress);
    }

    @Override
    protected void loadAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.loadAdditional(tag, registries);
        ResourceLocation parsed = ResourceLocation.tryParse(tag.getString("Definition"));
        if (parsed != null) {
            definitionId = parsed;
        }
        progress = tag.getInt("Progress");
    }

    private boolean runCycleTick(ServerLevel level, RefineryDefinition definition) {
        int accelerationModifiers = modifierCount(RefineryModifierType.ACCELERATION);
        int workTicks = activeModifier() == RefineryModifierType.SABOTAGE
                ? 1 : 1 << Math.min(accelerationModifiers, 6);
        boolean worked = false;
        for (int tick = 0; tick < workTicks; tick++) {
            if (!runSingleCycleTick(level, definition)) break;
            worked = true;
        }
        return worked;
    }

    private boolean runSingleCycleTick(ServerLevel level, RefineryDefinition definition) {
        if (requiresFuelThisTick(definition)) {
            assert definition.fuel() != null;
            if (!has(definition.fuel().resource())) {
                progress = 0;
                return false;
            }
        }

        consumeFuelIfRequired(definition);
        progress++;

        if (progress >= definition.cycleTicks()) {
            if (activeModifier() == RefineryModifierType.SABOTAGE) {
                activateReverseCatalysts(level, definition);
            } else {
                activateCatalysts(level);
            }
            progress = 0;
        }
        setChanged();
        return true;
    }

    private boolean requiresFuelThisTick(RefineryDefinition definition) {
        return definition.fuel() != null && applies(definition.fuel());
    }

    private void consumeFuelIfRequired(RefineryDefinition definition) {
        if (requiresFuelThisTick(definition)) {
            assert definition.fuel() != null;
            extract(definition.fuel().resource());
        }
    }

    private void activateCatalysts(ServerLevel level) {
        int crystallizationModifiers = modifierCount(RefineryModifierType.CRYSTALLIZATION);
        double conversionMultiplier = Math.pow(CRYSTALLIZATION_CONVERSION_MULTIPLIER, crystallizationModifiers);
        double crystallizationMultiplier = Math.pow(CRYSTALLIZATION_CHANCE_MULTIPLIER, crystallizationModifiers);
        for (Map.Entry<BlockPos, CatalystTransformationRecipe> entry : catalystTargets.entrySet()) {
            transformTarget(level, entry.getKey(), entry.getValue(), conversionMultiplier);
        }
        for (Map.Entry<BlockPos, CatalystCrystallizationRecipe> entry : crystallizationTargets.entrySet()) {
            crystallizeTarget(level, entry.getKey(), entry.getValue(), crystallizationMultiplier);
        }
    }

    private void transformTarget(ServerLevel level, BlockPos target, CatalystTransformationRecipe recipe,
                                 double chanceMultiplier) {
        if (!matchesInput(level, target, recipe.inputBlock())) {
            return;
        }

        CatalystTransformationRecipe.Outcome outcome = chooseOutcome(recipe, level.random.nextDouble(), chanceMultiplier);
        if (outcome == null) {
            return;
        }

        BlockState previousState = level.getBlockState(target);
        playReplacementEffects(level, target, previousState);
        Block outputBlock = BuiltInRegistries.BLOCK.get(outcome.outputBlock());
        level.setBlock(target, outputBlock.defaultBlockState(), Block.UPDATE_ALL);
    }

    private CatalystTransformationRecipe.Outcome chooseOutcome(CatalystTransformationRecipe recipe, double roll,
                                                                 double chanceMultiplier) {
        for (CatalystTransformationRecipe.Outcome outcome : recipe.outputs()) {
            roll -= outcome.chance() * chanceMultiplier;
            if (roll <= 0.0D) {
                return outcome;
            }
        }
        return null;
    }

    private void crystallizeTarget(ServerLevel level, BlockPos target, CatalystCrystallizationRecipe recipe,
                                   double chanceMultiplier) {
        double chance = Math.min(1.0D, recipe.chance() * chanceMultiplier);
        if (matchesInput(level, target, recipe.inputBlock()) && level.random.nextDouble() <= chance) {
            spawnCrystal(level, target, recipe.crystalBlock());
        }
    }

    private void activateReverseCatalysts(ServerLevel level, RefineryDefinition definition) {
        Set<BlockPos> transformed = new java.util.HashSet<>();
        for (BlockPos catalyst : catalysts) {
            for (CatalystTransformationRecipe recipe : definition.catalystRecipes()) {
                for (CatalystTransformationRecipe.Outcome outcome : recipe.outputs()) {
                    for (BlockPos target : findNearbyInputs(level, catalyst, outcome.outputBlock())) {
                        if (transformed.add(target) && level.random.nextDouble() <= outcome.chance()) {
                            replaceBlock(level, target, BuiltInRegistries.BLOCK.get(recipe.inputBlock()));
                        }
                    }
                }
            }
        }
    }

    private void replaceBlock(ServerLevel level, BlockPos target, Block outputBlock) {
        BlockState previousState = level.getBlockState(target);
        playReplacementEffects(level, target, previousState);
        level.setBlock(target, outputBlock.defaultBlockState(), Block.UPDATE_ALL);
    }

    private boolean matchesInput(ServerLevel level, BlockPos target, ResourceLocation inputBlock) {
        Block block = level.getBlockState(target).getBlock();
        return BuiltInRegistries.BLOCK.getKey(block).equals(inputBlock);
    }

    private void playReplacementEffects(ServerLevel level, BlockPos target, BlockState previousState) {
        level.levelEvent(2001, target, Block.getId(previousState));
        level.playSound(null, target, previousState.getSoundType(level, target, null).getBreakSound(),
                SoundSource.BLOCKS, 1.0F, 1.0F);
    }

    private void spawnCrystal(ServerLevel level, BlockPos target, ResourceLocation crystalId) {
        List<Direction> exposedFaces = new ArrayList<>();
        for (Direction direction : Direction.values()) {
            BlockPos placement = target.relative(direction);
            if (isInControllerChunk(placement) && level.isEmptyBlock(placement)) {
                exposedFaces.add(direction);
            }
        }

        if (exposedFaces.isEmpty()) {
            return;
        }

        Direction face = exposedFaces.get(level.random.nextInt(exposedFaces.size()));
        BlockPos placement = target.relative(face);
        BlockState crystal = BuiltInRegistries.BLOCK.get(crystalId).defaultBlockState();
        if (crystal.hasProperty(BlockStateProperties.FACING)) {
            crystal = crystal.setValue(BlockStateProperties.FACING, face);
        }
        if (!crystal.canSurvive(level, placement)) {
            return;
        }
        level.setBlock(placement, crystal, Block.UPDATE_ALL);
    }

    private List<BlockPos> findNearbyInputs(ServerLevel level, BlockPos catalyst, ResourceLocation input) {
        List<BlockPos> matches = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(
                catalyst.offset(-CATALYST_RANGE, -CATALYST_RANGE, -CATALYST_RANGE),
                catalyst.offset(CATALYST_RANGE, CATALYST_RANGE, CATALYST_RANGE))) {
            if (isInControllerChunk(pos) && matchesInput(level, pos, input)) {
                matches.add(pos.immutable());
            }
        }
        return matches;
    }

    private boolean isInControllerChunk(BlockPos pos) {
        return (pos.getX() >> 4) == (worldPosition.getX() >> 4)
                && (pos.getZ() >> 4) == (worldPosition.getZ() >> 4);
    }

    private boolean applies(RefineryOperatingRate rate) {
        return progress % rate.intervalTicks() == 0;
    }

    private List<RefineryPortBlockEntity> ports(RefineryPortType role) {
        List<RefineryPortBlockEntity> matching = ports.stream().filter(port -> port.portType() == role).toList();
        if (matching.isEmpty()) {
            return matching;
        }

        int start = Math.floorMod(roundRobinCursor.getOrDefault(role, 0), matching.size());
        List<RefineryPortBlockEntity> ordered = new ArrayList<>(matching.size());
        for (int offset = 0; offset < matching.size(); offset++) {
            ordered.add(matching.get((start + offset) % matching.size()));
        }
        return ordered;
    }

    private void advancePort() {
        roundRobinCursor.merge(RefineryPortType.FUEL, 1, Integer::sum);
    }

    private boolean has(RefineryResource resource) {
        int available = 0;
        for (RefineryPortBlockEntity port : ports(RefineryPortType.FUEL)) {
            available += storedAmount(port, resource);
            if (available >= resource.amount()) {
                return true;
            }
        }
        return false;
    }

    private int storedAmount(RefineryPortBlockEntity port, RefineryResource resource) {
        return switch (resource.kind()) {
            case ITEM -> countItems(port, resource);
            case FLUID -> amountOfFluid(port, resource);
            case ENERGY -> storedEnergy(port);
        };
    }

    private int countItems(RefineryPortBlockEntity port, RefineryResource resource) {
        int count = 0;
        for (int slot = 0; slot < port.itemStorage().getSlots(); slot++) {
            ItemStack stack = port.itemStorage().getStackInSlot(slot);
            if (stack.is(BuiltInRegistries.ITEM.get(resource.id()))) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private int amountOfFluid(RefineryPortBlockEntity port, RefineryResource resource) {
        var tank = port.fluidStorage();
        if (tank == null || !BuiltInRegistries.FLUID.getKey(tank.getFluidInTank(0).getFluid()).equals(resource.id())) {
            return 0;
        }
        return tank.getFluidInTank(0).getAmount();
    }

    private int storedEnergy(RefineryPortBlockEntity port) {
        var storage = port.energyStorage();
        return storage == null ? 0 : storage.getEnergyStored();
    }

    private void extract(RefineryResource resource) {
        int remaining = resource.amount();
        boolean transferred = false;
        for (RefineryPortBlockEntity port : ports(RefineryPortType.FUEL)) {
            int used = extractFromPort(port, resource, remaining);
            remaining -= used;
            transferred |= used > 0;
            if (remaining == 0) {
                if (transferred) {
                    advancePort();
                }
                return;
            }
        }
        if (transferred) {
            advancePort();
        }
    }

    private int extractFromPort(RefineryPortBlockEntity port, RefineryResource resource, int amount) {
        return switch (resource.kind()) {
            case ITEM -> extractItems(port, resource, amount);
            case FLUID -> extractFluid(port, resource, amount);
            case ENERGY -> extractEnergy(port, amount);
        };
    }

    private int extractItems(RefineryPortBlockEntity port, RefineryResource resource, int amount) {
        int extracted = 0;
        for (int slot = 0; slot < port.itemStorage().getSlots() && extracted < amount; slot++) {
            ItemStack stack = port.itemStorage().getStackInSlot(slot);
            if (stack.is(BuiltInRegistries.ITEM.get(resource.id()))) {
                extracted += port.itemStorage().extractItem(slot, amount - extracted, false).getCount();
            }
        }
        return extracted;
    }

    private int extractFluid(RefineryPortBlockEntity port, RefineryResource resource, int amount) {
        var tank = port.fluidStorage();
        if (tank == null) {
            return 0;
        }
        FluidStack requested = new FluidStack(BuiltInRegistries.FLUID.get(resource.id()), amount);
        return tank.drain(requested, net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE).getAmount();
    }

    private int extractEnergy(RefineryPortBlockEntity port, int amount) {
        var storage = port.energyStorage();
        return storage == null ? 0 : storage.extractEnergy(amount, false);
    }

}
