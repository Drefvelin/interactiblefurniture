package net.tfminecraft.manager.handlers;

import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.persistence.PersistentDataType;

import net.tfminecraft.furniture.Furniture;
import net.tfminecraft.furniture.data.InteractionData;
import net.tfminecraft.utils.Keys;

public class InteractionHandler {

    public static void spawnInteraction(Furniture furniture) {
        if (furniture.getType() == null || !furniture.getType().hasInteraction()) return;
        if (furniture.getInteractionEntityId() != null) {
            Entity existing = Bukkit.getEntity(furniture.getInteractionEntityId());
            if (existing != null && !existing.isDead()) return;
        }

        InteractionData data = furniture.getType().getInteractionData();
        Location loc = computeInteractionLocation(furniture, data);

        Interaction interaction = loc.getWorld().spawn(loc, Interaction.class, entity -> {
            entity.setInteractionWidth(data.getWidth());
            entity.setInteractionHeight(data.getHeight());
            entity.setResponsive(true);
            entity.setPersistent(true);
            entity.getPersistentDataContainer().set(
                    Keys.furnitureEntity(),
                    PersistentDataType.STRING,
                    furniture.getEntityId().toString()
            );
        });

        furniture.setInteractionEntityId(interaction.getUniqueId());
    }

    public static void removeInteraction(Furniture furniture) {
        UUID id = furniture.getInteractionEntityId();
        if (id == null) return;
        Entity entity = Bukkit.getEntity(id);
        if (entity != null) entity.remove();
        furniture.setInteractionEntityId(null);
    }

    public static void updateInteractionPosition(Furniture furniture) {
        if (furniture.getType() == null || !furniture.getType().hasInteraction()) return;
        UUID id = furniture.getInteractionEntityId();
        if (id == null) {
            spawnInteraction(furniture);
            return;
        }
        Entity entity = Bukkit.getEntity(id);
        if (entity == null || entity.isDead()) {
            spawnInteraction(furniture);
            return;
        }
        InteractionData data = furniture.getType().getInteractionData();
        entity.teleport(computeInteractionLocation(furniture, data));
    }

    public static Furniture resolveFurniture(Interaction interaction, Map<UUID, Furniture> placed) {
        String raw = interaction.getPersistentDataContainer().get(Keys.furnitureEntity(), PersistentDataType.STRING);
        if (raw == null) return null;
        try {
            UUID displayId = UUID.fromString(raw);
            return placed.get(displayId);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Location computeInteractionLocation(Furniture furniture, InteractionData data) {
        Location base = furniture.getLoc().clone();
        base.add(data.getOffset());
        return base;
    }
}
