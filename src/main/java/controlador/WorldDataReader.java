package controlador;

import static controlador.Utilidades.fromMStoDateString;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import modelo.World;
import net.querz.nbt.io.NBTUtil;
import net.querz.nbt.io.NamedTag;
import net.querz.nbt.tag.CompoundTag;
import net.querz.nbt.tag.IntTag;
import net.querz.nbt.tag.ListTag;
import net.querz.nbt.tag.LongTag;
import net.querz.nbt.tag.NumberTag;
import net.querz.nbt.tag.StringTag;
import net.querz.nbt.tag.Tag;

public class WorldDataReader {
    private static final Path LEGACY_OVERWORLD_REGION_DIR = Path.of("region");
    private static final Path NAMESPACED_OVERWORLD_REGION_DIR = Path.of("dimensions", "minecraft", "overworld", "region");

    // Minecraft no ha guardado siempre la seed en el mismo sitio.
    // Este reader intenta ocultar esa diferencia para que la UI no tenga que
    // saber si el mundo es legacy o moderno.

    // Lee desde level.dat el tiempo acumulado del mundo indicado.
    private static Path getDataFile(World mundo){
        if(mundo == null || mundo.getWorldName() == null || mundo.getWorldName().isBlank()) return null;
        Path levelDat = Path.of(mundo.getWorldDir()).resolve("level.dat");
        if(!Files.isRegularFile(levelDat)) return null;
        return levelDat;
    }

    // Punto centralizado para abrir level.dat y devolver la raiz NBT.
    // Asi evitamos repetir la misma lectura en todos los getters.
    private static CompoundTag getRootTag(World mundo) {
        Path levelDat = getDataFile(mundo);
        if(levelDat == null) return null;

        try {
            NamedTag named = NBTUtil.read(levelDat.toFile());
            if(named == null || !(named.getTag() instanceof CompoundTag root)) return null;
            return root;
        } catch (Exception ex) {
            return null;
        }
    }

    // Dentro de level.dat, casi todos los metadatos del mundo viven bajo Data.
    private static CompoundTag getDataTag(World mundo) {
        CompoundTag root = getRootTag(mundo);
        return root == null ? null : root.getCompoundTag("Data");
    }

    public static WorldMetadata readMetadata(World mundo) {
        CompoundTag data = getDataTag(mundo);
        if(data == null) return WorldMetadata.empty();

        Map<String, String> gameRules = readGameRules(data);
        return new WorldMetadata(
                readActiveTicks(data),
                readLastPlayed(data),
                readVersionName(data),
                readDataVersion(data),
                readSeed(data),
                readSpawnPoint(data),
                readGameMode(data),
                readDifficulty(data),
                readBooleanLabel(data, "DifficultyLocked"),
                readWeatherSummary(data),
                readDayTime(data),
                readBooleanLabel(data, "hardcore"),
                readBooleanLabel(data, "allowCommands"),
                readDataPacksSummary(data),
                readGameRulesSummary(gameRules),
                gameRules
        );
    }

    public static String getVersionName(World mundo) {
        try {
            CompoundTag data = getDataTag(mundo);
            if(data == null) return null;

            CompoundTag versionTag = data.getCompoundTag("Version");
            if(versionTag == null) return null;

            String versionName = versionTag.getString("Name");
            return versionName == null || versionName.isBlank() ? null : versionName;
        } catch (Exception ex) {
            return null;
        }
    }

    public static String getDataVersion(World mundo) {
        try {
            CompoundTag data = getDataTag(mundo);
            if(data == null) return null;

            NumberTag<?> dataVersionTag = readNumberTag(data, "DataVersion");
            if(dataVersionTag == null) return null;
            return Integer.toString(dataVersionTag.asInt());
        } catch (Exception ex) {
            return null;
        }
    }

