package net.tfminecraft.furniture;

import java.util.Optional;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import net.tfminecraft.InteractibleFurniture;
import net.tfminecraft.loaders.FurnitureLoader;
import net.tfminecraft.manager.handlers.FurnitureBreakHandler;
import net.tfminecraft.manager.handlers.InteractionHandler;

/**
 * Represents a placed furniture instance in the world.
 *
 * - `typeId` references the furniture definition loaded by the loader.
 * - `entityId` is the UUID of the spawned ItemDisplay entity used to render the furniture.
 * - `barrierBlock` (optional) holds the barrier block placed when the furniture is "solid".
 */
public class Furniture {
    private final String id;
    private Location loc;
    private UUID entityId;
    private float yaw;
    private boolean persistedCarried;
    private java.util.List<Block> barrierBlocks = new java.util.ArrayList<>();
    private Location originBlockLocation;
    private org.bukkit.block.BlockFace originBlockFace;
    private final java.util.Map<String, PlacedSlot> activeSlots = new java.util.HashMap<>();
    private java.util.Map<String, Object> variables = new java.util.HashMap<>();
    private net.tfminecraft.furniture.data.ModelData modelOverride;

    private Player holder;
    private boolean firstCarryTick = true;
    private Location lastLoc = null;
    private Location baseResetLoc = null;
    private UUID interactionEntityId;

    public Furniture(String typeId, Location loc, UUID entityId) {
        this(typeId, loc, entityId, null, null);
    }

    public Furniture(String id, Location loc, UUID entityId, Location originBlockLocation, org.bukkit.block.BlockFace originBlockFace) {
        this.id = id;
        this.loc = loc;
        this.entityId = entityId;
        this.originBlockLocation = originBlockLocation;
        this.originBlockFace = originBlockFace;
    }

    public String getId() {
        return id;
    }

    public FurnitureType getType() {
        return FurnitureLoader.getByString(id);
    }

