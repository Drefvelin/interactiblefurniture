package net.tfminecraft.manager.handlers;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ItemDisplay;

import net.tfminecraft.furniture.Furniture;
import net.tfminecraft.furniture.PlacedFurnitureSlot;
import net.tfminecraft.furniture.PlacedSlot;
import net.tfminecraft.furniture.SlotDefinition;
import net.tfminecraft.furniture.data.DisplayData;

public final class FurnitureNestedDisplay {
    private FurnitureNestedDisplay() {}

    public static void syncNestedRoot(Furniture parent, String slotId, Furniture nested) {
        if (parent == null || nested == null || parent.getType() == null) {
            return;
        }
        SlotDefinition def = parent.getType().getSlot(slotId);
        if (def == null) {
            return;
        }

        ItemDisplay parentDisplay = getDisplay(parent);
        ItemDisplay nestedDisplay = getDisplay(nested);
        if (parentDisplay == null || nestedDisplay == null) {
            return;
        }

        Location worldLoc = def.computeDisplayLocation(parent.getLoc(), parentDisplay, new DisplayData());
        nestedDisplay.teleport(worldLoc);
        nestedDisplay.setTransformation(def.buildFinalTransformation(parentDisplay, new DisplayData()));
        nested.setLoc(worldLoc);

        syncNestedItemSlots(nested, nestedDisplay);
    }

    public static void onParentTransformChanged(Furniture parent) {
        if (parent == null) {
            return;
        }
        for (PlacedFurnitureSlot slot : parent.getActiveFurnitureSlots().values()) {
            Furniture nested = slot.getNested();
            if (nested != null) {
                syncNestedRoot(parent, slot.getId(), nested);
            }
        }
    }

    public static void syncItemSlots(Furniture nested) {
        if (nested == null) {
            return;
        }
        ItemDisplay nestedDisplay = getDisplay(nested);
        if (nestedDisplay == null) {
            return;
        }
        syncNestedItemSlots(nested, nestedDisplay);
    }

    public static void prepareAttached(Furniture nested) {
        if (nested == null) {
            return;
        }
        nested.clearOriginBlock();
        nested.removeInteractionEntity();
    }

    public static void prepareDetached(Furniture nested, Furniture parent, String slotId) {
        if (nested == null || parent == null || parent.getType() == null) {
            return;
        }

        SlotDefinition def = parent.getType().getSlot(slotId);
        ItemDisplay parentDisplay = getDisplay(parent);
        ItemDisplay nestedDisplay = getDisplay(nested);
        if (def == null || parentDisplay == null || nestedDisplay == null) {
            return;
        }

        Location worldLoc = def.computeDisplayLocation(parent.getLoc(), parentDisplay, new DisplayData());
        nestedDisplay.teleport(worldLoc);
        nestedDisplay.setTransformation(def.buildFinalTransformation(parentDisplay, new DisplayData()));
        nested.setLoc(worldLoc);

        parent.getOriginBlockLocation().ifPresent(loc -> {
            BlockFace face = parent.getOriginBlockFace().orElse(BlockFace.UP);
            nested.setOriginBlock(loc, face);
        });
        nested.spawnInteractionEntity();
        syncNestedItemSlots(nested, nestedDisplay);
    }

    private static void syncNestedItemSlots(Furniture nested, ItemDisplay nestedDisplay) {
        for (PlacedSlot slot : nested.getActiveSlots().values()) {
            if (slot.getDefinition() == null || slot.getDisplayStandId() == null) {
                continue;
            }
            slot.syncDisplayToParent(nestedDisplay, slot.getCurrentDisplayData());
        }
    }

    private static ItemDisplay getDisplay(Furniture furniture) {
        if (furniture == null) {
            return null;
        }
        var entity = Bukkit.getEntity(furniture.getEntityId());
        return entity instanceof ItemDisplay display ? display : null;
    }
}
