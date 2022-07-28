package at.petrak.hexcasting.fabric;

import at.petrak.hexcasting.api.HexAPI;
import at.petrak.hexcasting.api.misc.ScrollQuantity;
import at.petrak.hexcasting.api.mod.HexConfig;
import at.petrak.hexcasting.xplat.IXplatAbstractions;
import io.github.fablabsmc.fablabs.api.fiber.v1.builder.ConfigTreeBuilder;
import io.github.fablabsmc.fablabs.api.fiber.v1.exception.ValueDeserializationException;
import io.github.fablabsmc.fablabs.api.fiber.v1.schema.type.derived.ConfigTypes;
import io.github.fablabsmc.fablabs.api.fiber.v1.serialization.FiberSerialization;
import io.github.fablabsmc.fablabs.api.fiber.v1.serialization.JanksonValueSerializer;
import io.github.fablabsmc.fablabs.api.fiber.v1.tree.ConfigTree;
import io.github.fablabsmc.fablabs.api.fiber.v1.tree.PropertyMirror;
import net.minecraft.resources.ResourceLocation;

import java.io.*;
import java.nio.file.*;
import java.util.List;

import static at.petrak.hexcasting.api.mod.HexConfig.anyMatch;
import static at.petrak.hexcasting.api.mod.HexConfig.noneMatch;

// https://github.com/VazkiiMods/Botania/blob/1.18.x/Fabric/src/main/java/vazkii/botania/fabric/FiberBotaniaConfig.java
public class FabricHexConfig {
    private static final Common COMMON = new Common();
    private static final Client CLIENT = new Client();
    private static final Server SERVER = new Server();

    private static void writeDefaultConfig(ConfigTree config, Path path, JanksonValueSerializer serializer) {
        try (OutputStream s = new BufferedOutputStream(
            Files.newOutputStream(path, StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW))) {
            FiberSerialization.serialize(config, s, serializer);
        } catch (FileAlreadyExistsException ignored) {
        } catch (IOException e) {
            HexAPI.LOGGER.error("Error writing default config", e);
        }
    }

    private static void setupConfig(ConfigTree config, Path p, JanksonValueSerializer serializer) {
        writeDefaultConfig(config, p, serializer);

        try (InputStream s = new BufferedInputStream(
            Files.newInputStream(p, StandardOpenOption.READ, StandardOpenOption.CREATE))) {
            FiberSerialization.deserialize(config, s, serializer);
        } catch (IOException | ValueDeserializationException e) {
            HexAPI.LOGGER.error("Error loading config from {}", p, e);
        }
    }

    public static void setup() {
        try {
            Files.createDirectory(Paths.get("config"));
        } catch (FileAlreadyExistsException ignored) {
        } catch (IOException e) {
            HexAPI.LOGGER.warn("Failed to make config dir", e);
        }

        var serializer = new JanksonValueSerializer(false);
        var common = COMMON.configure(ConfigTree.builder());
        setupConfig(common, Paths.get("config", HexAPI.MOD_ID + "-common.json5"), serializer);
        HexConfig.setCommon(COMMON);

        // We care about the client only on the *physical* client ...
        if (IXplatAbstractions.INSTANCE.isPhysicalClient()) {
            var client = CLIENT.configure(ConfigTree.builder());
            setupConfig(client, Paths.get("config", HexAPI.MOD_ID + "-client.json5"), serializer);
            HexConfig.setClient(CLIENT);
        }
        // but we care about the server on the *logical* server
        // i believe this should Just Work without a guard? assuming we don't access it from the client ever
        var server = SERVER.configure(ConfigTree.builder());
        setupConfig(server, Paths.get("config", HexAPI.MOD_ID + "-server.json5"), serializer);
        HexConfig.setServer(SERVER);

    }

    private static final class Common implements HexConfig.CommonConfigAccess {
        private final PropertyMirror<Integer> dustManaAmount = PropertyMirror.create(ConfigTypes.NATURAL);
        private final PropertyMirror<Integer> shardManaAmount = PropertyMirror.create(ConfigTypes.NATURAL);
        private final PropertyMirror<Integer> chargedCrystalManaAmount = PropertyMirror.create(ConfigTypes.NATURAL);
        private final PropertyMirror<Double> manaToHealthRate = PropertyMirror.create(
            ConfigTypes.DOUBLE.withMinimum(0d));

        public ConfigTree configure(ConfigTreeBuilder bob) {
            bob.fork("Mana Amounts")
                .beginValue("dustManaAmount", ConfigTypes.NATURAL, DEFAULT_DUST_MANA_AMOUNT)
                .withComment("How much mana a single Amethyst Dust item is worth")
                .finishValue(dustManaAmount::mirror)

                .beginValue("shardManaAmount", ConfigTypes.NATURAL, DEFAULT_SHARD_MANA_AMOUNT)
                .withComment("How much mana a single Amethyst Shard item is worth")
                .finishValue(shardManaAmount::mirror)

                .beginValue("chargedCrystalManaAmount", ConfigTypes.NATURAL, DEFAULT_CHARGED_MANA_AMOUNT)
                .withComment("How much mana a single Charged Amethyst Crystal item is worth")
                .finishValue(chargedCrystalManaAmount::mirror)

                .beginValue("manaToHealthRate", ConfigTypes.DOUBLE, DEFAULT_MANA_TO_HEALTH_RATE)
                .withComment("How many points of mana a half-heart is worth when casting from HP")
                .finishValue(manaToHealthRate::mirror)
                .finishBranch();

            return bob.build();
        }

