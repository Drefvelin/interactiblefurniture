package net.tfminecraft.furniture;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Display;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import me.Plugins.TLibs.TLibs;
import net.tfminecraft.furniture.data.DisplayData;

import java.util.List;
import java.util.UUID;

public class FurnitureSlot {
    private Furniture f;
    private final String id;
    private final int layer;
    private final int row;
    private final int col;
    private final Vector offset;
    private final List<String> whitelist;
    private final Vector displayRotation;
    private final Vector displayScale;
    private final Vector displayPosition;
    private DisplayData currentData;

    private ItemStack currentItem;
    private UUID displayStandId;

    private final boolean interactible;

    public FurnitureSlot(
            String id, int layer, int row, int col,
            Vector offset, List<String> whitelist,
            Vector displayRotation, Vector displayScale,
            Vector displayPosition, Furniture f, boolean interactible) {

        this.id = id;
        this.layer = layer;
        this.row = row;
        this.col = col;
        this.offset = offset;
        this.whitelist = whitelist;
        this.interactible = interactible;
        this.displayRotation = displayRotation;
        this.displayScale = displayScale;
        this.displayPosition = displayPosition;
        this.currentItem = null;
        this.displayStandId = null;
        this.f = f;
    }

    public String getId() { return id; }
    public int getLayer() { return layer; }
    public int getRow() { return row; }
    public int getCol() { return col; }
    public Vector getOffset() { return offset; }
    public List<String> getWhitelist() { return whitelist; }
    public Vector getDisplayRotation() { return displayRotation; }
    public Vector getDisplayScale() { return displayScale; }
    public Vector getDisplayPosition() { return displayPosition; }
    public ItemStack getCurrentItem() { return currentItem; }
    public boolean isInteractible() { return interactible; }

    public void updateDisplay() {
        if(displayStandId == null) return;
        ItemDisplay disp = (ItemDisplay) Bukkit.getEntity(displayStandId);
        if (disp == null) return;
        if(currentItem == null || currentItem.getType() == Material.AIR) {
            disp.remove();
            displayStandId = null;
            f.removeActiveSlot(id);
            return;
        }
        disp.setItemStack(currentItem);
    }

    public void setCurrentItem(ItemStack currentItem) {
        this.currentItem = currentItem;
        updateDisplay();
    }

    public UUID getDisplayStandId() { return displayStandId; }

    public void setDisplayStandId(UUID displayStandId) {
        this.displayStandId = displayStandId;
    }

    public boolean isItemAllowed(String itemPath) {
        return whitelist.contains(itemPath);
    }

    public boolean canInteract(ItemStack item) {
        if (!interactible) return false;
        if (item == null || item.getType() == Material.AIR) return true;
        for (String s : whitelist)
            if (TLibs.getItemAPI().getChecker().checkItemWithPath(item, s))
                return true;
        return false;
    }

    public boolean isItemAllowed(ItemStack item) {
        if (!interactible) return false;
        if (item == null || item.getType() == Material.AIR) return false;
        for (String s : whitelist)
            if (TLibs.getItemAPI().getChecker().checkItemWithPath(item, s))
                return true;
        return false;
    }

    public void forceModel(ItemStack i) {
        if (f != null && displayStandId == null) {
            spawnDisplayStand(f.getLoc(), i, (ItemDisplay) Bukkit.getEntity(f.getEntityId()), null);
        } else {
            this.currentItem = i;
        }
        updateDisplay();
    }

    public void setModel(ItemStack i) {
        this.currentItem = i;
    }

    public void clearModel() {
        this.currentItem = null;
        updateDisplay();
    }

