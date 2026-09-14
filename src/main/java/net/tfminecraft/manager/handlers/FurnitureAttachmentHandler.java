package net.tfminecraft.manager.handlers;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.Sound;

import net.tfminecraft.InteractibleFurniture;
import net.tfminecraft.events.FurnitureSlotFurnitureAddEvent;
import net.tfminecraft.events.FurnitureSlotFurnitureTakeEvent;
import net.tfminecraft.furniture.Furniture;
import net.tfminecraft.furniture.FurnitureType;
import net.tfminecraft.furniture.PlacedFurnitureSlot;
import net.tfminecraft.furniture.SlotDefinition;
import net.tfminecraft.furniture.SlotType;

public final class FurnitureAttachmentHandler {
    private FurnitureAttachmentHandler() {}

    public static boolean attach(Furniture parent, String slotId, Furniture nested, Player player) {
        if (parent == null || nested == null || player == null || slotId == null) {
            return false;
        }
        FurnitureType parentType = parent.getType();
        if (parentType == null) {
            return false;
        }

        SlotDefinition slot = parentType.getSlot(slotId);
        if (slot == null || slot.getSlotType() != SlotType.FURNITURE) {
            return false;
        }
        if (parent.hasActiveFurnitureSlot(slotId)) {
            return false;
        }
        if (nested.isAttached()) {
            return false;
        }
        if (parent.getEntityId().equals(nested.getEntityId())) {
            return false;
        }

        FurnitureType nestedType = nested.getType();
        if (nestedType == null || !slot.isFurnitureAllowed(nestedType)) {
            return false;
        }

        FurnitureSlotFurnitureAddEvent event = new FurnitureSlotFurnitureAddEvent(player, parent, slot, nested);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return false;
        }

        nested.setAttachment(parent.getEntityId(), slotId);
        InteractibleFurniture.getInstance().getFurnitureManager().getPlacedFurniture().remove(nested.getEntityId());

        PlacedFurnitureSlot placed = parent.getOrCreatePlacedFurnitureSlot(slotId);
        placed.setNested(nested);
        placed.setParent(parent);

        FurnitureNestedDisplay.prepareAttached(nested);
        FurnitureNestedDisplay.syncNestedRoot(parent, slotId, nested);

        InteractibleFurniture.getInstance().getFurnitureManager().persistFurniture(parent);
        return true;
    }

    public static boolean attachFromCarried(Furniture parent, String slotId, Furniture nested, Player player) {
        if (nested != null && nested.isCarried()) {
            nested.stopCarrying();
        }
        if (!attach(parent, slotId, nested, player)) {
            return false;
        }
        parent.getLoc().getWorld().playSound(parent.getLoc(), Sound.ENTITY_ITEM_FRAME_ADD_ITEM, 1f, 1f);
        player.swingMainHand();
        return true;
    }

    public static Furniture detach(Furniture parent, String slotId, Player player) {
        if (parent == null || player == null || slotId == null) {
            return null;
        }
        if (!parent.hasActiveFurnitureSlot(slotId)) {
            return null;
        }

        PlacedFurnitureSlot placed = parent.getActiveFurnitureSlot(slotId).orElse(null);
        if (placed == null || placed.getNested() == null) {
            return null;
        }

        Furniture nested = placed.getNested();
        SlotDefinition slot = parent.getType() != null ? parent.getType().getSlot(slotId) : null;
        if (slot == null) {
            return null;
        }

        FurnitureSlotFurnitureTakeEvent event = new FurnitureSlotFurnitureTakeEvent(player, parent, slot, nested);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return null;
        }

        FurnitureNestedDisplay.prepareDetached(nested, parent, slotId);
        nested.clearAttachment();
        parent.removeActiveFurnitureSlot(slotId);

        InteractibleFurniture.getInstance().getFurnitureManager().getPlacedFurniture().put(nested.getEntityId(), nested);
        InteractibleFurniture.getInstance().getFurnitureManager().persistFurniture(parent);
        return nested;
    }
}
