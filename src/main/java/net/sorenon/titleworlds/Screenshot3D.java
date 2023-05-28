package net.sorenon.titleworlds;

import com.google.gson.JsonElement;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.Lifecycle;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.commands.Commands;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.WorldLoader;
import net.minecraft.server.WorldStem;
import net.minecraft.server.dedicated.DedicatedServerProperties;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.FolderRepositorySource;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.DataPackConfig;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.PrimaryLevelData;
import net.minecraft.world.level.storage.WorldData;
import net.sorenon.titleworlds.mixin.accessor.WorldOpenFlowsAcc;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static net.sorenon.titleworlds.TitleWorldsMod.LEVEL_SOURCE;
import static net.sorenon.titleworlds.TitleWorldsMod.saveOnExitSource;

public class Screenshot3D {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final DateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd+HH_mm_ss");

    public static String take3DScreenshot(ClientLevel originLevel, @Nullable String name) {

        if (name == null) {
            name = "3D_screenshot+" + DATE_FORMAT.format(new Date());
        }

//        TODO Screenshot.grab();
//        TODO FileUtil.findAvailableName

        createSnapshotWorldAndSave(
                name,
                originLevel,
                LEVEL_SOURCE
        );

        return name;

    }

    public static String take3DScreenshotOnExit(ClientLevel originLevel) {

        saveOnExitSource.findLevelCandidates().forEach(levelDirectory -> {
            Path levelPath = levelDirectory.path();
            try {
                FileUtils.deleteDirectory(levelPath.toFile());
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        String name = "3D_screenshot+" + DATE_FORMAT.format(new Date());

//        TODO Screenshot.grab();
//        TODO FileUtil.findAvailableName

        createSnapshotWorldAndSave(
                name,
                originLevel,
                saveOnExitSource
        );

        return name;
    }


    private static void createSnapshotWorldAndSave(
            String worldName,
            ClientLevel originLevel,
            LevelStorageSource levelSourceLocation

    ) {

        Minecraft minecraft = Minecraft.getInstance();

        LevelStorageSource.LevelStorageAccess levelStorageAccess;
        try {
            levelStorageAccess = levelSourceLocation.createAccess(worldName);
        } catch (IOException var22) {
            LOGGER.warn("Failed to read level {} data", worldName, var22);
            SystemToast.onWorldAccessFailure(minecraft, worldName);
            minecraft.setScreen(null);
            return;
        }
        PackRepository packRepository = new PackRepository(
                new ServerPacksSource(),
                new FolderRepositorySource(levelStorageAccess.getLevelPath(LevelResource.DATAPACK_DIR), PackType.SERVER_DATA, PackSource.WORLD)
        );
        WorldStem worldStem;
        try {
            ClientLevel.ClientLevelData originLevelData = originLevel.getLevelData();

            LevelSettings levelSettings = new LevelSettings(
                    worldName,
                    GameType.CREATIVE,
                    false,
                    Difficulty.PEACEFUL,
                    true,
                    originLevelData.getGameRules(),
                    levelStorageAccess.getDataConfiguration()
            );

            RegistryAccess registryAccess = originLevel.registryAccess();

            WritableRegistry<LevelStem> levelStems = new MappedRegistry<>(
                    Registries.LEVEL_STEM, Lifecycle.experimental(), false
            );


            levelStems.register(
                    ResourceKey.create(Registries.LEVEL_STEM, originLevel.dimension().location()),
                    new LevelStem(
                            originLevel.dimensionTypeRegistration(),
                            new FlatLevelSource(
                                    new FlatLevelGeneratorSettings(Optional.empty(), originLevel.getBiome(Minecraft.getInstance().player.blockPosition()), null)
                            )
                    ),
                    Lifecycle.stable()
            );

            WorldGenSettings worldGenSettings = new WorldGenSettings(
                    new WorldOptions(0, false, false),
                    new WorldDimensions(
                            levelStems
                    )
            );
            var worldFlows = minecraft.createWorldOpenFlows();
            WorldGenSettings finalWorldGenSettings = worldGenSettings;
            worldStem = Screenshot3D.loadWorldNonDataBlocking(minecraft, levelStorageAccess, packRepository,
//                    dataLoadContext -> {
//                        RegistryAccess writable = RegistryAccess.Frozen.EMPTY;
//                        DynamicOps<JsonElement> dynamicOps = RegistryOps.create(JsonOps.INSTANCE, registryAccess);
//                        DynamicOps<JsonElement> dynamicOps2 = RegistryOps.create(JsonOps.INSTANCE, writable);
//                        DataResult<WorldGenSettings> dataResult = WorldGenSettings.CODEC
//                                .encodeStart(dynamicOps, finalWorldGenSettings)
//                                .setLifecycle(Lifecycle.stable())
//                                .flatMap(jsonElement -> WorldGenSettings.CODEC.parse(dynamicOps2, jsonElement));
//                        WorldGenSettings worldGenSettings2 = dataResult.getOrThrow(
//                                false, Util.prefix("Error reading worldgen settings after loading data packs: ", LOGGER::error)
//                        );
//                        var levelData = new PrimaryLevelData(levelSettings, worldGenSettings2.options(), PrimaryLevelData.SpecialWorldProperty.NONE, dataResult.lifecycle());
////                    levelData.setSpawn(originLevel.getSharedSpawnPos(), originLevel.getSharedSpawnAngle());
//                        levelData.setSpawn(Minecraft.getInstance().player.blockPosition(), Minecraft.getInstance().player.yHeadRot);
//                        levelData.setDayTime(originLevelData.getDayTime());
//                        levelData.setGameTime(originLevelData.getGameTime());
////                                        loadedPlayerTag
////                                        levelData.setClearWeatherTime();
//                        levelData.setRaining(originLevelData.isRaining());
////                                        levelData.setRainTime();
//                        levelData.setThundering(originLevelData.isThundering());
////                                        levelData.setThunderTime();
////                                        levelData.setInitialized();
////                                        levelData.setWorldBorder();
////                                        levelData.setEndDragonFightData();
////                                        levelData.setCustomBossEvents();
////                                        levelData.setWanderingTraderSpawnDelay();
////                                        levelData.setWanderingTraderSpawnChance();
////                                        levelData.setWanderingTraderId();
////                                        knownServerBrands
////                                        wasModded
////                                        scheduledEvents
//                        WorldDimensions.withOverworld(originLevel.dimension(), levelStems, levelStems.get(ResourceKey.create(Registries.LEVEL_STEM, originLevel.dimension().location()).location()))
//                        return new WorldLoader.DataLoadOutput<PrimaryLevelData>(levelData, dataResult )
//                    },
                    dataLoadContext -> {
                        RegistryAccess writable = RegistryAccess.EMPTY;
                        DynamicOps<JsonElement> dynamicOps = RegistryOps.create(JsonOps.INSTANCE, registryAccess);
                        DynamicOps<JsonElement> dynamicOps2 = RegistryOps.create(JsonOps.INSTANCE, writable);
                        DataResult<WorldGenSettings> dataResult = WorldGenSettings.CODEC
                                .encodeStart(dynamicOps, worldGenSettings)
                                .setLifecycle(Lifecycle.stable())
                                .flatMap(jsonElement -> WorldGenSettings.CODEC.parse(dynamicOps2, jsonElement));
                        WorldGenSettings worldGenSettings2 = dataResult.getOrThrow(
                                false, Util.prefix("Error reading worldgen settings after loading data packs: ", LOGGER::error)
                        );
                        var levelData = new PrimaryLevelData(levelSettings, worldGenSettings2.options(), PrimaryLevelData.SpecialWorldProperty.NONE, dataResult.lifecycle());
////                    levelData.setSpawn(originLevel.getSharedSpawnPos(), originLevel.getSharedSpawnAngle());
                        levelData.setSpawn(Minecraft.getInstance().player.blockPosition(), Minecraft.getInstance().player.yHeadRot);
                        levelData.setDayTime(originLevelData.getDayTime());
                        levelData.setGameTime(originLevelData.getGameTime());
////                                        loadedPlayerTag
////                                        levelData.setClearWeatherTime();
                       levelData.setRaining(originLevelData.isRaining());
////                                        levelData.setRainTime();
                       levelData.setThundering(originLevelData.isThundering());
//                                      levelData.setThunderTime();
//                                        levelData.setInitialized();
//                                        levelData.setWorldBorder();
//                                        levelData.setEndDragonFightData();
//                                        levelData.setCustomBossEvents();
//                                        levelData.setWanderingTraderSpawnDelay();
//                                        levelData.setWanderingTraderSpawnChance();
//                                        levelData.setWanderingTraderId();
//                                        knownServerBrands
//                                        wasModded
//                                        scheduledEvents
                        RegistryOps<Tag> dynamicOps3 = RegistryOps.create(NbtOps.INSTANCE, dataLoadContext.datapackWorldgen());
                        Pair<WorldData, WorldDimensions.Complete> pair = levelStorageAccess.getDataTag(dynamicOps3, dataLoadContext.dataConfiguration(), levelStems, dataLoadContext.datapackWorldgen().allRegistriesLifecycle());
                        if (pair == null) {
                            throw new IllegalStateException("Failed to load world");
                        }

                        return new WorldLoader.DataLoadOutput<WorldData>(pair.getFirst(), pair.getSecond().dimensionsRegistryAccess());
                    },
                    WorldStem::new
            ).get();
        } catch (Exception var21) {
            LOGGER.warn("Failed to load datapacks, can't proceed with server load", var21);

            try {
                levelStorageAccess.close();
            } catch (IOException var17) {
                LOGGER.warn("Failed to unlock access to level {}", worldName, var17);
            }

            return;
        }

        WorldData exception = worldStem.worldData();

        try {
            RegistryAccess.Frozen iOException3 = worldStem.registries().compositeAccess();
            levelStorageAccess.saveDataTag(iOException3, exception);
            var server = MinecraftServer.spin(
                    thread -> new SnapshotCreateServer(thread, minecraft, originLevel, levelStorageAccess, packRepository, worldStem)
            );
            server.halt(true);
        } catch (Throwable var20) {
            CrashReport yggdrasilAuthenticationService = CrashReport.forThrowable(var20, "Starting integrated server");
            CrashReportCategory minecraftSessionService = yggdrasilAuthenticationService.addCategory("Starting integrated server");
            minecraftSessionService.setDetail("Level ID", worldName);
            minecraftSessionService.setDetail("Level Name", exception.getLevelName());
            throw new ReportedException(yggdrasilAuthenticationService);
        }
    }

    public static WorldLoader.InitConfig loadOrCreateConfig(LevelStorageSource.LevelStorageAccess levelStorageAccess, boolean bl, PackRepository packRepository) {
        WorldDataConfiguration worldDataConfiguration2;
        boolean bl2;
        WorldDataConfiguration worldDataConfiguration = levelStorageAccess.getDataConfiguration();
        if (worldDataConfiguration != null) {
            bl2 = false;
            worldDataConfiguration2 = worldDataConfiguration;
        } else {
            bl2 = true;
            var dataConfig = DataPackConfig.DEFAULT;
            worldDataConfiguration2 = new WorldDataConfiguration(dataConfig, FeatureFlags.DEFAULT_FLAGS);
        }
        WorldLoader.PackConfig packConfig = new WorldLoader.PackConfig(packRepository, worldDataConfiguration2, bl, bl2);
        return new WorldLoader.InitConfig(packConfig, Commands.CommandSelection.INTEGRATED, 2);
    }

    public static <D, R> CompletableFuture<R> loadWorldNonDataBlocking(Minecraft mc, LevelStorageSource.LevelStorageAccess levelStorageAccess, PackRepository packRepository, WorldLoader.WorldDataSupplier<D> worldDataSupplier, WorldLoader.ResultFactory<D, R> resultFactory) throws ExecutionException, InterruptedException {
        WorldLoader.InitConfig initConfig = loadOrCreateConfig(levelStorageAccess, false, packRepository);
        return WorldLoader.load(initConfig, worldDataSupplier, resultFactory, Util.backgroundExecutor(), mc);
    }

}
