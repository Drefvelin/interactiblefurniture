package net.tfminecraft.furniture;

import java.util.Optional;

public final class PlacedFurnitureSlot {
    private Furniture parent;
    private final String slotId;
    private Furniture nested;

    public PlacedFurnitureSlot(Furniture parent, String slotId) {
        this.parent = parent;
        this.slotId = slotId;
    }

    public String getId() {
        return slotId;
    }

    public Furniture getParent() {
        return parent;
    }

    public void setParent(Furniture parent) {
        this.parent = parent;
    }

    public Furniture getNested() {
        return nested;
    }

    public void setNested(Furniture nested) {
        this.nested = nested;
    }

    public Optional<SlotDefinition> getDefinition() {
        if (parent == null || parent.getType() == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(parent.getType().getSlot(slotId));
    }
}
