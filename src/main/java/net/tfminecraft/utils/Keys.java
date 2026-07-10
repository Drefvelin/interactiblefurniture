package net.tfminecraft.utils;

import org.bukkit.NamespacedKey;
import net.tfminecraft.InteractibleFurniture;

public final class Keys {
    public static NamespacedKey furnitureEntity() {
        return new NamespacedKey(InteractibleFurniture.getInstance(), "furniture_entity");
    }

    private Keys() {}
}
