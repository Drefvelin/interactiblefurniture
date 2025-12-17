package net.tfminecraft.loaders;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Set;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import me.Plugins.TLibs.Interface.LoaderInterface;
import net.tfminecraft.furniture.FurnitureType;

/**
 * Loads furniture definitions from a YAML file following the same pattern as other loaders in repo.
 */
public class FurnitureLoader implements LoaderInterface {
    private static final HashMap<String, FurnitureType> map = new HashMap<>();

    public static HashMap<String, FurnitureType> getMap() {
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

        Set<String> keys = config.getKeys(false);
        for (String key : keys) {
            if (!config.isConfigurationSection(key)) continue;
            FurnitureType ft = new FurnitureType(key, config.getConfigurationSection(key));
            map.put(key, ft);
        }
    }

    public static FurnitureType getByString(String id) {
        return map.get(id);
    }
}