        @Override
        public int dustManaAmount() {
            return dustManaAmount.getValue();
        }

        @Override
        public int shardManaAmount() {
            return shardManaAmount.getValue();
        }

        @Override
        public int chargedCrystalManaAmount() {
            return chargedCrystalManaAmount.getValue();
        }

        @Override
        public double manaToHealthRate() {
            return manaToHealthRate.getValue();
        }
    }

    private static final class Client implements HexConfig.ClientConfigAccess {
        private final PropertyMirror<Double> patternPointSpeedMultiplier = PropertyMirror.create(
            ConfigTypes.DOUBLE.withMinimum(0d));
        private final PropertyMirror<Boolean> ctrlTogglesOffStrokeOrder = PropertyMirror.create(ConfigTypes.BOOLEAN);
        private final PropertyMirror<Double> gridSnapThreshold = PropertyMirror.create(
            ConfigTypes.DOUBLE.withMinimum(0.5).withMaximum(1.0));
        private final PropertyMirror<Boolean> invertSpellbookScrollDirection = PropertyMirror.create(ConfigTypes.BOOLEAN);
        private final PropertyMirror<Boolean> invertAbacusScrollDirection = PropertyMirror.create(ConfigTypes.BOOLEAN);

        public ConfigTree configure(ConfigTreeBuilder bob) {
            bob
                .beginValue("patternPointSpeedMultiplier", ConfigTypes.DOUBLE, DEFAULT_PATTERN_POINT_SPEED_MULTIPLIER)
                .withComment("How fast the point showing you the stroke order on patterns moves.  Must be positive.")
                .finishValue(patternPointSpeedMultiplier::mirror)

                .beginValue("ctrlTogglesOffStrokeOrder", ConfigTypes.BOOLEAN, DEFAULT_CTRL_TOGGLES_OFF_STROKE_ORDER)
                .withComment("Whether the ctrl key will instead turn *off* the color gradient on patterns")
                .finishValue(ctrlTogglesOffStrokeOrder::mirror)

                .beginValue("gridSnapThreshold", ConfigTypes.DOUBLE, DEFAULT_GRID_SNAP_THRESHOLD)
                .withComment(
                    "When using a staff, the distance from one dot you have to go to snap to the next dot, where 0.5 means 50% of the way.  Valid range is 0.5 to 1.0, and may cause client crashes if set above or below those values.")
                .finishValue(gridSnapThreshold::mirror)

                .beginValue("invertSpellbookScrollDirection", ConfigTypes.BOOLEAN, DEFAULT_INVERT_SPELLBOOK_SCROLL)
                .withComment("Whether scrolling up (as opposed to down) will increase the page index of the spellbook, and vice versa")
                .finishValue(invertSpellbookScrollDirection::mirror)

                .beginValue("invertAbacusScrollDirection", ConfigTypes.BOOLEAN, DEFAULT_INVERT_ABACUS_SCROLL)
                .withComment("Whether scrolling up (as opposed to down) will increase the value of the abacus, and vice versa")
                .finishValue(invertAbacusScrollDirection::mirror)

            ;

            return bob.build();
        }

        @Override
        public double patternPointSpeedMultiplier() {
            return patternPointSpeedMultiplier.getValue();
        }

        @Override
        public boolean ctrlTogglesOffStrokeOrder() {
            return ctrlTogglesOffStrokeOrder.getValue();
        }

        @Override
        public boolean invertSpellbookScrollDirection() {
            return invertSpellbookScrollDirection.getValue();
        }

        @Override
        public boolean invertAbacusScrollDirection() {
            return invertAbacusScrollDirection.getValue();
        }

        @Override
        public double gridSnapThreshold() {
            return gridSnapThreshold.getValue();
        }
    }

