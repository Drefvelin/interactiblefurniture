package net.tfminecraft.furniture.data;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.configuration.ConfigurationSection;

import net.tfminecraft.enums.Display;

public class FurnitureDataContainer {
    private ModelData modelData;
    private ModelData modelOverride;
    private Map<String, Object> variables = new HashMap<>();

    public FurnitureDataContainer(ModelData modelData) {
        this.modelData = new ModelData(modelData);
    }

    public FurnitureDataContainer(ConfigurationSection config) {
        String displayStr = config.getString("display");
        String modelStr = config.getString("model");
        if (displayStr != null && modelStr != null) {
            try {
                Display display = Display.valueOf(displayStr.toUpperCase());
                this.modelData = new ModelData(display, modelStr);
            } catch (Exception e) {
                e.printStackTrace();
                this.modelData = new ModelData(Display.ITEM_DISPLAY, "v.paper");
            }
        }
    }

    public ModelData getCurrentModelData() {
        return modelOverride != null ? modelOverride : modelData;
    }

    public ModelData getModelData() {
        return modelData;
    }

    public ModelData getModelOverride() {
        return modelOverride;
    }

    public void setModelOverride(ModelData modelOverride) {
        this.modelOverride = modelOverride;
    }

    public Map<String, Object> getVariables() {
        return variables;
    }

    public void setVariables(Map<String, Object> variables) {
        this.variables = variables;
    }
}