    public static WorldStorageLayout detectOverworldStorageLayout(World mundo) {
        Path worldDir = getWorldDirectory(mundo);
        if(worldDir == null) return WorldStorageLayout.LEGACY;

        Path legacyDir = worldDir.resolve(LEGACY_OVERWORLD_REGION_DIR);
        Path namespacedDir = worldDir.resolve(NAMESPACED_OVERWORLD_REGION_DIR);

        boolean hasLegacyDir = Files.isDirectory(legacyDir);
        boolean hasNamespacedDir = Files.isDirectory(namespacedDir);
        if(hasNamespacedDir && !hasLegacyDir) {
            return WorldStorageLayout.NAMESPACED;
        }
        if(hasLegacyDir && !hasNamespacedDir) {
            return WorldStorageLayout.LEGACY;
        }

        // Desde la rama 26.x Mojang movio el Overworld a dimensions/minecraft/overworld.
        // Si aun no existe la carpeta (por ejemplo, mundo nuevo sin chunks), usamos el
        // nombre de version de level.dat para decidir que layout esperar.
        String versionName = getVersionName(mundo);
        if(usesNamespacedStorage(versionName)) {
            return WorldStorageLayout.NAMESPACED;
        }

        return WorldStorageLayout.LEGACY;
    }

    public static Path getOverworldRegionDirectory(World mundo) {
        Path worldDir = getWorldDirectory(mundo);
        if(worldDir == null) return null;

        Path legacyDir = worldDir.resolve(LEGACY_OVERWORLD_REGION_DIR);
        Path namespacedDir = worldDir.resolve(NAMESPACED_OVERWORLD_REGION_DIR);

        WorldStorageLayout layout = detectOverworldStorageLayout(mundo);
        if(layout == WorldStorageLayout.NAMESPACED) {
            return Files.isDirectory(namespacedDir) || !Files.isDirectory(legacyDir) ? namespacedDir : legacyDir;
        }
        return Files.isDirectory(legacyDir) || !Files.isDirectory(namespacedDir) ? legacyDir : namespacedDir;
    }

    private static Path getWorldDirectory(World mundo) {
        if(mundo == null || mundo.getWorldDir() == null || mundo.getWorldDir().isBlank()) return null;
        return Path.of(mundo.getWorldDir());
    }

    private static boolean usesNamespacedStorage(String versionName) {
        if(versionName == null || versionName.isBlank()) return false;

        String normalized = versionName.trim().toLowerCase(Locale.ROOT);
        return normalized.startsWith("26.")
                || normalized.startsWith("26w")
                || normalized.contains("26.1 snapshot")
                || normalized.contains("snapshot 26");
    }

    public static long getActiveTicks(World mundo) {
        try{
            CompoundTag data = getDataTag(mundo);
            if(data == null) return 0L;

            NumberTag<?> timeTag = readNumberTag(data, "Time");
            if(timeTag == null) return 0L;
            return Math.max(0L, timeTag.asLong());
        } catch (Exception ex){
            System.out.println("Ha ocurrido un error intentando leer los ticks del mundo: "+mundo.getWorldDir());
            return 0L;
        }
    }

    public static String getLastPlayed(World mundo){
        try{
            CompoundTag data = getDataTag(mundo);
            if(data == null) return null;

            NumberTag<?> timeTag = readNumberTag(data, "LastPlayed");
            if(timeTag == null) return null;
            return fromMStoDateString(timeTag.asLong());
        } catch (Exception ex){
            System.out.println("Ha ocurrido un error accediendo a LastPlayed en el mundo: "+mundo.getWorldDir());
            return null;
        }
    }

    public static String getSeed(World mundo) {
        try {
            CompoundTag data = getDataTag(mundo);
            if(data == null) return "-";

            // Mundos legacy y muchas versiones intermedias guardan la seed en Data.RandomSeed.
            NumberTag<?> randomSeedTag = readNumberTag(data, "RandomSeed");
            if(randomSeedTag != null) {
                return Long.toString(randomSeedTag.asLong());
            }

            // En mundos modernos la seed suele vivir en Data.WorldGenSettings.seed.
            CompoundTag worldGenSettings = data.getCompoundTag("WorldGenSettings");
            if(worldGenSettings != null) {
                Tag<?> seedTag = worldGenSettings.get("seed");
                if(seedTag instanceof LongTag longSeedTag) {
                    return Long.toString(longSeedTag.asLong());
                }
                if(seedTag instanceof IntTag intSeedTag) {
                    return Integer.toString(intSeedTag.asInt());
                }
                if(seedTag instanceof StringTag stringSeedTag) {
                    return normalizeRawString(stringSeedTag.getValue());
                }
                if(seedTag != null) {
                    String genericSeedValue = normalizeRawString(seedTag.valueToString());
                    if(genericSeedValue != null && !genericSeedValue.isBlank()) {
                        return genericSeedValue;
                    }
                }
            }

            return "-";
        } catch (Exception ex) {
            System.out.println("Ha ocurrido un error accediendo a la seed del mundo: " + mundo.getWorldDir());
            return "-";
        }
    }

