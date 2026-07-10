package net.tfminecraft.furniture.data;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.util.Vector;

public class InteractionData {
    private final float width;
    private final float height;
    private final Vector offset;

    public InteractionData(ConfigurationSection config) {
        this.width = (float) config.getDouble("width", 1.5);
        this.height = (float) config.getDouble("height", 2.0);
        if (config.isConfigurationSection("offset")) {
            ConfigurationSection off = config.getConfigurationSection("offset");
            this.offset = new Vector(
                    off.getDouble("x", 0),
                    off.getDouble("y", 0.5),
                    off.getDouble("z", 0)
            );
        } else {
            this.offset = new Vector(0, 0.5, 0);
        }
    }

    public InteractionData(InteractionData other) {
        this.width = other.width;
        this.height = other.height;
        this.offset = other.offset.clone();
    }

    public float getWidth() { return width; }
    public float getHeight() { return height; }
    public Vector getOffset() { return offset; }
}
