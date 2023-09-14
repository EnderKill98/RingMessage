package me.enderkill98.ringmessage.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.util.regex.Pattern;

public class JSONConfigUtil {

    /**
     * Allow using a String for Patterns
     */
    private static class PatternTypeAdapter extends TypeAdapter<Pattern> {

        @Override
        public void write(JsonWriter out, Pattern value) throws IOException {
            out.value(value.toString());
        }

        @Override
        public Pattern read(JsonReader in) throws IOException {
            return Pattern.compile(in.nextString());
        }
    }

    private static Gson GSON = new GsonBuilder()
            .registerTypeAdapter(Pattern.class, new PatternTypeAdapter())
            .setPrettyPrinting().create();

    public static File resolve(String configName) {
        File configModFolder = new File(FabricLoader.getInstance().getConfigDir().toFile(), "ringmessage");
        File configFile = new File(configModFolder, configName);
        return configFile;
    }

    public static boolean exists(String configFileName) {
        return resolve(configFileName).exists();
    }

    public static <T> T load(Class<T> configClass, String configFileName) {
        try {
            File configFile = resolve(configFileName);
            if(!configFile.getParentFile().exists()) configFile.getParentFile().mkdirs();
            if(!configFile.exists()) {
                return GSON.fromJson("{}", configClass);
            }else {
                return GSON.fromJson(new FileReader(configFile), configClass);
            }
        }catch (FileNotFoundException ex) {
            ex.printStackTrace();
            return GSON.fromJson("{}", configClass);
        }
    }

    public static boolean save(Object config, String configFileName) {
        File configFile = resolve(configFileName);
        if(!configFile.getParentFile().exists()) configFile.getParentFile().mkdirs();
        String json = GSON.toJson(config);
        try {
            FileWriter writer = new FileWriter(configFile, false);
            writer.write(json);
            writer.flush();
            writer.close();
            return true;
        }catch (IOException ex) {
            ex.printStackTrace();
            return false;
        }
    }

}
