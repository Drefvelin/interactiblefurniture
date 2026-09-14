package net.tfminecraft.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

import net.tfminecraft.furniture.Furniture;
import net.tfminecraft.furniture.SlotDefinition;

public class FurnitureSlotFurnitureAddEvent extends PlayerEvent implements Cancellable {
    private static final HandlerList handlers = new HandlerList();
    private final Furniture furniture;
    private final SlotDefinition slot;
    private final Furniture nested;
    private boolean cancelled;

    public FurnitureSlotFurnitureAddEvent(Player player, Furniture furniture, SlotDefinition slot, Furniture nested) {
        super(player);
        this.furniture = furniture;
        this.slot = slot;
        this.nested = nested;
    }

    public Furniture getFurniture() {
        return furniture;
    }

    public SlotDefinition getSlot() {
        return slot;
    }

    public Furniture getNested() {
        return nested;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }
}
