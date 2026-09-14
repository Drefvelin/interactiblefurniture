package net.tfminecraft.debug;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;

import net.tfminecraft.InteractibleFurniture;
import net.tfminecraft.furniture.Furniture;
import net.tfminecraft.furniture.FurnitureType;
import net.tfminecraft.furniture.PlacedFurnitureSlot;
import net.tfminecraft.furniture.SlotDefinition;
import net.tfminecraft.furniture.data.DisplayData;
import net.tfminecraft.furniture.data.InteractionData;
import net.tfminecraft.manager.handlers.InteractionHandler;

public final class InteractionDebugRenderer {

    private static final double SEARCH_RADIUS = 16.0;
    private static final double SEARCH_RADIUS_SQ = SEARCH_RADIUS * SEARCH_RADIUS;
    private static final double PARTICLE_SPACING = 0.25;
    private static final double SLOT_BOX_HALF = 0.15;
    private static final float DUST_SIZE = 1.0f;

    private static final Color COLOR_INTERACTION = Color.fromRGB(0, 255, 255);
    private static final Color COLOR_SLOT_INTERACTIBLE = Color.fromRGB(0, 255, 0);
    private static final Color COLOR_SLOT_OTHER = Color.fromRGB(160, 160, 160);
    private static final Color COLOR_BARRIER = Color.fromRGB(255, 0, 0);
    private static final Color COLOR_ORIGIN = Color.fromRGB(0, 120, 255);

    private InteractionDebugRenderer() {
    }

    public static void renderFor(Player player) {
        Location playerLoc = player.getLocation();
        if (playerLoc.getWorld() == null) {
            return;
        }

        Set<UUID> rendered = new HashSet<>();
        for (Furniture furniture : InteractibleFurniture.getInstance()
                .getFurnitureManager()
                .getPlacedFurniture()
                .values()) {
            renderFurnitureTree(player, playerLoc, furniture, rendered);
        }
    }

    private static void renderFurnitureTree(Player player, Location playerLoc, Furniture furniture, Set<UUID> rendered) {
        if (furniture == null || furniture.isCarried()) {
            return;
        }
        UUID entityId = furniture.getEntityId();
        if (entityId == null || rendered.contains(entityId)) {
            return;
        }

        Location loc = furniture.getLoc();
        if (loc == null || loc.getWorld() == null || !loc.getWorld().equals(playerLoc.getWorld())) {
            return;
        }
        if (!loc.getChunk().isLoaded()) {
            return;
        }
        if (loc.distanceSquared(playerLoc) > SEARCH_RADIUS_SQ) {
            return;
        }

        rendered.add(entityId);
        renderFurniture(player, furniture);

        for (PlacedFurnitureSlot placed : furniture.getActiveFurnitureSlots().values()) {
            Furniture nested = placed.getNested();
            if (nested != null) {
                renderFurnitureTree(player, playerLoc, nested, rendered);
            }
        }
    }

    private static void renderFurniture(Player player, Furniture furniture) {
        FurnitureType type = furniture.getType();
        if (type == null) {
            return;
        }

        if (type.hasInteraction() && !type.isSolid()) {
            renderInteractionBox(player, furniture, type);
        }

        if (type.isSolid()) {
            for (Block block : furniture.getBarrierBlocks()) {
                if (block == null || !block.getChunk().isLoaded()) {
                    continue;
                }
                Location blockLoc = block.getLocation();
                drawAabb(player, blockLoc.getX(), blockLoc.getY(), blockLoc.getZ(),
                        blockLoc.getX() + 1, blockLoc.getY() + 1, blockLoc.getZ() + 1, COLOR_BARRIER);
            }
        } else {
            furniture.getOriginBlockLocation().ifPresent(origin -> {
                if (!origin.getChunk().isLoaded()) {
                    return;
                }
                drawAabb(player, origin.getX(), origin.getY(), origin.getZ(),
                        origin.getX() + 1, origin.getY() + 1, origin.getZ() + 1, COLOR_ORIGIN);
            });
        }

        Entity displayEntity = Bukkit.getEntity(furniture.getEntityId());
        if (!(displayEntity instanceof ItemDisplay display)) {
            return;
        }

        DisplayData displayData = new DisplayData();
        for (SlotDefinition slot : type.getSlots().values()) {
            Location slotLoc = slot.computeDisplayLocation(furniture.getLoc(), display, displayData);
            if (slotLoc == null || slotLoc.getWorld() == null) {
                continue;
            }
            Color color = slot.isInteractible() ? COLOR_SLOT_INTERACTIBLE : COLOR_SLOT_OTHER;
            double h = SLOT_BOX_HALF;
            drawAabb(player,
                    slotLoc.getX() - h, slotLoc.getY() - h, slotLoc.getZ() - h,
                    slotLoc.getX() + h, slotLoc.getY() + h, slotLoc.getZ() + h,
                    color);
        }
    }

    private static void renderInteractionBox(Player player, Furniture furniture, FurnitureType type) {
        float width;
        float height;
        Location center;

        UUID interactionId = furniture.getInteractionEntityId();
        Entity entity = interactionId != null ? Bukkit.getEntity(interactionId) : null;
        if (entity instanceof Interaction interaction) {
            width = interaction.getInteractionWidth();
            height = interaction.getInteractionHeight();
            center = interaction.getLocation();
        } else {
            InteractionData data = type.getInteractionData();
            if (data == null) {
                return;
            }
            width = data.getWidth();
            height = data.getHeight();
            center = InteractionHandler.getInteractionLocation(furniture);
            if (center == null) {
                return;
            }
        }

        double half = width / 2.0;
        double minX = center.getX() - half;
        double maxX = center.getX() + half;
        double minY = center.getY();
        double maxY = center.getY() + height;
        double minZ = center.getZ() - half;
        double maxZ = center.getZ() + half;
        drawAabb(player, minX, minY, minZ, maxX, maxY, maxZ, COLOR_INTERACTION);
    }

    private static void drawAabb(Player player, double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ, Color color) {
        Particle.DustOptions dust = new Particle.DustOptions(color, DUST_SIZE);

        for (double x = minX; x <= maxX; x += PARTICLE_SPACING) {
            for (double z = minZ; z <= maxZ; z += PARTICLE_SPACING) {
                spawnDust(player, x, minY, z, dust);
                spawnDust(player, x, maxY, z, dust);
            }
        }
        for (double y = minY; y <= maxY; y += PARTICLE_SPACING) {
            for (double z = minZ; z <= maxZ; z += PARTICLE_SPACING) {
                spawnDust(player, minX, y, z, dust);
                spawnDust(player, maxX, y, z, dust);
            }
        }
        for (double x = minX; x <= maxX; x += PARTICLE_SPACING) {
            for (double y = minY; y <= maxY; y += PARTICLE_SPACING) {
                spawnDust(player, x, y, minZ, dust);
                spawnDust(player, x, y, maxZ, dust);
            }
        }
    }

    private static void spawnDust(Player player, double x, double y, double z, Particle.DustOptions dust) {
        player.spawnParticle(Particle.DUST, x, y, z, 1, 0, 0, 0, 0, dust, true);
    }
}