    /**
     * Build the transformation WITHOUT translation.
     * Only rotation + scale belong here.
     */
    public Transformation buildFinalTransformation(ItemDisplay parentDisplay, DisplayData data) {

        if (data == null && currentData != null) data = currentData;
        else if (data == null) data = new DisplayData();
        currentData = data;

        Transformation parentT = parentDisplay.getTransformation();

        // Parent rotation + scale
        Quaternionf parentRot = new Quaternionf(parentT.getLeftRotation());
        Vector3f parentScale = new Vector3f(parentT.getScale());

        // Slot base rotation
        Quaternionf slotBaseRot = new Quaternionf()
                .rotateY((float) Math.toRadians(displayRotation.getY()))
                .rotateX((float) Math.toRadians(displayRotation.getX()))
                .rotateZ((float) Math.toRadians(displayRotation.getZ()));

        // DisplayData rotation
        Quaternionf dataRot = new Quaternionf()
                .rotateY((float) Math.toRadians(data.getyRot()))
                .rotateX((float) Math.toRadians(data.getxRot()))
                .rotateZ((float) Math.toRadians(data.getzRot()));

        Quaternionf finalRot = parentRot.mul(slotBaseRot).mul(dataRot);

        // Scale inheritance
        Vector3f finalScale = parentScale
                .mul((float) displayScale.getX(), (float) displayScale.getY(), (float) displayScale.getZ())
                .mul(data.getxScale(), data.getyScale(), data.getzScale());

        // NOTE: translation = 0 here; we’ll set it per-tick based on world-space diff
        return new Transformation(
                new Vector3f(0, 0, 0),
                finalRot,
                finalScale,
                new Quaternionf()
        );
    }

    public Location computeDisplayLocationFromTransform(ItemDisplay parentDisplay, DisplayData data) {

        if (data == null && currentData != null) data = currentData;
        else if (data == null) data = new DisplayData();
        currentData = data;

        Location baseLoc = parentDisplay.getLocation().clone();
        Transformation parentT = parentDisplay.getTransformation();

        // parent rotation & translation
        Quaternionf parentRot = new Quaternionf(parentT.getLeftRotation());
        Vector3f parentTranslate = new Vector3f(parentT.getTranslation());

        // base origin + parent translation (carry movement)
        baseLoc.add(parentTranslate.x, parentTranslate.y, parentTranslate.z);

        // Apply slot offset
        Vector3f off1 = new Vector3f(
                (float) offset.getX(),
                (float) offset.getY(),
                (float) offset.getZ()
        );
        parentRot.transform(off1);
        baseLoc.add(off1.x, off1.y, off1.z);

        // Apply slot fine position
        Vector3f off2 = new Vector3f(
                (float) displayPosition.getX(),
                (float) displayPosition.getY(),
                (float) displayPosition.getZ()
        );
        parentRot.transform(off2);
        baseLoc.add(off2.x, off2.y, off2.z);

        // Apply DisplayData positional overrides
        Vector3f dataOffset = new Vector3f(
                data.getxPos(),
                data.getyPos(),
                data.getzPos()
        );
        parentRot.transform(dataOffset);
        baseLoc.add(dataOffset.x, dataOffset.y, dataOffset.z);

        return baseLoc;
    }

    public void followParentTransform(ItemDisplay parentDisplay) {
        if (displayStandId == null) return;

        ItemDisplay slotDisp = (ItemDisplay) Bukkit.getEntity(displayStandId);
        if (slotDisp == null) return;

        // Use existing DisplayData or new default
        DisplayData data = (currentData != null) ? currentData : new DisplayData();

        // Where the slot SHOULD be in world space
        Location desiredLoc = computeDisplayLocationFromTransform(parentDisplay, data);

        float offset = (f.getType().getDisplayData().getyPos()*-1);
        // Apply the +0.1 vertical offset
        desiredLoc.add(0, offset, 0);

        // Current physical entity location
        Location originLoc = slotDisp.getLocation();

        // diff = world-space offset from current slot entity origin
        org.bukkit.util.Vector diff = desiredLoc.toVector().subtract(originLoc.toVector());

        // Build the slot transform (rotation+scale only)
        Transformation baseT = buildFinalTransformation(parentDisplay, data);

        // Overwrite translation with world-space delta
        baseT.getTranslation().set(
                (float) diff.getX(),
                (float) diff.getY(),
                (float) diff.getZ()
        );

        // Smooth interpolation
        slotDisp.setInterpolationDuration(2);
        slotDisp.setInterpolationDelay(0);
        slotDisp.setTransformation(baseT);
    }

