package net.tfminecraft.manager.handlers;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import me.Plugins.TLibs.TLibs;
import net.tfminecraft.events.FurnitureInteractEvent;
import net.tfminecraft.events.FurnitureSlotItemAddEvent;
import net.tfminecraft.events.FurnitureSlotItemTakeEvent;
import net.tfminecraft.furniture.Furniture;
import net.tfminecraft.furniture.FurnitureSlot;
import net.tfminecraft.furniture.data.DisplayData;
import net.tfminecraft.loaders.SoundLoader;
import net.tfminecraft.utils.CoordinateUtils;

import java.util.ArrayList;
import java.util.List;

public class SlotInteractionHandler {
    public static boolean handleSlotInteraction(Player player, Block clicked, Action action, Furniture furniture, 
            Entity furnitureEntity, org.bukkit.block.BlockFace clickedFace) {
        List<FurnitureSlot> possibleSlots = new ArrayList<>(furniture.getType().getSlots().values());
        if (possibleSlots.isEmpty()) {
            // Fire FurnitureInteractEvent since no slots are present
            FurnitureInteractEvent event = new FurnitureInteractEvent(player, furniture);
            Bukkit.getPluginManager().callEvent(event);
            return false;
        }

        FurnitureSlot closestSlot = findClosestSlot(player.getInventory().getItemInMainHand(), player, clicked, clickedFace, furniture, furnitureEntity, possibleSlots);
        if (closestSlot == null) {
            return false;
        }

        return handleSlotAction(player, clicked, action, furniture, furnitureEntity, closestSlot);
    }

    private static FurnitureSlot findClosestSlot(ItemStack item, Player player, Block clicked, org.bukkit.block.BlockFace face,
            Furniture furniture, Entity furnitureEntity, List<FurnitureSlot> slots) {
        // Calculate click point intersection
        Vector clickPoint = CoordinateUtils.calculateClickPoint(player, clicked, face);
        if (clickPoint == null) return null;

        // Find closest slot
        FurnitureSlot closest = null;
        double closestDist = Double.MAX_VALUE;

        for (FurnitureSlot slot : slots) {
            if(!slot.canInteract(item)) continue;
            Location slotLoc = slot.computeDisplayLocation(
                furniture.getLoc(), 
                (ItemDisplay) furnitureEntity,
                new DisplayData()
            );
            Vector slotPoint = slotLoc.toVector();
            double dist = CoordinateUtils.calculateDistance(slotPoint, clickPoint, face);

            if (dist < closestDist) {
                closestDist = dist;
                closest = slot;
            }
        }

        return closest;
    }

    private static boolean handleSlotAction(Player player, Block clicked, Action action, 
            Furniture furniture, Entity furnitureEntity, FurnitureSlot slot) {
        if (action == Action.RIGHT_CLICK_BLOCK) {
            return handleRightClick(player, furniture, furnitureEntity, slot);
        } else if (action == Action.LEFT_CLICK_BLOCK) {
            return handleLeftClick(player, clicked, furniture, slot);
        }
        return false;
    }

    private static boolean handleRightClick(Player player, Furniture furniture, 
            Entity furnitureEntity, FurnitureSlot slot) {
        ItemStack held = player.getInventory().getItemInMainHand();

        // Take item if slot occupied and hand empty
        if (held == null || held.getType() == Material.AIR) {
            return tryTakeItem(player, furniture, slot);
        }

        // Place item if allowed
        return tryPlaceItem(player, furniture, furnitureEntity, slot, held);
    }

    private static boolean tryTakeItem(Player player, Furniture furniture, FurnitureSlot slot) {
        boolean[] taken = {false};

        furniture.getActiveSlot(slot.getId()).ifPresent(activeSlot -> {
            ItemStack slotItem = activeSlot.getCurrentItem();
            if (slotItem == null) {
                ItemDisplay displayStand = (ItemDisplay)Bukkit.getEntity(activeSlot.getDisplayStandId());
                if (displayStand == null) return;
                slotItem = displayStand.getItemStack();
            }

            if(slotItem == null) return;

            // Fire the take event before removing the item
            FurnitureSlotItemTakeEvent event = new FurnitureSlotItemTakeEvent(player, furniture, slot, slotItem.clone());
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) {
                return;
            }
            furniture.removeActiveSlot(slot.getId());
            ItemStack item = event.getItem();

            boolean merged = false;

            // Try to find a matching stack in inventory first
            for (ItemStack invItem : player.getInventory().getContents()) {
                if (invItem == null) continue;

                // Match by similarity (type + meta)
                if (invItem.isSimilar(item)) {
                    int space = invItem.getMaxStackSize() - invItem.getAmount();
                    if (space > 0) {
                        int toAdd = Math.min(space, item.getAmount());
                        invItem.setAmount(invItem.getAmount() + toAdd);
                        item.setAmount(item.getAmount() - toAdd);

                        if (item.getAmount() <= 0) {
                            merged = true;
                            break;
                        }
                    }
                }
            }

            // If no existing stack matched or was full, place into main hand
            if (!merged && item.getAmount() > 0) {
                player.getInventory().setItemInMainHand(item);
            }

            // Play retrieval sound
            String path = TLibs.getItemAPI().getChecker().getAsStringPath(item);
            String sound = "minecraft:entity.item_frame.add_item";
            if (SoundLoader.has(path)) {
                sound = SoundLoader.getByString(path);
            }
            furniture.getLoc().getWorld().playSound(furniture.getLoc(), sound, 1, 1);

            player.swingMainHand();
            taken[0] = true;
        });

        return taken[0];
    }


    private static boolean tryPlaceItem(Player player, Furniture furniture, 
            Entity furnitureEntity, FurnitureSlot slot, ItemStack held) {
        if (!slot.getWhitelist().stream()
                .anyMatch(path -> TLibs.getItemAPI().getChecker().checkItemWithPath(held, path))) {
            return false;
        }

        if (furniture.getActiveSlot(slot.getId()).isPresent()) {
            return false;
        }
        ItemStack toPlace = held.clone();
        FurnitureSlotItemAddEvent event = new FurnitureSlotItemAddEvent(player, furniture, slot, toPlace);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return false;
        }
        toPlace.setAmount(1);
        held.setAmount(held.getAmount() - 1);

        if (!(furnitureEntity instanceof ItemDisplay)) {
            return false;
        }

        slot.spawnDisplayStand(furniture.getLoc(), toPlace, (ItemDisplay) furnitureEntity, event.getDisplayData());
        furniture.addActiveSlot(slot);
        String path = TLibs.getItemAPI().getChecker().getAsStringPath(toPlace);
        String sound = "minecraft:entity.item_frame.add_item";
        if(SoundLoader.has(path)) {
            sound = SoundLoader.getByString(path);
        }
        furniture.getLoc().getWorld().playSound(furniture.getLoc(), sound, 1, 1);
        player.swingMainHand();
        return true;
    }

    private static boolean handleLeftClick(Player player, Block clicked, Furniture furniture, FurnitureSlot slot) {
        boolean[] removed = {false};
        furniture.getActiveSlot(slot.getId()).ifPresent(activeSlot -> {
            ItemStack item = activeSlot.getCurrentItem();
            if (item != null) {
                furniture.removeActiveSlot(slot.getId());
                clicked.getWorld().dropItemNaturally(clicked.getLocation(), item);
                removed[0] = true;
            }
        });
        return removed[0];
    }
}