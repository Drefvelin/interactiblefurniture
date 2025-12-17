package net.tfminecraft;

import java.io.File;

import org.bukkit.plugin.java.JavaPlugin;

import net.tfminecraft.database.Database;
import net.tfminecraft.manager.FurnitureManager;

public class InteractibleFurniture extends JavaPlugin{
    private final FurnitureManager furnitureManager = new FurnitureManager();

    @Override
    public void onEnable() {
        createConfigs();
        loadConfigs();
        // register our furniture manager
        getServer().getPluginManager().registerEvents(furnitureManager, this);
        furnitureManager.start();
        furnitureManager.loadAlreadyLoadedChunks();
        getLogger().info("InteractibleFurniture has been enabled!");
    }

    @Override
    public void onDisable() {
        furnitureManager.deleteCarried();
        furnitureManager.saveAllLoadedChunks();
        getLogger().info("InteractibleFurniture has been disabled.");
    }

    public void registerListeners() {
        // kept for compatibility; specific listeners are registered in onEnable
    }

    public void createConfigs() {
    String[] files = {
        "furniture/example.yml",
        "sounds.yml"
        };
		for(String s : files) {
			File newConfigFile = new File(getDataFolder(), s);
	        if (!newConfigFile.exists()) {
	        	newConfigFile.getParentFile().mkdirs();
	            saveResource(s, false);
	        }
		}
	}

    public void loadConfigs() {
        // load furniture definitions
        for(File file : new File(getDataFolder(), "furniture").listFiles()) {
            if(file.isFile() && file.getName().endsWith(".yml")) {
                new net.tfminecraft.loaders.FurnitureLoader().load(file);
            }
        }
        new net.tfminecraft.loaders.SoundLoader().load(new File(getDataFolder(), "sounds.yml"));
	}

    public static InteractibleFurniture getInstance() {
        return JavaPlugin.getPlugin(InteractibleFurniture.class);
    }

    public FurnitureManager getFurnitureManager() {
        return furnitureManager;
    }
}