    /**
     * Compute the display location (world space),
     * including:
     * - offset
     * - displayPosition
     * - DisplayData position
     */
    public Location computeDisplayLocation(Location baseLocation, ItemDisplay parentDisplay, DisplayData data) {

        if (data == null && currentData != null) data = currentData;
        else if (data == null) data = new DisplayData();
        currentData = data;

        Location slotLoc = baseLocation.clone();

        Transformation parentT = parentDisplay.getTransformation();
        Quaternionf parentRot = new Quaternionf(parentT.getLeftRotation());

        // Base slot offset
        Vector3f baseOffset = new Vector3f(
                (float) offset.getX(),
                (float) offset.getY(),
                (float) offset.getZ()
        );
        parentRot.transform(baseOffset);
        slotLoc.add(baseOffset.x, baseOffset.y, baseOffset.z);

        // slot displayPosition
        Vector3f fineOffset = new Vector3f(
                (float) displayPosition.getX(),
                (float) displayPosition.getY(),
                (float) displayPosition.getZ()
        );
        parentRot.transform(fineOffset);
        slotLoc.add(fineOffset.x, fineOffset.y, fineOffset.z);

        // DisplayData positional override
        Vector3f dataOffset = new Vector3f(
                data.getxPos(),
                data.getyPos(),
                data.getzPos()
        );
        parentRot.transform(dataOffset);
        slotLoc.add(dataOffset.x, dataOffset.y, dataOffset.z);

        return slotLoc;
    }

    public void applyDisplayData(DisplayData data) {
        if (displayStandId == null) return;
        if (f.isCarried()) return;

        ItemDisplay display = (ItemDisplay) Bukkit.getEntity(displayStandId);
        if (display == null) return;

        ItemDisplay parentDisplay = (ItemDisplay) Bukkit.getEntity(f.getEntityId());
        if (parentDisplay == null) return;

        // Compute the new world location
        Location newLoc = computeDisplayLocation(f.getLoc(), parentDisplay, data);

        // Move the display
        display.teleport(newLoc);

        // Apply transformation
        Transformation finalT = buildFinalTransformation(parentDisplay, data);
        display.setTransformation(finalT);
    }



    public void setRotation(DisplayData data) {
        applyDisplayData(data);
    }

    public void spawnDisplayStand(Location baseLocation, ItemStack item, ItemDisplay parentDisplay, DisplayData data) {
        if (data == null) data = new DisplayData();

        Location worldLoc = computeDisplayLocation(baseLocation, parentDisplay, data);

        ItemDisplay display = (ItemDisplay) worldLoc.getWorld().spawnEntity(worldLoc, EntityType.ITEM_DISPLAY);
        display.setItemStack(item.clone());
        display.setBillboard(Display.Billboard.FIXED);
        display.setBrightness(new Display.Brightness(
                baseLocation.getBlock().getLightLevel(),
                baseLocation.getBlock().getLightLevel()));
        display.setShadowRadius(0.1f);
        display.setShadowStrength(0.1f);
        display.setPersistent(true);
        display.setViewRange(50f);

        display.setTransformation(buildFinalTransformation(parentDisplay, data));

        this.displayStandId = display.getUniqueId();
        this.currentItem = item;
    }

    public void removeDisplayStand(org.bukkit.World world) {
        if (displayStandId != null) {
            world.getEntities().stream()
                    .filter(e -> e instanceof ItemDisplay && e.getUniqueId().equals(displayStandId))
                    .forEach(e -> e.remove());
            displayStandId = null;
            currentItem = null;
        }
    }
}
