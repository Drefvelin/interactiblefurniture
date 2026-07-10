package net.tfminecraft.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.util.Vector;

import net.tfminecraft.furniture.Furniture;
import net.tfminecraft.furniture.FurnitureSlot;

public class FurnitureInteractEvent extends PlayerEvent implements Cancellable{
    private static final HandlerList handlers = new HandlerList();
    private final Furniture furniture;
    private final FurnitureSlot hitSlot;
    private final Vector clickPoint;
    private boolean cancelled;

    public FurnitureInteractEvent(Player player, Furniture furniture, FurnitureSlot hitSlot, Vector clickPoint) {
        super(player);
        this.furniture = furniture;
        this.hitSlot = hitSlot;
        this.clickPoint = clickPoint;
    }

    public FurnitureInteractEvent(Player player, Furniture furniture) {
        this(player, furniture, null, null);
    }

    public Furniture getFurniture() {
        return furniture;
    }

    public FurnitureSlot getHitSlot() {
        return hitSlot;
    }

    public Vector getClickPoint() {
        return clickPoint;
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
