package at.petrak.hexcasting.fabric.xplat;

import at.petrak.hexcasting.api.addldata.DataHolder;
import at.petrak.hexcasting.api.addldata.HexHolder;
import at.petrak.hexcasting.api.addldata.ManaHolder;
import at.petrak.hexcasting.api.misc.FrozenColorizer;
import at.petrak.hexcasting.api.misc.GravitySetting;
import at.petrak.hexcasting.api.mod.HexConfig;
import at.petrak.hexcasting.api.mod.HexItemTags;
import at.petrak.hexcasting.api.player.FlightAbility;
import at.petrak.hexcasting.api.player.Sentinel;
import at.petrak.hexcasting.api.spell.casting.CastingHarness;
import at.petrak.hexcasting.api.spell.casting.ResolvedPattern;
import at.petrak.hexcasting.common.lib.HexItems;
import at.petrak.hexcasting.common.network.IMessage;
import at.petrak.hexcasting.fabric.cc.HexCardinalComponents;
import at.petrak.hexcasting.fabric.interop.gravity.GravityApiInterop;
import at.petrak.hexcasting.fabric.recipe.FabricUnsealedIngredient;
import at.petrak.hexcasting.interop.HexInterop;
import at.petrak.hexcasting.interop.pehkui.PehkuiInterop;
import at.petrak.hexcasting.mixin.accessor.AccessorVillager;
import at.petrak.hexcasting.xplat.IXplatAbstractions;
import at.petrak.hexcasting.xplat.IXplatTags;
import at.petrak.hexcasting.xplat.Platform;
import com.jamieswhiteshirt.reachentityattributes.ReachEntityAttributes;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.client.itemgroup.FabricItemGroupBuilder;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageUtil;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.advancements.critereon.ItemPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.storage.loot.predicates.AlternativeLootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.MatchTool;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import virtuoel.pehkui.api.ScaleTypes;

import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import static at.petrak.hexcasting.api.HexAPI.modLoc;

public class FabricXplatImpl implements IXplatAbstractions {
    @Override
    public Platform platform() {
        return Platform.FABRIC;
    }

    @Override
    public boolean isPhysicalClient() {
        return FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT;
    }

    @Override
    public boolean isModPresent(String id) {
        return FabricLoader.getInstance().isModLoaded(id);
    }

    @Override
    public void initPlatformSpecific() {
        if (this.isModPresent(HexInterop.Fabric.GRAVITY_CHANGER_API_ID)) {
            GravityApiInterop.init();
        }
    }

    @Override
    public double getReachDistance(Player player) {
        return ReachEntityAttributes.getReachDistance(player, 5.0);
    }

    @Override
    public void sendPacketToPlayer(ServerPlayer target, IMessage packet) {
        ServerPlayNetworking.send(target, packet.getFabricId(), packet.toBuf());
    }

    @Override
    public void sendPacketNear(Vec3 pos, double radius, ServerLevel dimension, IMessage packet) {
        var pkt = ServerPlayNetworking.createS2CPacket(packet.getFabricId(), packet.toBuf());
        var nears = PlayerLookup.around(dimension, pos, radius);
        for (var p : nears) {
            p.connection.send(pkt);
        }
    }

    @Override
    public Packet<?> toVanillaClientboundPacket(IMessage message) {
        return ServerPlayNetworking.createS2CPacket(message.getFabricId(), message.toBuf());
    }

    @Override
    public void brainsweep(Mob mob) {
        var cc = HexCardinalComponents.BRAINSWEPT.get(mob);
        cc.setBrainswept(true);
        // CC API does the syncing for us

        mob.removeFreeWill();
        if (mob instanceof Villager villager) {
            ((AccessorVillager) villager).hex$releaseAllPois();
        }
    }

    @Override
    public void setColorizer(Player target, FrozenColorizer colorizer) {
        var cc = HexCardinalComponents.FAVORED_COLORIZER.get(target);
        cc.setColorizer(colorizer);
    }

    @Override
    public void setSentinel(Player target, Sentinel sentinel) {
        var cc = HexCardinalComponents.SENTINEL.get(target);
        cc.setSentinel(sentinel);
    }

