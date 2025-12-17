package net.tfminecraft.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.inventory.ItemStack;

import net.tfminecraft.furniture.Furniture;
import net.tfminecraft.furniture.FurnitureSlot;

public class FurnitureSlotItemTakeEvent extends PlayerEvent implements Cancellable {
    private static final HandlerList handlers = new HandlerList();
    private final Furniture furniture;
    private final FurnitureSlot slot;
    private ItemStack item;
    private boolean cancelled;

    public FurnitureSlotItemTakeEvent(Player player, Furniture furniture, FurnitureSlot slot, ItemStack item) {
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

    public void setItem(ItemStack i) {
        item = i;
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