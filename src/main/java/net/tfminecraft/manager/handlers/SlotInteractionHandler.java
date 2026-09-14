package net.tfminecraft.manager.handlers;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import me.Plugins.TLibs.TLibs;
import net.tfminecraft.InteractibleFurniture;
import net.tfminecraft.events.FurnitureInteractEvent;
import net.tfminecraft.events.FurnitureSlotItemAddEvent;
import net.tfminecraft.events.FurnitureSlotItemTakeEvent;
import net.tfminecraft.furniture.Furniture;
import net.tfminecraft.furniture.FurnitureType;
import net.tfminecraft.furniture.PlacedFurnitureSlot;
import net.tfminecraft.furniture.PlacedSlot;
import net.tfminecraft.furniture.SlotDefinition;
import net.tfminecraft.furniture.SlotType;
import net.tfminecraft.furniture.data.DisplayData;
import net.tfminecraft.loaders.SoundLoader;
import net.tfminecraft.utils.CoordinateUtils;

import java.util.ArrayList;
import java.util.List;

public class SlotInteractionHandler {
    public record SlotHitTarget(Furniture furniture, SlotDefinition slot, double distance) {}

    public static boolean handleSlotInteraction(Player player, Block clicked, Action action, Furniture furniture,
            Entity furnitureEntity, org.bukkit.block.BlockFace clickedFace) {
        FurnitureType type = furniture.getType();
        if (type == null) return false;
        List<SlotDefinition> possibleSlots = new ArrayList<>(type.getSlots().values());
        if (possibleSlots.isEmpty()) {
            FurnitureInteractEvent event = new FurnitureInteractEvent(player, furniture);
            Bukkit.getPluginManager().callEvent(event);
            return false;
        }

        SlotDefinition closestSlot = findClosestInteractibleSlot(
                player.getInventory().getItemInMainHand(), player, clicked, clickedFace,
                furniture, furnitureEntity, possibleSlots);
        if (closestSlot == null) {
            return false;
        }

        return handleSlotAction(player, clicked, action, furniture, furnitureEntity, closestSlot);
    }

    public static boolean handleSlotInteraction(Player player, Furniture furniture, ItemDisplay furnitureEntity,
            SlotDefinition targetSlot, Action action) {
        return handleSlotAction(player, null, action, furniture, furnitureEntity, targetSlot);
    }

    public static SlotDefinition findClosestSlotForHit(Vector clickPoint, Furniture furniture, ItemDisplay parent) {
        return findClosestSlotForHit(clickPoint, furniture, parent, null);
    }

    public static SlotDefinition findClosestSlotForHit(Vector clickPoint, Furniture furniture, ItemDisplay parent, ItemStack held) {
        SlotHitTarget target = findBestSlotHit(clickPoint, furniture, parent, held);
        return target != null ? target.slot() : null;
    }

    public static SlotHitTarget findBestSlotHit(Vector clickPoint, Furniture furniture, ItemDisplay display) {
        return findBestSlotHit(clickPoint, furniture, display, null);
    }

    public static SlotHitTarget findBestSlotHit(Vector clickPoint, Furniture furniture, ItemDisplay display, ItemStack held) {
        if (clickPoint == null || furniture == null || display == null) {
            return null;
        }

        SlotHitTarget best = null;

        for (PlacedFurnitureSlot placed : furniture.getActiveFurnitureSlots().values()) {
            Furniture nested = placed.getNested();
            if (nested == null) {
                continue;
            }
            var nestedEntity = Bukkit.getEntity(nested.getEntityId());
            if (!(nestedEntity instanceof ItemDisplay nestedDisplay)) {
                continue;
            }
            SlotHitTarget nestedHit = findBestSlotHit(clickPoint, nested, nestedDisplay, held);
            if (nestedHit != null && (best == null || nestedHit.distance() < best.distance())) {
                best = nestedHit;
            }
        }

        FurnitureType type = furniture.getType();
        if (type != null) {
            for (SlotDefinition slot : type.getSlots().values()) {
                if (!slotAcceptsHeld(slot, held)) {
                    continue;
                }
                Location slotLoc = slot.computeDisplayLocation(
                        furniture.getLoc(),
                        display,
                        new DisplayData()
                );
                double dist = CoordinateUtils.distance3D(slotLoc.toVector(), clickPoint);
                if (best == null || dist < best.distance()) {
                    best = new SlotHitTarget(furniture, slot, dist);
                }
            }
        }

        return best;
    }

