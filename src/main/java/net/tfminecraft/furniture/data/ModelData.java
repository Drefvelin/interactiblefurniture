package net.tfminecraft.furniture.data;

import net.tfminecraft.enums.Display;

public class ModelData {
    private Display display;
    private String model;

    public ModelData(Display display, String model) {
        this.display = display;
        this.model = model;
    }

    public ModelData(ModelData other) {
        this.display = other.display;
        this.model = other.model;
    }

    public Display getDisplay() {
        return display;
    }

    public String getModel() {
        return model;
    }
}
