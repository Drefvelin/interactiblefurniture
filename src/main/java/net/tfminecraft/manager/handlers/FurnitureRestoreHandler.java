package net.tfminecraft.manager.handlers;

import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import me.Plugins.TLibs.TLibs;
import net.tfminecraft.InteractibleFurniture;
import net.tfminecraft.furniture.Furniture;
import net.tfminecraft.furniture.FurnitureType;
import net.tfminecraft.furniture.PlacedSlot;
import net.tfminecraft.utils.Keys;

public class FurnitureRestoreHandler {

    private FurnitureRestoreHandler() {}

    public static Furniture restore(Furniture furniture) {
        if (furniture == null || furniture.getLoc() == null || furniture.getLoc().getWorld() == null) {
            return null;
        }
        if (furniture.getType() == null) {
            return null;
        }

        if (furniture.isPersistedCarried()) {
            cleanupCarried(furniture);
            return null;
        }

        if (!ensureDisplay(furniture)) {
            return null;
        }
        restoreBarriers(furniture);
        if (furniture.getType().hasInteraction()) {
            InteractionHandler.updateInteractionPosition(furniture);
        }
        restoreSlots(furniture);
        return furniture;
    }

    public static void reconcileChunk(Chunk chunk, Map<UUID, Furniture> placed) {
        for (Entity entity : chunk.getEntities()) {
            if (entity instanceof ItemDisplay display) {
                String raw = display.getPersistentDataContainer().get(Keys.furnitureDisplay(), PersistentDataType.STRING);
                if (raw == null) continue;
                try {
                    UUID furnitureId = UUID.fromString(raw);
                    if (!placed.containsKey(furnitureId)) {
                        display.remove();
                    }
                } catch (IllegalArgumentException ignored) {
                    display.remove();
                }
            } else if (entity instanceof Interaction interaction) {
                String raw = interaction.getPersistentDataContainer().get(Keys.furnitureEntity(), PersistentDataType.STRING);
                if (raw == null) continue;
                try {
                    UUID furnitureId = UUID.fromString(raw);
                    if (!placed.containsKey(furnitureId)) {
                        interaction.remove();
                    }
                } catch (IllegalArgumentException ignored) {
                    interaction.remove();
                }
            }
        }
    }

    private static boolean ensureDisplay(Furniture furniture) {
        Entity existing = Bukkit.getEntity(furniture.getEntityId());
        if (existing instanceof ItemDisplay display && !display.isDead()) {
            FurniturePlacementHandler.tagDisplay(display, furniture.getEntityId());
            Location saved = furniture.getLoc();
            if (display.getWorld().equals(saved.getWorld())
                    && display.getLocation().distanceSquared(saved) > 1.0) {
                display.teleport(saved);
            }
            return true;
        }

        BlockFace face = furniture.getOriginBlockFace().orElse(BlockFace.UP);
        ItemDisplay spawned = FurniturePlacementHandler.spawnDisplayAt(
                furniture, furniture.getLoc(), furniture.getYaw(), face);
        if (spawned == null) {
            InteractibleFurniture.getInstance().getLogger()
                    .warning("Failed to respawn furniture display at " + furniture.getLoc());
            return false;
        }
        furniture.setEntityId(spawned.getUniqueId());
        FurniturePlacementHandler.tagDisplay(spawned, spawned.getUniqueId());
        furniture.removeInteractionEntity();
        InteractibleFurniture.getInstance().getFurnitureManager().markDirty(furniture);
        return true;
    }

    private static void restoreBarriers(Furniture furniture) {
        FurnitureType type = furniture.getType();
        if (!furniture.getBarrierBlocks().isEmpty()) {
            for (Block block : new java.util.ArrayList<>(furniture.getBarrierBlocks())) {
                if (block.getType() == Material.AIR) {
                    block.setType(Material.BARRIER);
                }
            }
            return;
        }
        if (type.getLayers() == null || type.getLayers().isEmpty()) return;
        Location originLoc = furniture.getOriginBlockLocation().orElse(null);
        BlockFace face = furniture.getOriginBlockFace().orElse(null);
        if (originLoc == null || face == null) return;
        FurniturePlacementHandler.placeBarrierBlocks(type.getLayers(), furniture, originLoc.getBlock(), face);
    }

    private static void restoreSlots(Furniture furniture) {
        Entity parent = Bukkit.getEntity(furniture.getEntityId());
        if (!(parent instanceof ItemDisplay display)) return;

        for (PlacedSlot slot : furniture.getActiveSlots().values()) {
            slot.setFurniture(furniture);
            ItemStack item = slot.getCurrentItem();
            UUID standId = slot.getDisplayStandId();
            Entity stand = standId != null ? Bukkit.getEntity(standId) : null;
            if (stand instanceof ItemDisplay slotDisplay && !slotDisplay.isDead()) {
                if (item != null) {
                    slotDisplay.setItemStack(item);
                } else if (slotDisplay.getItemStack() != null) {
                    slot.setModel(slotDisplay.getItemStack());
                }
                slotDisplay.getPersistentDataContainer().set(
                        Keys.furnitureDisplay(), PersistentDataType.STRING, furniture.getEntityId().toString());
                continue;
            }
            if (item == null) continue;
            slot.spawnDisplayStand(furniture.getLoc(), item, display, null);
            InteractibleFurniture.getInstance().getFurnitureManager().markDirty(furniture);
        }
    }

    private static void cleanupCarried(Furniture furniture) {
        Location dropLoc = furniture.getLoc();
        FurnitureType type = furniture.getType();
        if (dropLoc != null && dropLoc.getWorld() != null && type != null) {
            ItemStack furnitureItem = TLibs.getItemAPI().getCreator().getItemFromPath(type.getItemPath());
            if (furnitureItem != null) {
                dropLoc.getWorld().dropItemNaturally(dropLoc, furnitureItem);
            }
        }
        furniture.removeInteractionEntity();
        for (PlacedSlot slot : furniture.getActiveSlots().values()) {
            if (dropLoc != null) {
                slot.removeDisplayStand(dropLoc.getWorld());
            }
        }
        Entity entity = Bukkit.getEntity(furniture.getEntityId());
        if (entity != null) entity.remove();
    }
}