    private static boolean slotAcceptsHeld(SlotDefinition slot, ItemStack held) {
        if (slot == null || !slot.isInteractible()) {
            return false;
        }
        if (held == null || held.getType().isAir()) {
            return slot.canInteractEmptyHand();
        }
        return slot.canInteract(held);
    }

    public static boolean tryAttachCarried(Player player, Furniture parent, Furniture carried, Vector clickPoint) {
        if (player == null || parent == null || carried == null || clickPoint == null) {
            return false;
        }
        var parentEntity = Bukkit.getEntity(parent.getEntityId());
        if (!(parentEntity instanceof ItemDisplay display)) {
            return false;
        }

        SlotDefinition slot = findClosestEmptyFurnitureSlot(parent, carried, clickPoint, display);
        if (slot == null) {
            return false;
        }
        return FurnitureAttachmentHandler.attachFromCarried(parent, slot.getId(), carried, player);
    }

    private static SlotDefinition findClosestEmptyFurnitureSlot(
            Furniture parent, Furniture carried, Vector clickPoint, ItemDisplay display) {
        FurnitureType type = parent.getType();
        FurnitureType carriedType = carried.getType();
        if (type == null || carriedType == null) {
            return null;
        }

        SlotDefinition closest = null;
        double closestDist = Double.MAX_VALUE;

        for (SlotDefinition slot : type.getSlots().values()) {
            if (slot.getSlotType() != SlotType.FURNITURE || !slot.isInteractible()) {
                continue;
            }
            if (parent.hasActiveFurnitureSlot(slot.getId())) {
                continue;
            }
            if (!slot.isFurnitureAllowed(carriedType)) {
                continue;
            }
            Location slotLoc = slot.computeDisplayLocation(
                    parent.getLoc(),
                    display,
                    new DisplayData()
            );
            double dist = CoordinateUtils.distance3D(slotLoc.toVector(), clickPoint);
            if (dist < closestDist) {
                closestDist = dist;
                closest = slot;
            }
        }

        return closest;
    }

