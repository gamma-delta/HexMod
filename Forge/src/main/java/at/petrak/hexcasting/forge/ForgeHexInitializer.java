package at.petrak.hexcasting.forge;

import at.petrak.hexcasting.api.HexAPI;
import at.petrak.hexcasting.api.PatternRegistry;
import at.petrak.hexcasting.api.advancements.HexAdvancementTriggers;
import at.petrak.hexcasting.api.mod.HexConfig;
import at.petrak.hexcasting.api.mod.HexStatistics;
import at.petrak.hexcasting.common.blocks.behavior.HexComposting;
import at.petrak.hexcasting.common.blocks.behavior.HexStrippables;
import at.petrak.hexcasting.common.casting.RegisterPatterns;
import at.petrak.hexcasting.common.casting.operators.spells.great.OpFlight;
import at.petrak.hexcasting.common.command.PatternResLocArgument;
import at.petrak.hexcasting.common.entities.HexEntities;
import at.petrak.hexcasting.common.items.ItemJewelerHammer;
import at.petrak.hexcasting.common.items.ItemLens;
import at.petrak.hexcasting.common.lib.*;
import at.petrak.hexcasting.common.loot.HexLootHandler;
import at.petrak.hexcasting.common.misc.AkashicTreeGrower;
import at.petrak.hexcasting.common.misc.Brainsweeping;
import at.petrak.hexcasting.common.misc.PlayerPositionRecorder;
import at.petrak.hexcasting.common.recipe.HexRecipeSerializers;
import at.petrak.hexcasting.forge.cap.CapSyncers;
import at.petrak.hexcasting.forge.cap.ForgeCapabilityHandler;
import at.petrak.hexcasting.forge.datagen.HexForgeDataGenerators;
import at.petrak.hexcasting.forge.interop.curios.CuriosApiInterop;
import at.petrak.hexcasting.forge.interop.curios.CuriosRenderers;
import at.petrak.hexcasting.forge.network.ForgePacketHandler;
import at.petrak.hexcasting.forge.network.MsgBrainsweepAck;
import at.petrak.hexcasting.forge.recipe.ForgeUnsealedIngredient;
import at.petrak.hexcasting.interop.HexInterop;
import at.petrak.hexcasting.xplat.IXplatAbstractions;
import net.minecraft.commands.synchronization.ArgumentTypes;
import net.minecraft.commands.synchronization.EmptyArgumentSerializer;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.event.LootTableLoadEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingConversionEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.IForgeRegistryEntry;
import thedarkcolour.kotlinforforge.KotlinModLoadingContext;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static at.petrak.hexcasting.api.HexAPI.modLoc;

@Mod(HexAPI.MOD_ID)
public class ForgeHexInitializer {
    public ForgeHexInitializer() {
        initConfig();
        initRegistry();
        initListeners();
    }

    private static void initConfig() {
        var config = new ForgeConfigSpec.Builder().configure(ForgeHexConfig::new);
        var clientConfig = new ForgeConfigSpec.Builder().configure(ForgeHexConfig.Client::new);
        var serverConfig = new ForgeConfigSpec.Builder().configure(ForgeHexConfig.Server::new);
        HexConfig.setCommon(config.getLeft());
        HexConfig.setClient(clientConfig.getLeft());
        HexConfig.setServer(serverConfig.getLeft());
        var mlc = ModLoadingContext.get();
        mlc.registerConfig(ModConfig.Type.COMMON, config.getRight());
        mlc.registerConfig(ModConfig.Type.CLIENT, clientConfig.getRight());
        mlc.registerConfig(ModConfig.Type.SERVER, serverConfig.getRight());
    }

    private static void initRegistry() {
        bind(ForgeRegistries.SOUND_EVENTS, HexSounds::registerSounds);
        bind(ForgeRegistries.BLOCKS, HexBlocks::registerBlocks);
        bind(ForgeRegistries.ITEMS, HexBlocks::registerBlockItems);
        bind(ForgeRegistries.BLOCK_ENTITIES, HexBlockEntities::registerTiles);
        bind(ForgeRegistries.ITEMS, HexItems::registerItems);

        bind(ForgeRegistries.RECIPE_SERIALIZERS, HexRecipeSerializers::registerSerializers);

        bind(ForgeRegistries.ENTITIES, HexEntities::registerEntities);

        bind(ForgeRegistries.PARTICLE_TYPES, HexParticles::registerParticles);

        ArgumentTypes.register(HexAPI.MOD_ID + ":pattern", PatternResLocArgument.class,
            new EmptyArgumentSerializer<>(PatternResLocArgument::id));
        HexAdvancementTriggers.registerTriggers();
    }

    // https://github.com/VazkiiMods/Botania/blob/1.18.x/Forge/src/main/java/vazkii/botania/forge/ForgeCommonInitializer.java
    private static <T extends IForgeRegistryEntry<T>> void bind(IForgeRegistry<T> registry,
        Consumer<BiConsumer<T, ResourceLocation>> source) {
        getModEventBus().addGenericListener(registry.getRegistrySuperType(),
            (RegistryEvent.Register<T> event) -> {
                IForgeRegistry<T> forgeRegistry = event.getRegistry();
                source.accept((t, rl) -> {
                    t.setRegistryName(rl);
                    forgeRegistry.register(t);
                });
            });
    }

