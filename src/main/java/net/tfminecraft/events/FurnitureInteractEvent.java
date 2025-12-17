package net.tfminecraft.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

import net.tfminecraft.furniture.Furniture;

public class FurnitureInteractEvent extends PlayerEvent implements Cancellable{
    private static final HandlerList handlers = new HandlerList();
    private final Furniture furniture;
    private boolean cancelled;

    public FurnitureInteractEvent(Player player, Furniture furniture) {
        super(player);
        this.furniture = furniture;
    }

    public Furniture getFurniture() {
        return furniture;
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
    public void setCancelled(boolean b) {
        cancelled = b;
    }
}