    public static SpawnPoint getSpawnPoint(World mundo) {
        try {
            CompoundTag data = getDataTag(mundo);
            if(data == null) return null;

            NumberTag<?> spawnXTag = readNumberTag(data, "SpawnX");
            NumberTag<?> spawnYTag = readNumberTag(data, "SpawnY");
            NumberTag<?> spawnZTag = readNumberTag(data, "SpawnZ");
            if(spawnXTag != null && spawnZTag != null) {
                int spawnY = spawnYTag != null ? spawnYTag.asInt() : 0;
                float angle = readFloat(data.get("SpawnAngle"), 0f);
                return new SpawnPoint(spawnXTag.asInt(), spawnY, spawnZTag.asInt(), angle);
            }

            CompoundTag spawnTag = data.getCompoundTag("spawn");
            if(spawnTag == null) return null;

            int[] spawnPos = spawnTag.getIntArray("pos");
            if(spawnPos != null && spawnPos.length >= 3) {
                float angle = readFloat(spawnTag.get("angle"), 0f);
                return new SpawnPoint(spawnPos[0], spawnPos[1], spawnPos[2], angle);
            }

            ListTag<?> spawnPosList = spawnTag.getListTag("pos");
            if(spawnPosList != null && spawnPosList.size() >= 3) {
                Tag<?> xTag = spawnPosList.get(0);
                Tag<?> yTag = spawnPosList.get(1);
                Tag<?> zTag = spawnPosList.get(2);
                if(xTag instanceof net.querz.nbt.tag.NumberTag<?> xNumber
                        && yTag instanceof net.querz.nbt.tag.NumberTag<?> yNumber
                        && zTag instanceof net.querz.nbt.tag.NumberTag<?> zNumber) {
                    float angle = readFloat(spawnTag.get("angle"), 0f);
                    return new SpawnPoint(xNumber.asInt(), yNumber.asInt(), zNumber.asInt(), angle);
                }
            }

            return null;
        } catch (Exception ex) {
            System.out.println("Ha ocurrido un error accediendo al spawn del mundo: " + mundo.getWorldDir());
            return null;
        }
    }

    public static String getGameMode(World mundo) {
        try {
            CompoundTag data = getDataTag(mundo);
            if(data == null) return null;

            NumberTag<?> gameTypeTag = readNumberTag(data, "GameType");
            if(gameTypeTag == null) return null;
            return formatGameMode(gameTypeTag.asInt());
        } catch (Exception ex) {
            return null;
        }
    }

    public static String getDifficulty(World mundo) {
        try {
            CompoundTag data = getDataTag(mundo);
            if(data == null) return null;

            NumberTag<?> difficultyTag = readNumberTag(data, "Difficulty");
            if(difficultyTag == null) return null;
            return formatDifficulty(difficultyTag.asInt());
        } catch (Exception ex) {
            return null;
        }
    }

    public static String getHardcore(World mundo) {
        Boolean value = readBooleanValue(mundo, "hardcore");
        if(value == null) return null;
        return value ? "✓" : "✗";
    }

    public static String getAllowCommands(World mundo) {
        Boolean value = readBooleanValue(mundo, "allowCommands");
        if(value == null) return null;
        return value ? "✓" : "✗";
    }

    public static String getDifficultyLocked(World mundo) {
        Boolean value = readBooleanValue(mundo, "DifficultyLocked");
        if(value == null) return null;
        return value ? "✓" : "✗";
    }

