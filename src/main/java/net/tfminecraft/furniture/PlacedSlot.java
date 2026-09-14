package net.tfminecraft.furniture;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;

import net.tfminecraft.furniture.data.DisplayData;
import net.tfminecraft.manager.handlers.FurnitureNestedDisplay;
import net.tfminecraft.utils.Keys;

/**
 * Runtime item occupying a {@link SlotDefinition} on one placed {@link Furniture}.
 */
public final class PlacedSlot {
    private Furniture furniture;
    private final String slotId;
    private DisplayData currentData;
    private ItemStack currentItem;
    private UUID displayStandId;

    public PlacedSlot(Furniture furniture, String slotId) {
        this.furniture = furniture;
        this.slotId = slotId;
    }

    public String getId() {
        return slotId;
    }

    public Furniture getFurniture() {
        return furniture;
    }

    public void setFurniture(Furniture furniture) {
        this.furniture = furniture;
    }

    public SlotDefinition getDefinition() {
        FurnitureType type = furniture != null ? furniture.getType() : null;
        return type != null ? type.getSlot(slotId) : null;
    }

    public ItemStack getCurrentItem() {
        return currentItem;
    }

    public UUID getDisplayStandId() {
        return displayStandId;
    }

    public void setDisplayStandId(UUID displayStandId) {
        this.displayStandId = displayStandId;
    }

    public DisplayData getCurrentDisplayData() {
        return currentData != null ? currentData : new DisplayData();
    }

    public void setModel(ItemStack item) {
        this.currentItem = item;
    }

    public void updateDisplay() {
        if (displayStandId == null) return;
        ItemDisplay disp = (ItemDisplay) Bukkit.getEntity(displayStandId);
        if (disp == null) return;
        if (currentItem == null || currentItem.getType() == Material.AIR) {
            disp.remove();
            displayStandId = null;
            if (furniture != null) {
                furniture.removeActiveSlot(slotId, false);
            }
            return;
        }
        disp.setItemStack(currentItem);
    }

    public void setCurrentItem(ItemStack currentItem) {
        this.currentItem = currentItem;
        updateDisplay();
    }

    public void forceModel(ItemStack i) {
        if (furniture != null && displayStandId == null) {
            ItemDisplay parent = (ItemDisplay) Bukkit.getEntity(furniture.getEntityId());
            if (parent != null) {
                spawnDisplayStand(furniture.getLoc(), i, parent, null);
                if (furniture.isAttached()) {
                    FurnitureNestedDisplay.syncItemSlots(furniture);
                }
                return;
            }
        }
        this.currentItem = i;
        updateDisplay();
    }

    public void clearModel() {
        this.currentItem = null;
        updateDisplay();
    }

    public void applyDisplayData(DisplayData data) {
        if (displayStandId == null || furniture == null || furniture.isCarried()) return;
        SlotDefinition def = getDefinition();
        if (def == null) return;

        ItemDisplay display = (ItemDisplay) Bukkit.getEntity(displayStandId);
        if (display == null) return;
        ItemDisplay parentDisplay = (ItemDisplay) Bukkit.getEntity(furniture.getEntityId());
        if (parentDisplay == null) return;

        if (data == null) {
            data = new DisplayData();
        }
        currentData = data;
        syncDisplayToParent(parentDisplay, data, false);
    }

    public void setRotation(DisplayData data) {
        applyDisplayData(data);
    }

    public void followParentTransform(ItemDisplay parentDisplay) {
        syncDisplayToParent(parentDisplay, getCurrentDisplayData(), true);
    }

    public void syncDisplayToParent(ItemDisplay parentDisplay, DisplayData data) {
        syncDisplayToParent(parentDisplay, data, false);
    }

    private void syncDisplayToParent(ItemDisplay parentDisplay, DisplayData data, boolean interpolate) {
        if (displayStandId == null || furniture == null || parentDisplay == null) {
            return;
        }
        SlotDefinition def = getDefinition();
        if (def == null) {
            return;
        }

        ItemDisplay slotDisp = (ItemDisplay) Bukkit.getEntity(displayStandId);
        if (slotDisp == null) {
            return;
        }

        if (data == null) {
            data = new DisplayData();
        }
        currentData = data;

        Location desiredLoc = def.computeDisplayLocationFromTransform(parentDisplay, data);
        float offset = furniture.getType() != null
                ? (float) (furniture.getType().getDisplayData().getyPos() * -1)
                : 0f;
        desiredLoc.add(0, offset, 0);

        Transformation baseT = def.buildFinalTransformation(parentDisplay, data);

        if (interpolate && furniture.isCarried()) {
            Location originLoc = slotDisp.getLocation();
            Vector diff = desiredLoc.toVector().subtract(originLoc.toVector());
            baseT.getTranslation().set(
                    (float) diff.getX(),
                    (float) diff.getY(),
                    (float) diff.getZ()
            );
            slotDisp.setInterpolationDuration(2);
            slotDisp.setInterpolationDelay(0);
            slotDisp.setTransformation(baseT);
            return;
        }

        slotDisp.teleport(desiredLoc);
        slotDisp.setTransformation(baseT);
    }

    public void spawnDisplayStand(Location baseLocation, ItemStack item, ItemDisplay parentDisplay, DisplayData data) {
        SlotDefinition def = getDefinition();
        if (def == null || furniture == null || parentDisplay == null) return;
        if (data == null) data = new DisplayData();
        currentData = data;

        Location spawnLoc = parentDisplay.getLocation();
        if (spawnLoc.getWorld() == null) {
            spawnLoc = baseLocation;
        }

        ItemDisplay display = (ItemDisplay) spawnLoc.getWorld().spawnEntity(spawnLoc, EntityType.ITEM_DISPLAY);
        display.setItemStack(item.clone());
        display.setBillboard(Display.Billboard.FIXED);
        display.setBrightness(new Display.Brightness(
                spawnLoc.getBlock().getLightLevel(),
                spawnLoc.getBlock().getLightLevel()));
        display.setShadowRadius(0.1f);
        display.setShadowStrength(0.1f);
        display.setPersistent(true);
        display.setViewRange(50f);
        display.getPersistentDataContainer().set(
                Keys.furnitureDisplay(),
                PersistentDataType.STRING,
                furniture.getEntityId().toString());
        display.getPersistentDataContainer().set(
                Keys.furnitureSlot(),
                PersistentDataType.STRING,
                slotId);

        this.displayStandId = display.getUniqueId();
        this.currentItem = item;
        syncDisplayToParent(parentDisplay, data, false);
    }

    public void removeDisplayStand(World world) {
        if (displayStandId != null && world != null) {
            world.getEntities().stream()
                    .filter(e -> e instanceof ItemDisplay && e.getUniqueId().equals(displayStandId))
                    .forEach(e -> e.remove());
        }
        displayStandId = null;
        currentItem = null;
    }
}
