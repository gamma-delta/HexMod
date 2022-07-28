package at.petrak.hexcasting.fabric

import at.petrak.hexcasting.api.HexAPI.modLoc
import at.petrak.hexcasting.api.advancements.HexAdvancementTriggers
import at.petrak.hexcasting.api.mod.HexStatistics
import at.petrak.hexcasting.common.blocks.behavior.HexComposting
import at.petrak.hexcasting.common.blocks.behavior.HexStrippables
import at.petrak.hexcasting.common.casting.RegisterPatterns
import at.petrak.hexcasting.common.casting.operators.spells.great.OpFlight
import at.petrak.hexcasting.common.command.PatternResLocArgument
import at.petrak.hexcasting.common.entities.HexEntities
import at.petrak.hexcasting.common.items.ItemJewelerHammer
import at.petrak.hexcasting.common.items.ItemLens
import at.petrak.hexcasting.common.lib.*
import at.petrak.hexcasting.common.loot.HexLootHandler
import at.petrak.hexcasting.common.misc.AkashicTreeGrower
import at.petrak.hexcasting.common.misc.Brainsweeping
import at.petrak.hexcasting.common.misc.PlayerPositionRecorder
import at.petrak.hexcasting.common.recipe.HexRecipeSerializers
import at.petrak.hexcasting.fabric.event.VillagerConversionCallback
import at.petrak.hexcasting.fabric.network.FabricPacketHandler
import at.petrak.hexcasting.fabric.recipe.FabricUnsealedIngredient
import at.petrak.hexcasting.fabric.storage.FabricImpetusStorage
import at.petrak.hexcasting.interop.HexInterop
import io.github.tropheusj.serialization_hooks.ingredient.IngredientDeserializer
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.event.player.AttackBlockCallback
import net.fabricmc.fabric.api.event.player.UseEntityCallback
import net.fabricmc.fabric.api.loot.v1.event.LootTableLoadingCallback
import net.fabricmc.fabric.api.registry.FlammableBlockRegistry
import net.minecraft.commands.synchronization.ArgumentTypes
import net.minecraft.commands.synchronization.EmptyArgumentSerializer
import net.minecraft.core.Registry
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.InteractionResult
import java.util.function.BiConsumer

object FabricHexInitializer : ModInitializer {
    override fun onInitialize() {
        FabricHexConfig.setup()
        FabricPacketHandler.init()

        initListeners()

        initRegistries()

        ArgumentTypes.register(
            "hexcasting:pattern",
            PatternResLocArgument::class.java,
            EmptyArgumentSerializer { PatternResLocArgument.id() }
        )
        RegisterPatterns.registerPatterns()
        HexAdvancementTriggers.registerTriggers()
        HexComposting.setup()
        HexStrippables.init()
        FabricImpetusStorage.registerStorage()

        HexInterop.init()
    }

    fun initListeners() {
        UseEntityCallback.EVENT.register(Brainsweeping::tradeWithVillager)
        VillagerConversionCallback.EVENT.register(Brainsweeping::copyBrainsweepFromVillager)
        AttackBlockCallback.EVENT.register { player, world, _, pos, _ ->
            // SUCCESS cancels further processing and, on the client, sends a packet to the server.
            // PASS falls back to further processing.
            // FAIL cancels further processing and does not send a packet to the server.
            if (ItemJewelerHammer.shouldFailToBreak(player, world.getBlockState(pos), pos)) {
                InteractionResult.SUCCESS // "success"
            } else {
                InteractionResult.PASS
            }
        }

        ServerTickEvents.END_WORLD_TICK.register(PlayerPositionRecorder::updateAllPlayers)
        ServerTickEvents.END_WORLD_TICK.register(ItemLens::tickAllPlayers)
        ServerTickEvents.END_WORLD_TICK.register(OpFlight::tickAllPlayers)

        CommandRegistrationCallback.EVENT.register { dp, _ -> HexCommands.register(dp) }

        LootTableLoadingCallback.EVENT.register { _, _, id, supplier, _ ->
            HexLootHandler.lootLoad(
                id,
            ) { supplier.withPool(it) }
        }
    }

    fun initRegistries() {
        HexSounds.registerSounds(bind(Registry.SOUND_EVENT))
        HexBlocks.registerBlocks(bind(Registry.BLOCK))
        HexBlocks.registerBlockItems(bind(Registry.ITEM))
        HexBlockEntities.registerTiles(bind(Registry.BLOCK_ENTITY_TYPE))
        HexItems.registerItems(bind(Registry.ITEM))
        Registry.register(IngredientDeserializer.REGISTRY, modLoc("unsealed"), FabricUnsealedIngredient.Deserializer.INSTANCE)

        HexEntities.registerEntities(bind(Registry.ENTITY_TYPE))

        HexRecipeSerializers.registerSerializers(bind(Registry.RECIPE_SERIALIZER))
        HexParticles.registerParticles(bind(Registry.PARTICLE_TYPE))

        HexLootFunctions.registerSerializers(bind(Registry.LOOT_FUNCTION_TYPE))

        // Because of Java's lazy-loading of classes, can't use Kotlin static initialization for
        // any calls that will eventually touch FeatureUtils.register(), as the growers here do,
        // unless the class is called in this initialization step.
        AkashicTreeGrower.init()

        // Done with soft implements in forge
        val flameOn = FlammableBlockRegistry.getDefaultInstance()
        for (log in listOf(
            HexBlocks.AKASHIC_LOG,
            HexBlocks.AKASHIC_LOG_STRIPPED,
            HexBlocks.AKASHIC_WOOD,
            HexBlocks.AKASHIC_LOG_STRIPPED,
        )) {
            flameOn.add(log, 5, 5)
        }
        for (wood in listOf(
            HexBlocks.AKASHIC_PLANKS,
            HexBlocks.AKASHIC_PANEL,
            HexBlocks.AKASHIC_TILE,
            HexBlocks.AKASHIC_DOOR,
            HexBlocks.AKASHIC_TRAPDOOR,
            HexBlocks.AKASHIC_STAIRS,
            HexBlocks.AKASHIC_SLAB,
            HexBlocks.AKASHIC_STAIRS,
            HexBlocks.AKASHIC_SLAB,
            HexBlocks.AKASHIC_BUTTON,
            HexBlocks.AKASHIC_PRESSURE_PLATE,
        )) {
            flameOn.add(wood, 20, 5)
        }
        for (papery in listOf(
            HexBlocks.SCROLL_PAPER,
            HexBlocks.SCROLL_PAPER_LANTERN,
            HexBlocks.ANCIENT_SCROLL_PAPER,
            HexBlocks.ANCIENT_SCROLL_PAPER_LANTERN,

            )) {
            flameOn.add(papery, 100, 60)
        }
        for (leaves in listOf(
            HexBlocks.AKASHIC_LEAVES1,
            HexBlocks.AKASHIC_LEAVES2,
            HexBlocks.AKASHIC_LEAVES3,
        )) {
            flameOn.add(leaves, 60, 30)
        }

        HexRecipeSerializers.registerTypes()
        HexStatistics.register()
    }

    private fun <T> bind(registry: Registry<in T>): BiConsumer<T, ResourceLocation> =
        BiConsumer<T, ResourceLocation> { t, id -> Registry.register(registry, id, t) }
}