    public static String getWeatherSummary(World mundo) {
        try {
            CompoundTag data = getDataTag(mundo);
            if(data == null) return null;

            boolean thundering = readBoolean(data.get("thundering"));
            boolean raining = readBoolean(data.get("raining"));
            if(thundering) return "Tormenta";
            if(raining) return "Lluvia";
            return "Despejado";
        } catch (Exception ex) {
            return null;
        }
    }

    public static String getDayTime(World mundo) {
        try {
            CompoundTag data = getDataTag(mundo);
            if(data == null) return null;

            NumberTag<?> dayTimeTag = readNumberTag(data, "DayTime");
            if(dayTimeTag == null) return null;

            long absoluteTicks = Math.max(0L, dayTimeTag.asLong());
            long day = absoluteTicks / 24000L;
            long timeOfDay = Math.floorMod(absoluteTicks, 24000L);
            long hours = Math.floorMod((timeOfDay / 1000L) + 6L, 24L);
            long minutes = Math.round((timeOfDay % 1000L) * 60.0 / 1000.0);
            if(minutes == 60L) {
                hours = (hours + 1L) % 24L;
                minutes = 0L;
            }
            return String.format(Locale.ROOT, "Día %d - %02d:%02d", day + 1L, hours, minutes);
        } catch (Exception ex) {
            return null;
        }
    }

    public static String getDataPacksSummary(World mundo) {
        try {
            CompoundTag data = getDataTag(mundo);
            if(data == null) return null;

            CompoundTag dataPacks = data.getCompoundTag("DataPacks");
            if(dataPacks == null) return null;

            int enabled = getStringListSize(dataPacks.getListTag("Enabled"));
            int disabled = getStringListSize(dataPacks.getListTag("Disabled"));
            if(enabled == 0 && disabled == 0) return null;
            return enabled + " activos / " + disabled + " desactivados";
        } catch (Exception ex) {
            return null;
        }
    }

    public static String getGameRulesSummary(World mundo) {
        try {
            Map<String, String> gameRules = getGameRules(mundo);
            if(gameRules.isEmpty()) return null;

            String keepInventory = readStringValue(gameRules, "keepInventory");
            String daylight = readStringValue(gameRules, "doDaylightCycle");
            String mobSpawning = readStringValue(gameRules, "doMobSpawning");
            String mobGriefing = readStringValue(gameRules, "mobGriefing");

            StringBuilder summary = new StringBuilder();
            appendRuleSummary(summary, "Inventario", keepInventory);
            appendRuleSummary(summary, "Dia", daylight);
            appendRuleSummary(summary, "Mobs", mobSpawning);
            appendRuleSummary(summary, "Griefing", mobGriefing);

            if(summary.isEmpty()) {
                return gameRules.size() + " reglas";
            }
            summary.append(" (").append(gameRules.size()).append(" reglas)");
            return summary.toString();
        } catch (Exception ex) {
            return null;
        }
    }

    public static Map<String, String> getGameRules(World mundo) {
        try {
            CompoundTag data = getDataTag(mundo);
            if(data == null) return Map.of();

            CompoundTag gameRules = data.getCompoundTag("GameRules");
            if(gameRules == null || gameRules.size() == 0) return Map.of();

            Map<String, String> result = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            for (Map.Entry<String, Tag<?>> entry : gameRules.entrySet()) {
                String key = entry.getKey();
                if(key == null || key.isBlank()) continue;
                Tag<?> tag = entry.getValue();
                if(tag == null) continue;
                result.put(key, normalizeRuleValue(tag.valueToString()));
            }
            return result.isEmpty() ? Map.of() : new LinkedHashMap<>(result);
        } catch (Exception ex) {
            return Map.of();
        }
    }

