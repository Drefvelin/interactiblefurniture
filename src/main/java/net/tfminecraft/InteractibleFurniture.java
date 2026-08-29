package net.tfminecraft;

import java.io.File;

import org.bukkit.plugin.java.JavaPlugin;

import net.tfminecraft.command.IfCommand;
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

        IfCommand ifCommand = new IfCommand();
        var ifCmd = getCommand("if");
        if (ifCmd != null) {
            ifCmd.setExecutor(ifCommand);
            ifCmd.setTabCompleter(ifCommand);
        } else {
            getLogger().severe("Command 'if' missing from plugin.yml");
        }

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
        "furniture/magic.yml",
        "furniture/cooking.yml",
        "furniture/shelf.yml",
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
        File folder = new File(getDataFolder(), "furniture");
        if (folder.exists() && folder.isDirectory()) {
            File[] files = folder.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile() && file.getName().endsWith(".yml")) {
                        new net.tfminecraft.loaders.FurnitureLoader().load(file);
                    }
                }
            }
        }
        File sounds = new File(getDataFolder(), "sounds.yml");
        if (sounds.exists()) {
            new net.tfminecraft.loaders.SoundLoader().load(sounds);
        }
    }

    public boolean reloadAll() {
        try {
            net.tfminecraft.loaders.FurnitureLoader.getMap().clear();
            net.tfminecraft.loaders.SoundLoader.getMap().clear();
            loadConfigs();
            getLogger().info("InteractibleFurniture configs reloaded ("
                    + net.tfminecraft.loaders.FurnitureLoader.getMap().size() + " types).");
            return true;
        } catch (Exception ex) {
            getLogger().severe("Reload failed: " + ex.getMessage());
            return false;
        }
    }

    public static InteractibleFurniture getInstance() {
        return JavaPlugin.getPlugin(InteractibleFurniture.class);
    }

    public FurnitureManager getFurnitureManager() {
        return furnitureManager;
    }
}
