package at.petrak.hexcasting.xplat;

import at.petrak.hexcasting.api.HexAPI;
import at.petrak.hexcasting.api.addldata.DataHolder;
import at.petrak.hexcasting.api.addldata.HexHolder;
import at.petrak.hexcasting.api.addldata.ManaHolder;
import at.petrak.hexcasting.api.misc.FrozenColorizer;
import at.petrak.hexcasting.api.misc.GravitySetting;
import at.petrak.hexcasting.api.player.FlightAbility;
import at.petrak.hexcasting.api.player.Sentinel;
import at.petrak.hexcasting.api.spell.casting.CastingHarness;
import at.petrak.hexcasting.api.spell.casting.ResolvedPattern;
import at.petrak.hexcasting.common.network.IMessage;
import at.petrak.hexcasting.interop.pehkui.PehkuiInterop;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.ServiceLoader;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

/**
 * more like IHexplatAbstracts lmaooooooo
 */
public interface IXplatAbstractions {
    Platform platform();

    boolean isModPresent(String id);

    boolean isPhysicalClient();

    void initPlatformSpecific();

    void sendPacketToPlayer(ServerPlayer target, IMessage packet);

    void sendPacketNear(Vec3 pos, double radius, ServerLevel dimension, IMessage packet);

    // https://github.com/VazkiiMods/Botania/blob/13b7bcd9cbb6b1a418b0afe455662d29b46f1a7f/Xplat/src/main/java/vazkii/botania/xplat/IXplatAbstractions.java#L157
    Packet<?> toVanillaClientboundPacket(IMessage message);

    double getReachDistance(Player player);

    // Things that used to be caps

    /**
     * Irregardless of whether it can actually be brainswept (you need to do the checking yourself)
     */
    void brainsweep(Mob mob);

    void setColorizer(Player target, FrozenColorizer colorizer);

    void setSentinel(Player target, Sentinel sentinel);

    void setFlight(ServerPlayer target, FlightAbility flight);

    void setHarness(ServerPlayer target, @Nullable CastingHarness harness);

    void setPatterns(ServerPlayer target, List<ResolvedPattern> patterns);

    boolean isBrainswept(Mob mob);

    FlightAbility getFlight(ServerPlayer player);

    FrozenColorizer getColorizer(Player player);

    Sentinel getSentinel(Player player);

    CastingHarness getHarness(ServerPlayer player, InteractionHand hand);

    List<ResolvedPattern> getPatterns(ServerPlayer player);

    void clearCastingData(ServerPlayer player);

    @Nullable
    ManaHolder findManaHolder(ItemStack stack);

    @Nullable
    DataHolder findDataHolder(ItemStack stack);

    @Nullable
    HexHolder findHexHolder(ItemStack stack);

    GravitySetting getGravitySetting(Entity entity);

    void setGravitySetting(Entity entity, GravitySetting gravity);


    // coooollooorrrs

    boolean isColorizer(ItemStack stack);

    int getRawColor(FrozenColorizer colorizer, float time, Vec3 position);

    // Items

    /**
     * No-op on forge (use a SoftImplement)
     */
    Item.Properties addEquipSlotFabric(EquipmentSlot slot);

    // Blocks

    <T extends BlockEntity> BlockEntityType<T> createBlockEntityType(BiFunction<BlockPos, BlockState, T> func,
        Block... blocks);

    boolean tryPlaceFluid(Level level, InteractionHand hand, BlockPos pos, ItemStack stack, Fluid fluid);


    // misc

    CreativeModeTab getTab();

    boolean isCorrectTierForDrops(Tier tier, BlockState bs);

    ResourceLocation getID(Block block);

    ResourceLocation getID(Item item);

    ResourceLocation getID(VillagerProfession profession);

    Ingredient getUnsealedIngredient(ItemStack stack);

    IXplatTags tags();

    LootItemCondition.Builder isShearsCondition();

    String getModName(String namespace);

    boolean isBreakingAllowed(Level world, BlockPos pos, BlockState state, Player player);

    boolean isPlacingAllowed(Level world, BlockPos pos, ItemStack blockStack, Player player);

    // interop

    PehkuiInterop.ApiAbstraction getPehkuiApi();

    ///

    IXplatAbstractions INSTANCE = find();

    private static IXplatAbstractions find() {
        var providers = ServiceLoader.load(IXplatAbstractions.class).stream().toList();
        if (providers.size() != 1) {
            var names = providers.stream().map(p -> p.type().getName()).collect(Collectors.joining(",", "[", "]"));
            throw new IllegalStateException(
                "There should be exactly one IXplatAbstractions implementation on the classpath. Found: " + names);
        } else {
            var provider = providers.get(0);
            HexAPI.LOGGER.debug("Instantiating xplat impl: " + provider.type().getName());
            return provider.get();
        }
    }

}