    private static SlotDefinition findClosestInteractibleSlot(ItemStack item, Player player, Block clicked, BlockFace face,
            Furniture furniture, Entity furnitureEntity, List<SlotDefinition> slots) {
        Vector clickPoint = CoordinateUtils.calculateClickPoint(player, clicked, face);
        if (clickPoint == null) return null;

        SlotDefinition closest = null;
        double closestDist = Double.MAX_VALUE;

        for (SlotDefinition slot : slots) {
            boolean canInteract = item == null || item.getType().isAir()
                    ? slot.canInteractEmptyHand()
                    : slot.canInteract(item);
            if (!canInteract) continue;
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
            Furniture furniture, Entity furnitureEntity, SlotDefinition slot) {
        if (action == Action.RIGHT_CLICK_BLOCK) {
            return handleRightClick(player, furniture, furnitureEntity, slot);
        } else if (action == Action.LEFT_CLICK_BLOCK && clicked != null) {
            return handleLeftClick(player, clicked, furniture, slot);
        }
        return false;
    }

    private static boolean handleRightClick(Player player, Furniture furniture,
            Entity furnitureEntity, SlotDefinition slot) {
        ItemStack held = player.getInventory().getItemInMainHand();

        if (slot.isFurnitureSlot()) {
            if (held == null || held.getType() == Material.AIR) {
                return tryTakeFurniture(player, furniture, slot);
            }
            return false;
        }

        if (held == null || held.getType() == Material.AIR) {
            return tryTakeItem(player, furniture, slot);
        }

        return tryPlaceItem(player, furniture, furnitureEntity, slot, held);
    }

    private static boolean tryTakeFurniture(Player player, Furniture furniture, SlotDefinition slot) {
        if (!furniture.hasActiveFurnitureSlot(slot.getId())) {
            return false;
        }
        Furniture detached = FurnitureAttachmentHandler.detach(furniture, slot.getId(), player);
        if (detached == null) {
            return false;
        }
        detached.carry(player);
        furniture.getLoc().getWorld().playSound(furniture.getLoc(), "minecraft:entity.item_frame.add_item", 1, 1);
        player.swingMainHand();
        return true;
    }

    private static boolean tryTakeItem(Player player, Furniture furniture, SlotDefinition slot) {
        boolean[] taken = {false};

        furniture.getActiveSlot(slot.getId()).ifPresent(activeSlot -> {
            ItemStack slotItem = activeSlot.getCurrentItem();
            if (slotItem == null) {
                ItemDisplay displayStand = (ItemDisplay) Bukkit.getEntity(activeSlot.getDisplayStandId());
                if (displayStand == null) return;
                slotItem = displayStand.getItemStack();
            }

            if (slotItem == null) return;

            FurnitureSlotItemTakeEvent event = new FurnitureSlotItemTakeEvent(player, furniture, slot, slotItem.clone());
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) {
                return;
            }
            furniture.removeActiveSlot(slot.getId());
            InteractibleFurniture.getInstance().getFurnitureManager().persistFurniture(furniture);
            ItemStack item = event.getItem();

            boolean merged = false;

            for (ItemStack invItem : player.getInventory().getContents()) {
                if (invItem == null) continue;

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

            if (!merged && item.getAmount() > 0) {
                player.getInventory().setItemInMainHand(item);
            }

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
            Entity furnitureEntity, SlotDefinition slot, ItemStack held) {
        if (!slot.isItemAllowed(held)) {
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
        toPlace = event.getItem();
        toPlace.setAmount(1);
        held.setAmount(held.getAmount() - 1);

        if (!(furnitureEntity instanceof ItemDisplay)) {
            return false;
        }

        PlacedSlot placed = furniture.getOrCreatePlacedSlot(slot.getId());
        placed.spawnDisplayStand(furniture.getLoc(), toPlace, (ItemDisplay) furnitureEntity, event.getDisplayData());
        InteractibleFurniture.getInstance().getFurnitureManager().persistFurniture(furniture);
        String path = TLibs.getItemAPI().getChecker().getAsStringPath(toPlace);
        String sound = "minecraft:entity.item_frame.add_item";
        if (SoundLoader.has(path)) {
            sound = SoundLoader.getByString(path);
        }
        furniture.getLoc().getWorld().playSound(furniture.getLoc(), sound, 1, 1);
        player.swingMainHand();
        return true;
    }

    private static boolean handleLeftClick(Player player, Block clicked, Furniture furniture, SlotDefinition slot) {
        boolean[] removed = {false};
        furniture.getActiveSlot(slot.getId()).ifPresent(activeSlot -> {
            ItemStack item = activeSlot.getCurrentItem();
            if (item == null) {
                return;
            }
            FurnitureSlotItemTakeEvent event = new FurnitureSlotItemTakeEvent(
                    player, furniture, slot, item.clone());
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) {
                return;
            }
            furniture.removeActiveSlot(slot.getId());
            InteractibleFurniture.getInstance().getFurnitureManager().persistFurniture(furniture);
            clicked.getWorld().dropItemNaturally(clicked.getLocation(), event.getItem());
            removed[0] = true;
        });
        return removed[0];
    }
}
