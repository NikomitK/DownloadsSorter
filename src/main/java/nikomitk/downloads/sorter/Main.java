package nikomitk.downloads.sorter;


import com.google.gson.Gson;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Map;

public class Main {

    static final Logger LOG = LogManager.getLogger("Download-Sorter");
    private static final Gson GSON = new Gson();

    private static Map<String, String> fileTypeMap;

    public static void main(String[] args) {
        LOG.info("Sorting files!");

        fileTypeMap = createMapFromConfigFile();

    }

    private static Map<String, String> createMapFromConfigFile() {
        InputStream is = Main.class.getResourceAsStream("/sort-config.json");
        assert is != null;
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        return GSON.fromJson(reader, Map.class);
    }
}