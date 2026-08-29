package net.tfminecraft.utils;

import org.bukkit.NamespacedKey;
import net.tfminecraft.InteractibleFurniture;

public final class Keys {
    public static NamespacedKey furnitureEntity() {
        return new NamespacedKey(InteractibleFurniture.getInstance(), "furniture_entity");
    }

    public static NamespacedKey furnitureDisplay() {
        return new NamespacedKey(InteractibleFurniture.getInstance(), "furniture_display");
    }

    public static NamespacedKey furnitureSlot() {
        return new NamespacedKey(InteractibleFurniture.getInstance(), "furniture_slot");
    }

    private Keys() {}
}
