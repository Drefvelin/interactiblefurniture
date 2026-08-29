package net.tfminecraft.furniture;

import java.util.List;

import org.bukkit.Location;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import me.Plugins.TLibs.TLibs;
import net.tfminecraft.furniture.data.DisplayData;

/**
 * Immutable yaml slot definition from {@link FurnitureLoader}.
 */
public final class SlotDefinition {
    private final String id;
    private final int layer;
    private final int row;
    private final int col;
    private final Vector offset;
    private final List<String> whitelist;
    private final Vector displayRotation;
    private final Vector displayScale;
    private final Vector displayPosition;
    private final boolean interactible;

    public SlotDefinition(
            String id, int layer, int row, int col,
            Vector offset, List<String> whitelist,
            Vector displayRotation, Vector displayScale,
            Vector displayPosition, boolean interactible) {
        this.id = id;
        this.layer = layer;
        this.row = row;
        this.col = col;
        this.offset = offset != null ? offset.clone() : new Vector(0, 0, 0);
        this.whitelist = whitelist != null ? List.copyOf(whitelist) : List.of();
        this.interactible = interactible;
        this.displayRotation = displayRotation != null ? displayRotation.clone() : new Vector(0, 0, 0);
        this.displayScale = displayScale != null ? displayScale.clone() : new Vector(1, 1, 1);
        this.displayPosition = displayPosition != null ? displayPosition.clone() : new Vector(0, 0, 0);
    }

    public String getId() { return id; }
    public int getLayer() { return layer; }
    public int getRow() { return row; }
    public int getCol() { return col; }
    public Vector getOffset() { return offset.clone(); }
    public List<String> getWhitelist() { return whitelist; }
    public Vector getDisplayRotation() { return displayRotation.clone(); }
    public Vector getDisplayScale() { return displayScale.clone(); }
    public Vector getDisplayPosition() { return displayPosition.clone(); }
    public boolean isInteractible() { return interactible; }

    public boolean isItemAllowed(String itemPath) {
        return whitelist.contains(itemPath);
    }

    public boolean canInteract(ItemStack item) {
        if (!interactible) return false;
        if (item == null || item.getType().isAir()) return true;
        return isItemAllowed(item);
    }

    public boolean isItemAllowed(ItemStack item) {
        if (!interactible) return false;
        if (item == null || item.getType().isAir()) return false;
        for (String s : whitelist) {
            if (TLibs.getItemAPI().getChecker().checkItemWithPath(item, s)) {
                return true;
            }
        }
        return false;
    }

    public Transformation buildFinalTransformation(ItemDisplay parentDisplay, DisplayData data) {
        if (data == null) data = new DisplayData();

        Transformation parentT = parentDisplay.getTransformation();
        Quaternionf parentRot = new Quaternionf(parentT.getLeftRotation());
        Vector3f parentScale = new Vector3f(parentT.getScale());

        Quaternionf slotBaseRot = new Quaternionf()
                .rotateY((float) Math.toRadians(displayRotation.getY()))
                .rotateX((float) Math.toRadians(displayRotation.getX()))
                .rotateZ((float) Math.toRadians(displayRotation.getZ()));

        Quaternionf dataRot = new Quaternionf()
                .rotateY((float) Math.toRadians(data.getyRot()))
                .rotateX((float) Math.toRadians(data.getxRot()))
                .rotateZ((float) Math.toRadians(data.getzRot()));

        Quaternionf finalRot = parentRot.mul(slotBaseRot).mul(dataRot);

        Vector3f finalScale = parentScale
                .mul((float) displayScale.getX(), (float) displayScale.getY(), (float) displayScale.getZ())
                .mul(data.getxScale(), data.getyScale(), data.getzScale());

        return new Transformation(
                new Vector3f(0, 0, 0),
                finalRot,
                finalScale,
                new Quaternionf()
        );
    }

    public Location computeDisplayLocationFromTransform(ItemDisplay parentDisplay, DisplayData data) {
        if (data == null) data = new DisplayData();

        Location baseLoc = parentDisplay.getLocation().clone();
        Transformation parentT = parentDisplay.getTransformation();
        Quaternionf parentRot = new Quaternionf(parentT.getLeftRotation());
        Vector3f parentTranslate = new Vector3f(parentT.getTranslation());

        baseLoc.add(parentTranslate.x, parentTranslate.y, parentTranslate.z);

        Vector3f off1 = new Vector3f(
                (float) offset.getX(),
                (float) offset.getY(),
                (float) offset.getZ()
        );
        parentRot.transform(off1);
        baseLoc.add(off1.x, off1.y, off1.z);

        Vector3f off2 = new Vector3f(
                (float) displayPosition.getX(),
                (float) displayPosition.getY(),
                (float) displayPosition.getZ()
        );
        parentRot.transform(off2);
        baseLoc.add(off2.x, off2.y, off2.z);

        Vector3f dataOffset = new Vector3f(
                data.getxPos(),
                data.getyPos(),
                data.getzPos()
        );
        parentRot.transform(dataOffset);
        baseLoc.add(dataOffset.x, dataOffset.y, dataOffset.z);

        return baseLoc;
    }

    public Location computeDisplayLocation(Location baseLocation, ItemDisplay parentDisplay, DisplayData data) {
        if (data == null) data = new DisplayData();

        Location slotLoc = baseLocation.clone();
        Transformation parentT = parentDisplay.getTransformation();
        Quaternionf parentRot = new Quaternionf(parentT.getLeftRotation());

        Vector3f baseOffset = new Vector3f(
                (float) offset.getX(),
                (float) offset.getY(),
                (float) offset.getZ()
        );
        parentRot.transform(baseOffset);
        slotLoc.add(baseOffset.x, baseOffset.y, baseOffset.z);

        Vector3f fineOffset = new Vector3f(
                (float) displayPosition.getX(),
                (float) displayPosition.getY(),
                (float) displayPosition.getZ()
        );
        parentRot.transform(fineOffset);
        slotLoc.add(fineOffset.x, fineOffset.y, fineOffset.z);

        Vector3f dataOffset = new Vector3f(
                data.getxPos(),
                data.getyPos(),
                data.getzPos()
        );
        parentRot.transform(dataOffset);
        slotLoc.add(dataOffset.x, dataOffset.y, dataOffset.z);

        return slotLoc;
    }
}
