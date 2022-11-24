package cn.coolloong;

import cn.nukkit.level.DimensionEnum;
import cn.nukkit.level.format.anvil.Anvil;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.utils.Utils;

import java.io.*;
import java.nio.ByteOrder;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;

public class WorldConvert {
    public static final Set<RegionConvertWork> tasks = new HashSet<>();
    public static final ForkJoinPool THREAD_POOL_EXECUTOR = (ForkJoinPool) Executors.newWorkStealingPool();
    private String path;

    public WorldConvert(String path) {
        this.path = path;
    }

    public WorldConvert convert(DimensionEnum dimension) {
        File output = null;
        File preCreate = null;
        CompoundTag jeLevelData = null;

        try (var levelDat = new FileInputStream(path + "/level.dat")) {
            jeLevelData = NBTIO.readCompressed(levelDat, ByteOrder.BIG_ENDIAN).getCompound("Data");
        } catch (IOException e) {
            System.out.println("level.dat file does not exist, please check if the path is correct !!!");
            PNXWorldConverter.close(0);
        }
        if (dimension.equals(DimensionEnum.OVERWORLD)) {
            output = new File("output/world");
            preCreate = new File("output/world/region");
        } else if (dimension.equals(DimensionEnum.NETHER)) {
            this.path = this.path + "/DIM-1";
            output = new File("output/nether");
            preCreate = new File("output/nether/region");
        } else if (dimension.equals(DimensionEnum.END)) {
            this.path = this.path + "/DIM1";
            output = new File("output/the_end");
            preCreate = new File("output/the_end/region");
        }
        if (!preCreate.exists() && !preCreate.mkdirs()) {
            System.out.println("Could not create the directory " + preCreate);
            PNXWorldConverter.close(0);
        }

        var regions = new File(path + "\\region");
        if (!regions.exists()) {
            System.out.println("region folder does not exist, please check if the path is correct !!!");
            PNXWorldConverter.close(0);
        }

        var dimensionData = dimension.getDimensionData();
        var generatorName = switch (dimension) {
            case NETHER -> "nether";
            case END -> "the_end";
            default -> "normal";
        };
        CompoundTag levelData = new CompoundTag("Data")
                .putCompound("GameRules", jeLevelData.getCompound("GameRules"))
                .putLong("DayTime", jeLevelData.getLong("DayTime"))
                .putInt("GameType", jeLevelData.getInt("GameType"))
                .putString("generatorName", generatorName)//flat unknown
                .putString("generatorOptions", dimension.equals(DimensionEnum.OVERWORLD) ? "default:overworld" : "")
                .putInt("generatorVersion", 1)
                .putBoolean("hardcore", jeLevelData.getBoolean("hardcore"))
                .putBoolean("initialized", jeLevelData.getBoolean("initialized"))
                .putLong("LastPlayed", jeLevelData.getLong("LastPlayed"))
                .putString("LevelName", jeLevelData.getString("LevelName"))
                .putBoolean("raining", jeLevelData.getBoolean("raining"))
                .putInt("rainTime", jeLevelData.getInt("rainTime"))
                .putLong("RandomSeed", jeLevelData.getCompound("WorldGenSettings").getLong("seed"))
                .putInt("SpawnX", jeLevelData.getInt("SpawnX"))
                .putInt("SpawnY", jeLevelData.getInt("SpawnY"))
                .putInt("SpawnZ", jeLevelData.getInt("SpawnZ"))
                .putBoolean("thundering", jeLevelData.getBoolean("thundering"))
                .putInt("thunderTime", jeLevelData.getInt("thunderTime"))
                .putInt("version", 19134)//jeLevelData.getInt("version")
                .putLong("Time", jeLevelData.getLong("Time"))
                .putLong("SizeOnDisk", 0)
                .putCompound("dimensionData", new CompoundTag("dimensionData")
                        .putString("dimensionName", dimensionData.getDimensionName())
                        .putInt("dimensionId", dimensionData.getDimensionId())
                        .putInt("maxHeight", dimensionData.getMaxHeight())
                        .putInt("minHeight", dimensionData.getMinHeight())
                        .putInt("chunkSectionCount", dimensionData.getChunkSectionCount()));
        try {
            Utils.safeWrite(new File(output, "level.dat"), file -> {
                try (FileOutputStream fos = new FileOutputStream(file); BufferedOutputStream out = new BufferedOutputStream(fos)) {
                    NBTIO.writeGZIPCompressed(new CompoundTag().putCompound("Data", levelData), out, ByteOrder.BIG_ENDIAN);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (IOException e) {
            System.out.println("Unable to write level.dat to output folder!!!");
            PNXWorldConverter.close(0);
        }
        try {
            Anvil format = new Anvil(null, output.getPath() + "/");
            for (var mca : Objects.requireNonNull(regions.listFiles())) {
                var task = new RegionConvertWork(mca, format, dimension);
                THREAD_POOL_EXECUTOR.execute(task);
                tasks.add(task);
            }
        } catch (IOException e) {
            e.printStackTrace();
            PNXWorldConverter.close(0);
        }
        return this;
    }
}
