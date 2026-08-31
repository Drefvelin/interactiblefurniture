package net.tfminecraft.manager.handlers;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import me.Plugins.TLibs.TLibs;
import net.tfminecraft.InteractibleFurniture;
import net.tfminecraft.enums.SoundEffect;
import net.tfminecraft.events.FurnitureBreakEvent;
import net.tfminecraft.furniture.Furniture;
import net.tfminecraft.furniture.FurnitureType;
import net.tfminecraft.furniture.PlacedSlot;
import net.tfminecraft.manager.handlers.InteractionHandler;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class FurnitureBreakHandler {
    
    public static boolean removeFurniture(UUID furnitureId, Map<UUID, Furniture> placed, 
            Player breaker, String reason) {
        return removeFurniture(furnitureId, placed, breaker, reason, true);
    }

    public static boolean removeFurniture(UUID furnitureId, Map<UUID, Furniture> placed, 
            Player breaker, String reason, boolean dropslots) {
        if(!placed.containsKey(furnitureId)) return false;
        FurnitureBreakEvent event = new FurnitureBreakEvent(placed.get(furnitureId), breaker);
        Bukkit.getPluginManager().callEvent(event);
        if(event.isCancelled()) return false;
        Furniture furniture = placed.remove(furnitureId);
        if (furniture == null) return false;
        clearUnsharedBarriers(furniture, placed);
        Chunk chunk = furniture.getLoc().getChunk();
        InteractibleFurniture.getInstance().getFurnitureManager().persistChunk(chunk);
        if(furniture.getType() != null && furniture.getType().hasSoundEffect(SoundEffect.BREAK)) {
            String sound = furniture.getType().getSoundEffectPath(SoundEffect.BREAK);
            furniture.getLoc().getWorld().playSound(furniture.getLoc(), sound, 1.0f, 1.0f);
        }
        dropFurnitureItem(breaker, furniture, reason);        // Drop items and remove slot displays
        dropSlotItems(furniture, furnitureId, dropslots);

        furniture.removeInteractionEntity();

        // Remove the furniture entity
        removeEntity(furnitureId);
        return true;
    }

    private static void clearUnsharedBarriers(Furniture furniture, Map<UUID, Furniture> placed) {
        for (Block block : new java.util.ArrayList<>(furniture.getBarrierBlocks())) {
            if (block.getType() != Material.BARRIER) continue;
            boolean shared = false;
            for (Furniture other : placed.values()) {
                if (other.getBarrierBlocks().contains(block)) {
                    shared = true;
                    break;
                }
            }
            if (!shared) {
                block.setType(Material.AIR);
            }
        }
        furniture.clearBarrierBlocks();
    }

    public static Set<UUID> findConnectedFurniture(Block startBlock, Map<UUID, Furniture> placed) {
        Set<UUID> connected = new HashSet<>();
        Set<Block> checkedBarriers = new HashSet<>();
        
        // Helper function to recursively find connected furniture
        findConnectedRecursive(startBlock, placed, connected, checkedBarriers);
        
        return connected;
    }
    
    public static void findConnectedRecursive(Block block, Map<UUID, Furniture> placed,
            Set<UUID> connected, Set<Block> checkedBarriers) {
        // Prevent cycles
        if (checkedBarriers.contains(block)) return;
        checkedBarriers.add(block);
        
        // Find all furniture connected to this block
        for (Map.Entry<UUID, Furniture> entry : placed.entrySet()) {
            if (connected.contains(entry.getKey())) continue;
            
            Furniture f = entry.getValue();
            if (f.getBarrierBlocks().contains(block) || f.isOriginBlock(block)) {
                connected.add(entry.getKey());
                // Recursively check all barriers of this furniture
                for (Block b : f.getBarrierBlocks()) {
                    findConnectedRecursive(b, placed, connected, checkedBarriers);
                }
            }
        }
    }

    private static void dropFurnitureItem(Player p, Furniture furniture, String reason) {
        FurnitureType type = furniture.getType();
        if (type == null) return;

        ItemStack furnitureItem = TLibs.getItemAPI().getCreator().getItemFromPath(type.getItemPath());
        if (furnitureItem == null) return;
        if(reason == null || (!reason.equals("picked-up") && !reason.equals("plugin"))) {
            Location dropLoc = furniture.getLoc();
            furniture.getLoc().getWorld().dropItemNaturally(dropLoc, furnitureItem);
        } else if(p != null && reason.equals("picked-up")) {
            p.swingMainHand();
            p.getInventory().setItemInMainHand(furnitureItem);
        }
    }

    private static void dropSlotItems(Furniture furniture, UUID furnitureId, boolean dropslots) {
        Location dropLoc = furniture.getLoc();
        if (dropLoc.getWorld() == null) return;

        for (PlacedSlot slot : new java.util.ArrayList<>(furniture.getActiveSlots().values())) {
            if (dropslots) {
                ItemStack item = slot.getCurrentItem();
                if (item != null) {
                    dropLoc.getWorld().dropItemNaturally(dropLoc, item);
                }
            }
            slot.removeDisplayStand(dropLoc.getWorld());
        }
        furniture.clearActiveSlots();
    }

    private static void removeEntity(UUID furnitureId) {
        Entity entity = Bukkit.getEntity(furnitureId);
        if (entity != null) {
            entity.remove();
        }
    }
}