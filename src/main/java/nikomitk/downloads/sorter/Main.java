package nikomitk.downloads.sorter;


import com.google.gson.Gson;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {

    private static final Logger LOG = LogManager.getLogger("Download-Sorter");
    private static final Gson GSON = new Gson();
    private static final String HOME_DIR = System.getProperty("user.home");
    private static final Map<String, String> fileTypeMap = new HashMap<>();
    private static final String DEFAULT_CONFIG_FILE = "/.config/downloads-sorter/sort-config.json";

    private static String configFile = HOME_DIR + DEFAULT_CONFIG_FILE;
    private static String sortDir = "/Downloads";
    private static String completeSortDir;

    private static int ageToSort = 30;

    private static int movedFiles;

    public static void main(String[] args) {

        LOG.info("Sorting files!");

        handleArgs(args);

        createMapFromConfigFile();

        completeSortDir = HOME_DIR + sortDir;

        normalizePaths();

        try {
            Set<String> filesInDir = loadFilesFromDir(sortDir);

            Set<String> wantedFiles = extractWantedFiles(filesInDir);

            wantedFiles.forEach(Main::moveFile);

            LOG.info("Moved {} files.", movedFiles);
        } catch (IOException e) {
            LOG.error(e);
        }

    }

    /**
     * Handles CLI Arguments.
     */
    private static void handleArgs(String[] args) {

        for (String arg : args) {
            LOG.debug("Handling argument: {}", arg);
            if (arg.startsWith("-p=")) {
                sortDir = arg.substring("-p=".length());

            } else if (arg.startsWith("-ft=")) {
                String[] split = arg.substring("-ft=".length()).split(":");
                fileTypeMap.put(split[0], split[1]);
                LOG.debug("Added file type: {} with directory: {}", split[0], split[1]);

            } else if (arg.startsWith("-a=")) {
                try {
                    ageToSort = Integer.parseInt(arg.substring("-a=".length()));
                    if (ageToSort < 0) {
                        ageToSort = 30;
                        throw new NumberFormatException();
                    }
                    LOG.info("Now considering files older than {} day(s).", ageToSort);
                } catch (NumberFormatException e) {
                    LOG.error("Invalid number. Must be a positive integer. Default value {} is used.", ageToSort);
                }
            } else if (arg.startsWith("-cf=")) {
                configFile = arg.substring("-cf=".length());
                LOG.info("Using config file: {}", configFile);

            } else {
                LOG.warn("Unknown argument: {}", arg);
            }
        }
    }

    /**
     * Reads the sort-config.json file into a Map.
     */
    private static void createMapFromConfigFile() {

        InputStream is = createReadConfigFile();

        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        Map<String, String> tempMap = GSON.fromJson(reader, Map.class);

        fileTypeMap.putAll(tempMap);
    }

    /**
     * Reads the config file. If the specified file doesn't exist, the default config file is used. If that doesn't exist
     * either, it is created from the default config file in the resources.
     *
     * @return InputStream of the config file.
     */
    private static InputStream createReadConfigFile() {

        Path configPath = Paths.get(configFile);
        if (!Files.exists(configPath) && !configFile.equals(HOME_DIR + DEFAULT_CONFIG_FILE)) {
            LOG.warn("Config file not found. Using default config file.");
            configFile = DEFAULT_CONFIG_FILE;
            configPath = Paths.get(HOME_DIR + configFile);
        }
        if (!Files.exists(configPath)) {
            try {
                LOG.debug("Default config file not found. Creating it.");
                Files.createDirectories(configPath.getParent());
                Files.copy(readResourceConfigFile(), configPath);
            } catch (IOException e) {
                // This shouldn't happen, but there are some edge cases where it could.
                LOG.error("Couldn't create config file: ", e);
                return readResourceConfigFile();
            }
        }
        try {
            return new FileInputStream(configPath.toFile());
        } catch (FileNotFoundException e) {
            // This should never happen
            throw new RuntimeException(e);
        }
    }

    /**
     * Reads the default config file.
     *
     * @return InputStream of the default config file.
     */
    private static InputStream readResourceConfigFile() {

        return Main.class.getResourceAsStream("/sort-config.json");
    }

    /**
     * Normalizes the paths in the fileTypeMap.
     */
    private static void normalizePaths() {

        fileTypeMap.entrySet().forEach(entry -> {
            if (entry.getValue().startsWith("~")) {
                entry.setValue(HOME_DIR + entry.getValue().substring(1));
            } else if (entry.getValue().startsWith("/")) {
                // nothing, path is fine
            } else {
                entry.setValue(completeSortDir + File.separator + entry.getValue());
            }
        });

        LOG.debug("Sorting into directories:");
        fileTypeMap.forEach((key, value) -> LOG.debug("*.{}: {}", key, value));
    }

    /**
     * Gathers all filenames that are in the directory which is to be sorted.
     *
     * @param dir the directory to sort.
     * @return Set of filenames.
     * @throws IOException if dir doesn't exist.
     */
    private static Set<String> loadFilesFromDir(String dir) throws IOException {

        try (Stream<Path> stream = Files.list(Paths.get(HOME_DIR + dir))) {
            Set<String> files = stream.filter(file -> !Files.isDirectory(file)).map(Path::getFileName).map(Path::toString).collect(Collectors.toSet());

            LOG.debug("Files found: {}", files.size());
            files.forEach(Main::logFilename);

            return files;
        }
    }

    /**
     * Filters out all files which either don't have a specified file ending or are newer than 30 days.
     *
     * @param files Set of filenames which should be filtered.
     * @return Set of filtered filenames.
     */
    private static Set<String> extractWantedFiles(Set<String> files) {

        Set<String> wantedFiles = files.stream().filter(Main::fileTypeWanted).filter(Main::fileOldEnough).collect(Collectors.toSet());
        LOG.info("Wanted files found: {}", wantedFiles.size());
        wantedFiles.forEach(Main::logFilename);
        return wantedFiles;
    }

    /**
     * Extracts file endings from filename and compares it to the specified file endings in the fileTypeMap.
     *
     * @return if the file ending is present in the fileTypeMap.
     */
    private static boolean fileTypeWanted(String fileName) {

        fileName = fileName.substring(fileName.lastIndexOf('.') + 1);
        return fileTypeMap.containsKey(fileName);
    }

    /**
     * Checks if the file is older than 30 days and thus should be sorted.
     *
     * @return if the file is older than 30 days.
     */
    private static boolean fileOldEnough(String fileName) {

        Instant lastModified = Instant.ofEpochMilli(new File(completeSortDir + File.separator + fileName).lastModified());
        return lastModified.atZone(ZoneId.systemDefault()).toLocalDateTime().plusDays(ageToSort).isBefore(LocalDateTime.now());
    }

    private static void logFilename(String fileName) {

        LOG.debug("- {}", fileName);
    }

    /**
     * Moves a single file into the directory that was specified for this file ending
     */
    private static void moveFile(String fileName) {

        File file = new File(completeSortDir + File.separator + fileName);
        String newDir = fileTypeMap.get(fileName.substring(fileName.lastIndexOf('.') + 1));
        File dir = new File(newDir);
        if (!dir.exists()) {
            try {
                dir.mkdirs();
            } catch (SecurityException e) {
                LOG.error(e);
            }
        }
        String newFileName = newDir + File.separator + fileName;
        boolean move = file.renameTo(new File(newFileName));
        if (move) movedFiles++;
        LOG.info("Moving file: {} to {}. {}", fileName, newFileName, (move ? "Success :)" : "Failure :("));
    }

}