    @Override
    public void setFlight(ServerPlayer target, FlightAbility flight) {
        var cc = HexCardinalComponents.FLIGHT.get(target);
        cc.setFlight(flight);
    }
    @Override
    public GravitySetting getGravitySetting(Entity target)
    {
        var cc = HexCardinalComponents.GRAVITY.get(target);
        return cc.getGravitySetting();
    }
    @Override
    public void setGravitySetting(Entity target, GravitySetting gravity) {
        var cc = HexCardinalComponents.GRAVITY.get(target);
        cc.setGravitySetting(gravity);
    }
    @Override
    public void setHarness(ServerPlayer target, CastingHarness harness) {
        var cc = HexCardinalComponents.HARNESS.get(target);
        cc.setHarness(harness);
    }

    @Override
    public void setPatterns(ServerPlayer target, List<ResolvedPattern> patterns) {
        var cc = HexCardinalComponents.PATTERNS.get(target);
        cc.setPatterns(patterns);
    }

    @Override
    public boolean isBrainswept(Mob mob) {
        var cc = HexCardinalComponents.BRAINSWEPT.get(mob);
        return cc.isBrainswept();
    }

    @Override
    public FlightAbility getFlight(ServerPlayer player) {
        var cc = HexCardinalComponents.FLIGHT.get(player);
        return cc.getFlight();
    }

    @Override
    public FrozenColorizer getColorizer(Player player) {
        var cc = HexCardinalComponents.FAVORED_COLORIZER.get(player);
        return cc.getColorizer();
    }

    @Override
    public Sentinel getSentinel(Player player) {
        var cc = HexCardinalComponents.SENTINEL.get(player);
        return cc.getSentinel();
    }

    @Override
    public CastingHarness getHarness(ServerPlayer player, InteractionHand hand) {
        var cc = HexCardinalComponents.HARNESS.get(player);
        return cc.getHarness(hand);
    }

    @Override
    public List<ResolvedPattern> getPatterns(ServerPlayer player) {
        var cc = HexCardinalComponents.PATTERNS.get(player);
        return cc.getPatterns();
    }

    @Override
    public void clearCastingData(ServerPlayer player) {
        this.setHarness(player, null);
        this.setPatterns(player, List.of());
    }

    @Override
    public @Nullable
    ManaHolder findManaHolder(ItemStack stack) {
        var cc = HexCardinalComponents.MANA_HOLDER.maybeGet(stack);
        return cc.orElse(null);
    }

    @Override
    public @Nullable
    DataHolder findDataHolder(ItemStack stack) {
        var cc = HexCardinalComponents.DATA_HOLDER.maybeGet(stack);
        return cc.orElse(null);
    }

    @Override
    public @Nullable
    HexHolder findHexHolder(ItemStack stack) {
        var cc = HexCardinalComponents.HEX_HOLDER.maybeGet(stack);
        return cc.orElse(null);
    }

    @Override
    public boolean isColorizer(ItemStack stack) {
        return HexCardinalComponents.COLORIZER.isProvidedBy(stack);
    }

    @Override
    public int getRawColor(FrozenColorizer colorizer, float time, Vec3 position) {
        var cc = HexCardinalComponents.COLORIZER.maybeGet(colorizer.item());
        return cc.map(col -> col.color(colorizer.owner(), time, position)).orElse(0xff_ff00dc);
    }

    @Override
    public <T extends BlockEntity> BlockEntityType<T> createBlockEntityType(BiFunction<BlockPos, BlockState, T> func,
        Block... blocks) {
        return FabricBlockEntityTypeBuilder.create(func::apply, blocks).build();
    }

    @Override
    @SuppressWarnings("UnstableApiUsage")
    public boolean tryPlaceFluid(Level level, InteractionHand hand, BlockPos pos, ItemStack stack, Fluid fluid) {
        Storage<FluidVariant> target = FluidStorage.SIDED.find(level, pos, Direction.UP);
        Storage<FluidVariant> emptyFrom = FluidStorage.ITEM.find(stack, ContainerItemContext.withInitial(stack));
        return StorageUtil.move(emptyFrom, target, (f) -> true, FluidConstants.BUCKET, null) > 0;
    }

    @Override
    public ResourceLocation getID(Block block) {
        return Registry.BLOCK.getKey(block);
    }

    @Override
    public ResourceLocation getID(Item item) {
        return Registry.ITEM.getKey(item);
    }

    @Override
    public ResourceLocation getID(VillagerProfession profession) {
        return Registry.VILLAGER_PROFESSION.getKey(profession);
    }

    @Override
    public Ingredient getUnsealedIngredient(ItemStack stack) {
        return FabricUnsealedIngredient.of(stack);
    }

    private static CreativeModeTab TAB = null;

