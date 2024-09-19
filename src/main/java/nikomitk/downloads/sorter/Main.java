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
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {

    private static final Logger LOG = LogManager.getLogger("Download-Sorter");
    private static final Gson GSON = new Gson();
    private static final String HOME_DIR = System.getProperty("user.home");

    private static Map<String, String> fileTypeMap;
    private static String completeSortDir;

    public static void main(String[] args) {
        LOG.info("Sorting files!");

        String sortDir = "/Downloads";

        completeSortDir = HOME_DIR + sortDir;

        fileTypeMap = createMapFromConfigFile();

        try {
            Set<String> filesInDir = loadFilesFromDir(sortDir);

            Set<String> wantedFiles = extractWantedFiles(filesInDir);

            wantedFiles.forEach(Main::moveFile);
        } catch (IOException e) {
            LOG.error(e);
        }

    }

    /**
     * Reads the sort-config.json file into a Map and normalizes the paths.
     *
     * @return Map of file endings and corresponding directory
     */
    private static Map<String, String> createMapFromConfigFile() {
        InputStream is = Main.class.getResourceAsStream("/sort-config.json");
        assert is != null;
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        Map<String, String> tempMap = GSON.fromJson(reader, Map.class);

        tempMap.entrySet().forEach(entry -> {
            if (entry.getValue().startsWith("~")) {
                entry.setValue(HOME_DIR + entry.getValue().substring(1));
            } else if (entry.getValue().startsWith("/")) {
                // nothing, path is fine
            } else {
                entry.setValue(completeSortDir + File.separator + entry.getValue());
            }
        });

        tempMap.values().forEach(LOG::debug);
        return tempMap;
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
            return stream.filter(file -> !Files.isDirectory(file)).map(Path::getFileName).map(Path::toString).collect(Collectors.toSet());
        }
    }

    /**
     * Filters out all files which either don't have a specified file ending or are newer than 30 days.
     *
     * @param files Set of filenames which should be filtered.
     * @return Set of filtered filenames.
     */
    private static Set<String> extractWantedFiles(Set<String> files) {
        return files.stream().filter(Main::fileTypeWanted).filter(Main::fileOldEnough).collect(Collectors.toSet());
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
        return lastModified.atZone(ZoneId.systemDefault()).toLocalDateTime().plusDays(30).isBefore(LocalDateTime.now());
    }

    /**
     * Moves a single file into the directory that was specified for this file ending
     */
    private static void moveFile(String fileName) {
        File file = new File(completeSortDir + File.separator + fileName);
        String newDir = fileTypeMap.get(fileName.substring(fileName.lastIndexOf('.') + 1));
        String newFileName = newDir + File.separator + fileName;
        boolean move = file.renameTo(new File(newFileName));
        LOG.info("Moving file: {} to {}. {}", fileName, newFileName, (move ? "Success" : "Failure"));
    }
}