    public static void showAllTags(World mundo){
        try{
            CompoundTag root = getRootTag(mundo);
            if (root != null) {
                CompoundTag data = root.getCompoundTag("Data");
                for (Map.Entry<String, Tag<?>> entry : data.entrySet()) {
                    var tag = entry.getValue();
                    String shownValue;

                    if (tag instanceof CompoundTag) {
                        shownValue = "{...}";
                    } else if (tag instanceof ListTag<?>) {
                        shownValue = "[...]";
                    } else {
                        shownValue = tag.valueToString();
                    }

                    System.out.println(
                        entry.getKey()
                        + " -> " + shownValue
                        + " -> " + tag.getClass().getSimpleName()
                    );
                }
            }
        } catch (Exception ex) {
            System.out.println("Ha ocurrido un error leyendo los tags del mundo: "+mundo.getWorldDir());
        }
    }   

    private static long readActiveTicks(CompoundTag data) {
        NumberTag<?> timeTag = readNumberTag(data, "Time");
        return timeTag == null ? 0L : Math.max(0L, timeTag.asLong());
    }

    private static String readLastPlayed(CompoundTag data) {
        NumberTag<?> timeTag = readNumberTag(data, "LastPlayed");
        return timeTag == null ? null : fromMStoDateString(timeTag.asLong());
    }

    private static String readVersionName(CompoundTag data) {
        CompoundTag versionTag = data == null ? null : data.getCompoundTag("Version");
        if(versionTag == null) return null;
        String versionName = versionTag.getString("Name");
        return versionName == null || versionName.isBlank() ? null : versionName;
    }

    private static String readDataVersion(CompoundTag data) {
        NumberTag<?> dataVersionTag = readNumberTag(data, "DataVersion");
        return dataVersionTag == null ? null : Integer.toString(dataVersionTag.asInt());
    }

    private static String readSeed(CompoundTag data) {
        if(data == null) return "-";
        NumberTag<?> randomSeedTag = readNumberTag(data, "RandomSeed");
        if(randomSeedTag != null) {
            return Long.toString(randomSeedTag.asLong());
        }
        CompoundTag worldGenSettings = data.getCompoundTag("WorldGenSettings");
        if(worldGenSettings != null) {
            Tag<?> seedTag = worldGenSettings.get("seed");
            if(seedTag instanceof LongTag longSeedTag) {
                return Long.toString(longSeedTag.asLong());
            }
            if(seedTag instanceof IntTag intSeedTag) {
                return Integer.toString(intSeedTag.asInt());
            }
            if(seedTag instanceof StringTag stringSeedTag) {
                return normalizeRawString(stringSeedTag.getValue());
            }
            if(seedTag != null) {
                String genericSeedValue = normalizeRawString(seedTag.valueToString());
                if(genericSeedValue != null && !genericSeedValue.isBlank()) {
                    return genericSeedValue;
                }
            }
        }
        return "-";
    }

    private static SpawnPoint readSpawnPoint(CompoundTag data) {
        if(data == null) return null;
        NumberTag<?> spawnXTag = readNumberTag(data, "SpawnX");
        NumberTag<?> spawnYTag = readNumberTag(data, "SpawnY");
        NumberTag<?> spawnZTag = readNumberTag(data, "SpawnZ");
        if(spawnXTag != null && spawnZTag != null) {
            int spawnY = spawnYTag != null ? spawnYTag.asInt() : 0;
            float angle = readFloat(data.get("SpawnAngle"), 0f);
            return new SpawnPoint(spawnXTag.asInt(), spawnY, spawnZTag.asInt(), angle);
        }

        CompoundTag spawnTag = data.getCompoundTag("spawn");
        if(spawnTag == null) return null;

        int[] spawnPos = spawnTag.getIntArray("pos");
        if(spawnPos != null && spawnPos.length >= 3) {
            float angle = readFloat(spawnTag.get("angle"), 0f);
            return new SpawnPoint(spawnPos[0], spawnPos[1], spawnPos[2], angle);
        }

        ListTag<?> spawnPosList = spawnTag.getListTag("pos");
        if(spawnPosList != null && spawnPosList.size() >= 3) {
            Tag<?> xTag = spawnPosList.get(0);
            Tag<?> yTag = spawnPosList.get(1);
            Tag<?> zTag = spawnPosList.get(2);
            if(xTag instanceof NumberTag<?> xNumber
                    && yTag instanceof NumberTag<?> yNumber
                    && zTag instanceof NumberTag<?> zNumber) {
                float angle = readFloat(spawnTag.get("angle"), 0f);
                return new SpawnPoint(xNumber.asInt(), yNumber.asInt(), zNumber.asInt(), angle);
            }
        }
        return null;
    }