    public Location getLoc() {
        return loc;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public void setEntityId(UUID entityId) {
        this.entityId = entityId;
    }

    public float getYaw() {
        return yaw;
    }

    public void setYaw(float yaw) {
        this.yaw = yaw;
    }

    public boolean isPersistedCarried() {
        return persistedCarried;
    }

    public void setPersistedCarried(boolean persistedCarried) {
        this.persistedCarried = persistedCarried;
    }

    public UUID getInteractionEntityId() {
        return interactionEntityId;
    }

    public void setInteractionEntityId(UUID interactionEntityId) {
        this.interactionEntityId = interactionEntityId;
    }

    public void spawnInteractionEntity() {
        InteractionHandler.spawnInteraction(this);
    }

    public void removeInteractionEntity() {
        InteractionHandler.removeInteraction(this);
    }

    public void updateInteractionPosition() {
        InteractionHandler.updateInteractionPosition(this);
    }

    public java.util.List<Block> getBarrierBlocks() {
        return barrierBlocks;
    }

    public void addBarrierBlock(Block b) {
        if (b != null) barrierBlocks.add(b);
    }

    public void clearBarrierBlocks() {
        barrierBlocks.clear();
    }

    public Optional<Location> getOriginBlockLocation() {
        return Optional.ofNullable(originBlockLocation);
    }
    public Optional<org.bukkit.block.BlockFace> getOriginBlockFace() {
        return Optional.ofNullable(originBlockFace);
    }

    public boolean isOriginBlock(org.bukkit.block.Block b) {
        if (originBlockLocation == null) return false;
        Location l = b.getLocation();
        return l.getWorld().equals(originBlockLocation.getWorld())
            && l.getBlockX() == originBlockLocation.getBlockX()
            && l.getBlockY() == originBlockLocation.getBlockY()
            && l.getBlockZ() == originBlockLocation.getBlockZ();
    }

    public boolean isCarried() {
        return holder != null;
    }

    public Player getHolder() {
        return holder;
    }

    public void carry(Player p) {
        if (!barrierBlocks.isEmpty()) return;

        originBlockFace = null;
        originBlockLocation = null;
        removeInteractionEntity();
        holder = p;
        persistedCarried = true;

        firstCarryTick = true;
        baseResetLoc = p.getLocation().clone();
        InteractibleFurniture.getInstance().getFurnitureManager().pulse(p);
        InteractibleFurniture.getInstance().getFurnitureManager().persistFurniture(this);
    }

    public void setFromDisplay(ItemDisplay newDisplay, Block block, BlockFace face) {
        // Update stored location
        this.loc = newDisplay.getLocation().clone();

        // Update origin block & face (for clicking interaction, breaking logic, etc)
        this.originBlockLocation = block.getLocation().clone();
        this.originBlockFace = face;

        // Stop carrying
        this.holder = null;
        this.persistedCarried = false;

        ItemDisplay display = (ItemDisplay) Bukkit.getEntity(entityId);
        display.setTransformation(newDisplay.getTransformation());
        display.teleport(newDisplay.getLocation());

        // Reset slot origins (necessary so slots follow correctly after placement)
        for (PlacedSlot slot : activeSlots.values()) {
            SlotDefinition def = slot.getDefinition();
            if (def == null) continue;
            Location newLoc = def.computeDisplayLocation(loc, display, null);
            Transformation t = def.buildFinalTransformation(display, null);
            ItemDisplay slotDisplay = (ItemDisplay) Bukkit.getEntity(slot.getDisplayStandId());
            if(slotDisplay == null) continue;
            slotDisplay.teleport(newLoc);
            slotDisplay.setTransformation(t);
        }
        newDisplay.remove();
        spawnInteractionEntity();
    }

    private Vector getHolderMovement() {
        
        Vector movement = new Vector(0, 0, 0);

        if (lastLoc != null) {
            movement = holder.getLocation().toVector().subtract(lastLoc.toVector());
        }
        movement.setY(0);
        lastLoc = holder.getLocation();
        return movement;
    }

    public void tick() {
        if (!isCarried()) return;

        ItemDisplay base = (ItemDisplay) Bukkit.getEntity(entityId);
        if (base == null) return;

        Location holderLoc = holder.getLocation();
        Vector velocity = getHolderMovement();
        Vector dir = holderLoc.getDirection().clone().setY(0).normalize();
        Location target = holderLoc.clone()
                .add(dir.multiply(0.7+velocity.length()*2))
                .add(0, 1.5, 0);

        target.setPitch(0);

        // compute translation offset relative to origin
        Vector offset = target.toVector().subtract(base.getLocation().toVector());

        if (firstCarryTick) {
            firstCarryTick = false;
            return;
        }

        Transformation oldT = base.getTransformation();
        Transformation t = new Transformation(
                oldT.getTranslation(),     // replaced in a moment
                oldT.getLeftRotation(),
                oldT.getScale(),
                oldT.getRightRotation()
        );

        // translation
        t.getTranslation().set(
                (float) offset.getX(),
                (float) offset.getY(),
                (float) offset.getZ()
        );

        // rotation
        t.getLeftRotation().rotationYXZ(
            (float) Math.toRadians(-holderLoc.getYaw() + 180),
            0f,
            0f
        );

        // distance between player's new position and the original base location
        if (baseResetLoc != null && baseResetLoc.getWorld().equals(holderLoc.getWorld())) {
            // If furniture entity has drifted too far from holder
            if (base.getLocation().distanceSquared(holderLoc) > (64 * 64)) {
                remove(true);
                return;
            }
        }

        // only apply when change is meaningful
        if (shouldApplyTransform(oldT, t)) {
            base.setInterpolationDuration(2);
            base.setInterpolationDelay(0);
            base.setTransformation(t);

            for (PlacedSlot slot : activeSlots.values()) {
                slot.followParentTransform(base);
            }
            updateInteractionPosition();
        }
    }

    private boolean shouldApplyTransform(Transformation oldT, Transformation newT) {
        float dx = Math.abs(oldT.getTranslation().x() - newT.getTranslation().x());
        float dy = Math.abs(oldT.getTranslation().y() - newT.getTranslation().y());
        float dz = Math.abs(oldT.getTranslation().z() - newT.getTranslation().z());

        // translation threshold (≈ 1–2 cm)
        if (dx > 0.02f || dy > 0.02f || dz > 0.02f) return true;

        // rotation threshold
        Quaternionf o = oldT.getLeftRotation();
        Quaternionf n = newT.getLeftRotation();
        float w = Math.abs(o.w() - n.w());
        float x = Math.abs(o.x() - n.x());
        float y = Math.abs(o.y() - n.y());
        float z = Math.abs(o.z() - n.z());

        // skip extremely tiny rotation changes
        return (w > 0.01f || x > 0.01f || y > 0.01f || z > 0.01f);
    }

    /**
     * Checks whether the given block and face match the stored origin exactly.
     * If the stored face is null, only the block is matched.
     */
    public boolean matchesOrigin(org.bukkit.block.Block b, org.bukkit.block.BlockFace face) {
        if (!isOriginBlock(b)) return false;
        if (originBlockFace == null) return true; // no face stored, match by block only
        if (face == null) return false;
        return originBlockFace == face;
    }

    /**
     * Get all active slots (slots that have items placed in them)
     */
    public java.util.Map<String, PlacedSlot> getActiveSlots() {
        return activeSlots;
    }

    public Optional<PlacedSlot> getActiveSlot(String slotId) {
        return Optional.ofNullable(activeSlots.get(slotId));
    }

    public PlacedSlot getOrCreatePlacedSlot(String slotId) {
        PlacedSlot existing = activeSlots.get(slotId);
        if (existing != null) {
            return existing;
        }
        PlacedSlot created = new PlacedSlot(this, slotId);
        activeSlots.put(slotId, created);
        return created;
    }

    public void addActiveSlot(PlacedSlot slot) {
        slot.setFurniture(this);
        activeSlots.put(slot.getId(), slot);
    }

    public boolean hasActiveSlot(String s) {
        return activeSlots.containsKey(s);
    }

    public void removeActiveSlot(String slotId) {
        removeActiveSlot(slotId, true);
    }

    public void removeActiveSlot(String slotId, boolean removeDisplay) {
        PlacedSlot slot = activeSlots.remove(slotId);
        if (slot != null && removeDisplay) {
            slot.removeDisplayStand(loc.getWorld());
        }
    }

    public void clearActiveSlots() {
        for (PlacedSlot slot : activeSlots.values()) {
            slot.removeDisplayStand(loc.getWorld());
        }
        activeSlots.clear();
    }

    public java.util.Map<String, Object> getVariables() {
        return variables;
    }

    public void setVariables(java.util.Map<String, Object> variables) {
        this.variables = variables != null ? variables : new java.util.HashMap<>();
    }

    public net.tfminecraft.furniture.data.ModelData getModelOverride() {
        return modelOverride;
    }

    public void setModelOverride(net.tfminecraft.furniture.data.ModelData modelOverride) {
        this.modelOverride = modelOverride;
    }

    public net.tfminecraft.furniture.data.ModelData getCurrentModelData() {
        if (modelOverride != null) {
            return modelOverride;
        }
        FurnitureType type = getType();
        return type != null ? type.getData().getModelData() : null;
    }

    public void remove(boolean dropslots) {
        if(isCarried()) loc = holder.getLocation();
        // Also drop the furniture if needed, handled by your existing code
        FurnitureBreakHandler.removeFurniture(
                entityId, 
                InteractibleFurniture.getInstance().getFurnitureManager().getPlacedFurniture(), 
                null, 
                "remove-function-called", 
                dropslots
        );
    }
}
