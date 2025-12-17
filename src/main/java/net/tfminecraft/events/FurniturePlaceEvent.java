package net.tfminecraft.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import net.tfminecraft.furniture.Furniture;

public class FurniturePlaceEvent extends Event implements Cancellable{
    private static final HandlerList handlers = new HandlerList();
    private final Furniture furniture;
    private final Player player;
    private boolean cancelled;

    public FurniturePlaceEvent(Furniture furniture, Player player) {
        this.furniture = furniture;
        this.player = player;
    }

    public Furniture getFurniture() {
        return furniture;
    }

    public boolean hasPlayer() {
        return player != null;
    }

    public Player getPlayer() {
        return player;
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