    private static String readGameMode(CompoundTag data) {
        NumberTag<?> gameTypeTag = readNumberTag(data, "GameType");
        if(gameTypeTag == null) return null;
        return formatGameMode(gameTypeTag.asInt());
    }

    private static String readDifficulty(CompoundTag data) {
        NumberTag<?> difficultyTag = readNumberTag(data, "Difficulty");
        if(difficultyTag == null) return null;
        return formatDifficulty(difficultyTag.asInt());
    }

    private static String readBooleanLabel(CompoundTag data, String key) {
        Tag<?> tag = data == null || key == null ? null : data.get(key);
        if(tag == null) return null;
        return readBoolean(tag) ? "✓" : "✗";
    }

    private static String readWeatherSummary(CompoundTag data) {
        if(data == null) return null;
        boolean thundering = readBoolean(data.get("thundering"));
        boolean raining = readBoolean(data.get("raining"));
        if(thundering) return "Tormenta";
        if(raining) return "Lluvia";
        return "Despejado";
    }

    private static String readDayTime(CompoundTag data) {
        NumberTag<?> dayTimeTag = readNumberTag(data, "DayTime");
        if(dayTimeTag == null) return null;

        long absoluteTicks = Math.max(0L, dayTimeTag.asLong());
        long day = absoluteTicks / 24000L;
        long timeOfDay = Math.floorMod(absoluteTicks, 24000L);
        long hours = Math.floorMod((timeOfDay / 1000L) + 6L, 24L);
        long minutes = Math.round((timeOfDay % 1000L) * 60.0 / 1000.0);
        if(minutes == 60L) {
            hours = (hours + 1L) % 24L;
            minutes = 0L;
        }
        return String.format(Locale.ROOT, "Día %d - %02d:%02d", day + 1L, hours, minutes);
    }

    private static String readDataPacksSummary(CompoundTag data) {
        CompoundTag dataPacks = data == null ? null : data.getCompoundTag("DataPacks");
        if(dataPacks == null) return null;

        int enabled = getStringListSize(dataPacks.getListTag("Enabled"));
        int disabled = getStringListSize(dataPacks.getListTag("Disabled"));
        if(enabled == 0 && disabled == 0) return null;
        return enabled + " activos / " + disabled + " desactivados";
    }