    private static void initListeners() {
        var modBus = getModEventBus();
        var evBus = MinecraftForge.EVENT_BUS;

        modBus.register(ForgeHexClientInitializer.class);

        modBus.addListener((FMLCommonSetupEvent evt) ->
            evt.enqueueWork(() -> {
                ForgePacketHandler.init();
                HexComposting.setup();
                HexStrippables.init();
                RegisterPatterns.registerPatterns();
                // Forge does not strictly require TreeGrowers to initialize during early game stages, unlike Fabric and Quilt.
                // However, all launcher panic if the same resource is registered twice.  But do need blocks and items to be completely initialized.
                // Explicitly calling here avoids potential confusion, or reliance on tricks that may fail under compiler optimization.
                AkashicTreeGrower.init();

                HexInterop.init();
            }));

        // We have to do these at some point when the registries are still open
        modBus.addGenericListener(Item.class, (RegistryEvent<Item> evt) -> {
            HexRecipeSerializers.registerTypes();
            CraftingHelper.register(modLoc("unsealed"), ForgeUnsealedIngredient.Serializer.INSTANCE);
            HexStatistics.register();
            HexLootFunctions.registerSerializers((lift, id) ->
                Registry.register(Registry.LOOT_FUNCTION_TYPE, id, lift));
        });

        modBus.addListener((FMLLoadCompleteEvent evt) ->
            HexAPI.LOGGER.info(PatternRegistry.getPatternCountInfo()));

        evBus.addListener((PlayerInteractEvent.EntityInteract evt) -> {
            var res = Brainsweeping.tradeWithVillager(
                evt.getPlayer(), evt.getWorld(), evt.getHand(), evt.getTarget(), null);
            if (res.consumesAction()) {
                evt.setCanceled(true);
                evt.setCancellationResult(res);
            }
        });
        evBus.addListener((LivingConversionEvent.Post evt) ->
            Brainsweeping.copyBrainsweepFromVillager(evt.getEntityLiving(), evt.getOutcome()));

        evBus.addListener((LivingEvent.LivingUpdateEvent evt) -> {
            OpFlight.INSTANCE.tickDownFlight(evt.getEntityLiving());
            ItemLens.tickLens(evt.getEntityLiving());
        });

        evBus.addListener((TickEvent.WorldTickEvent evt) -> {
            if (evt.phase == TickEvent.Phase.END && evt.world instanceof ServerLevel world) {
                PlayerPositionRecorder.updateAllPlayers(world);
            }
        });

        evBus.addListener((RegisterCommandsEvent evt) -> HexCommands.register(evt.getDispatcher()));

        evBus.addListener((PlayerEvent.BreakSpeed evt) ->
            evt.setCanceled(ItemJewelerHammer.shouldFailToBreak(evt.getPlayer(), evt.getState(), evt.getPos())));

        evBus.addListener((LootTableLoadEvent evt) -> HexLootHandler.lootLoad(
            evt.getName(),
            evt.getTable()::addPool));

        // === Events implemented in other ways on Fabric

        // On Fabric this should be auto-synced
        evBus.addListener((PlayerEvent.StartTracking evt) -> {
            Entity target = evt.getTarget();
            if (evt.getPlayer() instanceof ServerPlayer serverPlayer &&
                target instanceof Mob mob && IXplatAbstractions.INSTANCE.isBrainswept(mob)) {
                ForgePacketHandler.getNetwork()
                    .send(PacketDistributor.PLAYER.with(() -> serverPlayer), MsgBrainsweepAck.of(mob));
            }
        });

        // Implemented with a mixin on Farbc
        evBus.addListener((BlockEvent.BlockToolModificationEvent evt) -> {
            if (!evt.isSimulated() && evt.getToolAction() == ToolActions.AXE_STRIP) {
                BlockState bs = evt.getState();
                var output = HexStrippables.STRIPPABLES.get(bs.getBlock());
                if (output != null) {
                    evt.setFinalState(output.withPropertiesOf(bs));
                }
            }
        });

        // Caps are cardinal components on farbc
        modBus.addListener(ForgeCapabilityHandler::registerCaps);
        evBus.addGenericListener(ItemStack.class, ForgeCapabilityHandler::attachItemCaps);
        evBus.addGenericListener(BlockEntity.class, ForgeCapabilityHandler::attachBlockEntityCaps);

        modBus.register(HexForgeDataGenerators.class);
        modBus.register(ForgeCapabilityHandler.class);
        evBus.register(CapSyncers.class);

        if (ModList.get().isLoaded(HexInterop.Forge.CURIOS_API_ID)) {
            modBus.addListener(CuriosApiInterop::onInterModEnqueue);
            modBus.addListener(CuriosApiInterop::onClientSetup);
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> modBus.addListener(CuriosRenderers::onLayerRegister));
        }
    }

    // aaaauughhg
    private static IEventBus getModEventBus() {
        return KotlinModLoadingContext.Companion.get().getKEventBus();
    }
}
