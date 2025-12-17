package net.tfminecraft.loaders;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import me.Plugins.TLibs.Interface.LoaderInterface;

public class SoundLoader implements LoaderInterface{
    private static final HashMap<String, String> map = new HashMap<>();

    public static HashMap<String, String> getMap() {
        return map;
    }

    @Override
    public void load(File configFile) {
        FileConfiguration config = new YamlConfiguration();
        try {
            config.load(configFile);
        } catch (IOException | InvalidConfigurationException e) {
            e.printStackTrace();
            return;
        }

        for(String s : config.getStringList("sounds")) {
            String[] parts = s.split(" ");
            if(parts.length != 2) continue;
            map.put(parts[0], parts[1]);
        }
    }

    public static boolean has(String path) {
        return map.containsKey(path);
    }

    public static String getByString(String id) {
        return map.get(id);
    }
}