    private static Map<String, String> readGameRules(CompoundTag data) {
        CompoundTag gameRules = data == null ? null : data.getCompoundTag("GameRules");
        if(gameRules == null || gameRules.size() == 0) return Map.of();

        Map<String, String> result = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Map.Entry<String, Tag<?>> entry : gameRules.entrySet()) {
            String key = entry.getKey();
            if(key == null || key.isBlank()) continue;
            Tag<?> tag = entry.getValue();
            if(tag == null) continue;
            result.put(key, normalizeRuleValue(tag.valueToString()));
        }
        return result.isEmpty() ? Map.of() : new LinkedHashMap<>(result);
    }

    private static String readGameRulesSummary(Map<String, String> gameRules) {
        if(gameRules == null || gameRules.isEmpty()) return null;

        String keepInventory = readStringValue(gameRules, "keepInventory");
        String daylight = readStringValue(gameRules, "doDaylightCycle");
        String mobSpawning = readStringValue(gameRules, "doMobSpawning");
        String mobGriefing = readStringValue(gameRules, "mobGriefing");

        StringBuilder summary = new StringBuilder();
        appendRuleSummary(summary, "Inventario", keepInventory);
        appendRuleSummary(summary, "Dia", daylight);
        appendRuleSummary(summary, "Mobs", mobSpawning);
        appendRuleSummary(summary, "Griefing", mobGriefing);

        if(summary.isEmpty()) {
            return gameRules.size() + " reglas";
        }
        summary.append(" (").append(gameRules.size()).append(" reglas)");
        return summary.toString();
    }

    private static void appendRuleSummary(StringBuilder summary, String label, String value) {
        if(value == null || value.isBlank()) return;
        if(!summary.isEmpty()) summary.append(" | ");
        summary.append(label).append(": ").append(normalizeRuleValue(value));
    }

    private static String normalizeRuleValue(String value) {
        value = normalizeRawString(value);
        if("true".equalsIgnoreCase(value)) return "✓";
        if("false".equalsIgnoreCase(value)) return "✗";
        return value;
    }

    private static String normalizeRawString(String value) {
        if(value == null) return null;
        String normalized = value.trim();
        if(normalized.length() >= 2 && normalized.startsWith("\"") && normalized.endsWith("\"")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        return normalized;
    }

    private static String readStringValue(Map<String, String> values, String key) {
        if(values == null || key == null) return null;
        return values.get(key);
    }

    private static int getStringListSize(ListTag<?> listTag) {
        return listTag == null ? 0 : listTag.size();
    }

    private static String readStringValue(CompoundTag compound, String key) {
        if(compound == null || key == null) return null;
        Tag<?> tag = compound.get(key);
        if(tag instanceof StringTag stringTag) return stringTag.getValue();
        return tag == null ? null : tag.valueToString();
    }

    private static NumberTag<?> readNumberTag(CompoundTag compound, String key) {
        if(compound == null || key == null) return null;
        Tag<?> tag = compound.get(key);
        return tag instanceof NumberTag<?> numberTag ? numberTag : null;
    }

    private static String formatGameMode(int gameType) {
        return switch (gameType) {
            case 0 -> "Supervivencia";
            case 1 -> "Creativo";
            case 2 -> "Aventura";
            case 3 -> "Espectador";
            default -> Integer.toString(gameType);
        };
    }

    private static String formatDifficulty(int difficulty) {
        return switch (difficulty) {
            case 0 -> "Pacifica";
            case 1 -> "Facil";
            case 2 -> "Normal";
            case 3 -> "Dificil";
            default -> Integer.toString(difficulty);
        };
    }

    private static Boolean readBooleanValue(World mundo, String key) {
        try {
            CompoundTag data = getDataTag(mundo);
            if(data == null) return null;
            Tag<?> tag = data.get(key);
            if(tag == null) return null;
            return readBoolean(tag);
        } catch (Exception ex) {
            return null;
        }
    }

    private static boolean readBoolean(Tag<?> tag) {
        if(tag instanceof NumberTag<?> numberTag) {
            return numberTag.asInt() != 0;
        }
        if(tag instanceof StringTag stringTag) {
            return Boolean.parseBoolean(stringTag.getValue());
        }
        return false;
    }

    private static float readFloat(Tag<?> tag, float defaultValue) {
        if(tag instanceof NumberTag<?> numberTag) {
            return numberTag.asFloat();
        }
        if(tag instanceof StringTag stringTag) {
            try {
                return Float.parseFloat(stringTag.getValue());
            } catch (NumberFormatException ignored) {
            }
        }
        return defaultValue;
    }

    // Solo necesitamos X y Z para la preview, pero para la UI tenemos disponible el spawn completo.
    public record WorldMetadata(
            long activeTicks,
            String lastPlayed,
            String versionName,
            String dataVersion,
            String seed,
            SpawnPoint spawnPoint,
            String gameMode,
            String difficulty,
            String difficultyLocked,
            String weatherSummary,
            String dayTime,
            String hardcore,
            String allowCommands,
            String dataPacksSummary,
            String gameRulesSummary,
            Map<String, String> gameRules
    ) {
        public WorldMetadata {
            gameRules = gameRules == null || gameRules.isEmpty() ? Map.of() : new LinkedHashMap<>(gameRules);
        }

        static WorldMetadata empty() {
            return new WorldMetadata(
                    0L,
                    null,
                    null,
                    null,
                    "-",
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    Map.of()
            );
        }
    }

    public record SpawnPoint(int x, int y, int z, float angle) {
        public SpawnPoint(int x, int z) {
            this(x, 0, z, 0f);
        }
    }

    public enum WorldStorageLayout {
        LEGACY,
        NAMESPACED
    }
}
