package net.tfminecraft.utils;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import net.tfminecraft.furniture.FurnitureSlot;

public class DebugUtils {
    public static void sendSlotDebug(Player player, FurnitureSlot slot, Vector slotPoint, double dist) {
        player.sendMessage(ChatColor.GRAY + "[DEBUG] Slot " + slot.getId() +
            " at " + String.format("%.2f %.2f %.2f", 
                slotPoint.getX(), slotPoint.getY(), slotPoint.getZ()));
    }

    public static void sendNewClosestDebug(Player player, FurnitureSlot slot, double dist) {
        player.sendMessage(ChatColor.YELLOW + "[DEBUG] New closest: " + slot.getId() +
            " (dist=" + String.format("%.2f", dist) + ")");
    }

    public static void sendClickPointDebug(Player player, Vector clickPoint) {
        player.sendMessage(ChatColor.GREEN + "[DEBUG] Click point: " + 
            String.format("%.2f %.2f %.2f",
                clickPoint.getX(), clickPoint.getY(), clickPoint.getZ()));
    }
}