    private static final class Server implements HexConfig.ServerConfigAccess {
        private final PropertyMirror<Integer> opBreakHarvestLevel = PropertyMirror.create(
            ConfigTypes.INTEGER.withValidRange(0, 4, 1));
        private final PropertyMirror<Integer> maxRecurseDepth = PropertyMirror.create(ConfigTypes.NATURAL);
        private final PropertyMirror<Integer> maxSpellCircleLength = PropertyMirror.create(
            ConfigTypes.INTEGER.withMinimum(4));
        private final PropertyMirror<List<String>> actionDenyList = PropertyMirror.create(
            ConfigTypes.makeList(ConfigTypes.STRING));
        private final PropertyMirror<List<String>> circleActionDenyList = PropertyMirror.create(
            ConfigTypes.makeList(ConfigTypes.STRING));
        private final PropertyMirror<Boolean> villagersOffendedByMindMurder = PropertyMirror.create(
            ConfigTypes.BOOLEAN);
        private final PropertyMirror<List<String>> fewScrollTables = PropertyMirror.create(
            ConfigTypes.makeList(ConfigTypes.STRING));
        private final PropertyMirror<List<String>> someScrollTables = PropertyMirror.create(
            ConfigTypes.makeList(ConfigTypes.STRING));
        private final PropertyMirror<List<String>> manyScrollTables = PropertyMirror.create(
            ConfigTypes.makeList(ConfigTypes.STRING));

        public ConfigTree configure(ConfigTreeBuilder bob) {
            bob.fork("Spells")
                .beginValue("maxRecurseDepth", ConfigTypes.NATURAL, DEFAULT_MAX_RECURSE_DEPTH)
                .withComment("How many times a spell can recursively cast other spells")
                .finishValue(maxRecurseDepth::mirror)

                .beginValue("opBreakHarvestLevel", ConfigTypes.NATURAL, DEFAULT_OP_BREAK_HARVEST_LEVEL)
                .withComment("The harvest level of the Break Block spell.\n" +
                    "0 = wood, 1 = stone, 2 = iron, 3 = diamond, 4 = netherite.")
                .finishValue(opBreakHarvestLevel::mirror)
                .finishBranch()

                .fork("Spell Circles")
                .beginValue("maxSpellCircleLength", ConfigTypes.NATURAL, DEFAULT_MAX_SPELL_CIRCLE_LENGTH)
                .withComment("The maximum number of slates in a spell circle.  Must be at least 4.")
                .finishValue(maxSpellCircleLength::mirror)

                .beginValue("circleActionDenyList", ConfigTypes.makeList(ConfigTypes.STRING), List.of())
                .withComment(
                    "Resource locations of disallowed actions within circles. Trying to cast one of these in a circle will result in a mishap.")
                .finishValue(circleActionDenyList::mirror)
                .finishBranch()

                .beginValue("actionDenyList", ConfigTypes.makeList(ConfigTypes.STRING), List.of())
                .withComment(
                    "Resource locations of disallowed actions. Trying to cast one of these will result in a mishap.")
                .finishValue(actionDenyList::mirror)

                .beginValue("villagersOffendedByMindMurder", ConfigTypes.BOOLEAN, true)
                .withComment("Should villagers take offense when you flay the mind of their fellow villagers?")
                .finishValue(villagersOffendedByMindMurder::mirror)

                .fork("Scrolls in Loot")
                .beginValue("fewScrollTables", ConfigTypes.makeList(ConfigTypes.STRING), DEFAULT_FEW_SCROLL_TABLES)
                .withComment("Which loot tables should a small number of Ancient Scrolls be injected into?")
                .finishValue(fewScrollTables::mirror)

                .beginValue("someScrollTables", ConfigTypes.makeList(ConfigTypes.STRING), DEFAULT_SOME_SCROLL_TABLES)
                .withComment("Which loot tables should a decent number of Ancient Scrolls be injected into?")
                .finishValue(someScrollTables::mirror)

                .beginValue("manyScrollTables", ConfigTypes.makeList(ConfigTypes.STRING), DEFAULT_MANY_SCROLL_TABLES)
                .withComment("Which loot tables should a huge number of Ancient Scrolls be injected into?")
                .finishValue(manyScrollTables::mirror)
                .finishBranch();

            return bob.build();
        }

        @Override
        public int opBreakHarvestLevelBecauseForgeThoughtItWasAGoodIdeaToImplementHarvestTiersUsingAnHonestToGodTopoSort() {
            return opBreakHarvestLevel.getValue();
        }

        @Override
        public int maxRecurseDepth() {
            return maxRecurseDepth.getValue();
        }

        @Override
        public int maxSpellCircleLength() {
            return maxSpellCircleLength.getValue();
        }

        @Override
        public boolean isActionAllowed(ResourceLocation actionID) {
            return noneMatch(actionDenyList.getValue(), actionID);
        }

        @Override
        public boolean isActionAllowedInCircles(ResourceLocation actionID) {
            return noneMatch(circleActionDenyList.getValue(), actionID);
        }

        @Override
        public boolean doVillagersTakeOffenseAtMindMurder() {
            return villagersOffendedByMindMurder.getValue();
        }

        @Override
        public ScrollQuantity scrollsForLootTable(ResourceLocation lootTable) {
            if (anyMatch(fewScrollTables.getValue(), lootTable)) {
                return ScrollQuantity.FEW;
            } else if (anyMatch(someScrollTables.getValue(), lootTable)) {
                return ScrollQuantity.SOME;
            } else if (anyMatch(manyScrollTables.getValue(), lootTable)) {
                return ScrollQuantity.MANY;
            }
            return ScrollQuantity.NONE;
        }
    }
}