    @Override
    public CreativeModeTab getTab() {
        if (TAB == null) {
            TAB = FabricItemGroupBuilder.create(modLoc("creative_tab"))
                .icon(HexItems::tabIcon)
                .build();
        }

        return TAB;
    }

    // do a stupid hack from botania
    private static List<ItemStack> stacks(Item... items) {
        return Stream.of(items).map(ItemStack::new).toList();
    }

    private static final List<List<ItemStack>> HARVEST_TOOLS_BY_LEVEL = List.of(
        stacks(Items.WOODEN_PICKAXE, Items.WOODEN_AXE, Items.WOODEN_HOE, Items.WOODEN_SHOVEL),
        stacks(Items.STONE_PICKAXE, Items.STONE_AXE, Items.STONE_HOE, Items.STONE_SHOVEL),
        stacks(Items.IRON_PICKAXE, Items.IRON_AXE, Items.IRON_HOE, Items.IRON_SHOVEL),
        stacks(Items.DIAMOND_PICKAXE, Items.DIAMOND_AXE, Items.DIAMOND_HOE, Items.DIAMOND_SHOVEL),
        stacks(Items.NETHERITE_PICKAXE, Items.NETHERITE_AXE, Items.NETHERITE_HOE, Items.NETHERITE_SHOVEL)
    );

    @Override
    public boolean isCorrectTierForDrops(Tier tier, BlockState bs) {
        if (!bs.requiresCorrectToolForDrops()) {
            return true;
        }

        int level = HexConfig.server()
            .opBreakHarvestLevelBecauseForgeThoughtItWasAGoodIdeaToImplementHarvestTiersUsingAnHonestToGodTopoSort();
        for (var tool : HARVEST_TOOLS_BY_LEVEL.get(level)) {
            if (tool.isCorrectToolForDrops(bs)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public Item.Properties addEquipSlotFabric(EquipmentSlot slot) {
        return new FabricItemSettings().equipmentSlot(s -> slot);
    }

    private static final IXplatTags TAGS = new IXplatTags() {
        @Override
        public TagKey<Item> amethystDust() {
            return HexItemTags.create(new ResourceLocation("c", "amethyst_dusts"));
        }

        @Override
        public TagKey<Item> gems() {
            return HexItemTags.create(new ResourceLocation("c", "gems"));
        }
    };

    @Override
    public IXplatTags tags() {
        return TAGS;
    }

    @Override
    public LootItemCondition.Builder isShearsCondition() {
        return AlternativeLootItemCondition.alternative(
            MatchTool.toolMatches(ItemPredicate.Builder.item().of(Items.SHEARS)),
            MatchTool.toolMatches(ItemPredicate.Builder.item().of(
                HexItemTags.create(new ResourceLocation("c", "shears"))))
        );
    }

    @Override
    public String getModName(String namespace) {
        if (namespace.equals("c")) {
            return "Common";
        }
        Optional<ModContainer> container = FabricLoader.getInstance().getModContainer(namespace);
        if (container.isPresent()) {
            return container.get().getMetadata().getName();
        }
        return namespace;
    }

    @Override
    public boolean isBreakingAllowed(Level world, BlockPos pos, BlockState state, Player player) {
        return PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(world, player, pos, state, world.getBlockEntity(pos));
    }

    @Override
    public boolean isPlacingAllowed(Level world, BlockPos pos, ItemStack blockStack, Player player) {
        ItemStack cached = player.getMainHandItem();
        player.setItemInHand(InteractionHand.MAIN_HAND, blockStack.copy());
        var success = UseItemCallback.EVENT.invoker().interact(player, world, InteractionHand.MAIN_HAND);
        player.setItemInHand(InteractionHand.MAIN_HAND, cached);
        return success.getResult() == InteractionResult.PASS; // No other mod tried to consume this
    }

    private static PehkuiInterop.ApiAbstraction PEHKUI_API = null;

    @Override
    public PehkuiInterop.ApiAbstraction getPehkuiApi() {
        if (!this.isModPresent(HexInterop.PEHKUI_ID)) {
            throw new IllegalArgumentException("cannot get the pehkui api without pehkui");
        }

        if (PEHKUI_API == null) {
            PEHKUI_API = new PehkuiInterop.ApiAbstraction() {
                @Override
                public float getScale(Entity e) {
                    return ScaleTypes.BASE.getScaleData(e).getScale();
                }

                @Override
                public void setScale(Entity e, float scale) {
                    ScaleTypes.BASE.getScaleData(e).setScale(scale);
                }
            };
        }
        return PEHKUI_API;
    }
}
