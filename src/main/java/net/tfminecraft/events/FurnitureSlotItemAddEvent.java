package net.tfminecraft.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.inventory.ItemStack;

import net.tfminecraft.furniture.Furniture;
import net.tfminecraft.furniture.FurnitureSlot;
import net.tfminecraft.furniture.data.DisplayData;

public class FurnitureSlotItemAddEvent extends PlayerEvent implements Cancellable {
    private static final HandlerList handlers = new HandlerList();
    private final Furniture furniture;
    private final FurnitureSlot slot;
    private final ItemStack item;
    private boolean cancelled;
    private DisplayData display;

    public FurnitureSlotItemAddEvent(Player player, Furniture furniture, FurnitureSlot slot, ItemStack item) {
        super(player);
        this.furniture = furniture;
        this.slot = slot;
        this.item = item;
    }
    
    public Furniture getFurniture() {
        return furniture;
    }

    public FurnitureSlot getSlot() {
        return slot;
    }

    public ItemStack getItem() {
        return item;
    }

    public DisplayData getDisplayData() {
        return display;
    }

    public void setDisplayData(DisplayData display) {
        this.display = display;
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