package net.tfminecraft.command;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import me.Plugins.TLibs.Utils.TabCleaner;
import net.tfminecraft.InteractibleFurniture;
import net.tfminecraft.debug.InteractionDebugService;
import net.tfminecraft.furniture.Furniture;
import net.tfminecraft.furniture.FurnitureType;
import net.tfminecraft.furniture.PlacedFurnitureSlot;
import net.tfminecraft.furniture.SlotDefinition;
import net.tfminecraft.furniture.SlotType;
import net.tfminecraft.manager.handlers.FurnitureAttachmentHandler;

public final class IfCommand implements CommandExecutor, TabCompleter {

    private static final String RELOAD_PERMISSION = "interactiblefurniture.reload";
    private static final String DEBUG_PERMISSION = "interactiblefurniture.debug";
    private static final double SEARCH_RADIUS = 8.0;

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            return handleReload(sender);
        }

        if (args[0].equalsIgnoreCase("nested")) {
            return handleNested(sender, args);
        }

        if (args[0].equalsIgnoreCase("debug")) {
            return handleDebug(sender, args);
        }

        sendUsage(sender);
        return true;
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage("Usage: /if reload | /if nested attach | /if nested detach | /if debug <on|off>");
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission(RELOAD_PERMISSION)) {
            sender.sendMessage("You do not have permission to reload InteractibleFurniture.");
            return true;
        }
        boolean ok = InteractibleFurniture.getInstance().reloadAll();
        if (ok) {
            sender.sendMessage("Reloaded InteractibleFurniture configs.");
        } else {
            sender.sendMessage("Reload failed. Check console.");
        }
        return true;
    }

    private boolean handleNested(CommandSender sender, String[] args) {
        if (!sender.hasPermission(RELOAD_PERMISSION)) {
            sender.sendMessage("You do not have permission to use nested furniture commands.");
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("Usage: /if nested attach | /if nested detach");
            return true;
        }

        if (args[1].equalsIgnoreCase("attach")) {
            return handleNestedAttach(player);
        }
        if (args[1].equalsIgnoreCase("detach")) {
            return handleNestedDetach(player);
        }

        player.sendMessage("Usage: /if nested attach | /if nested detach");
        return true;
    }

    private boolean handleNestedAttach(Player player) {
        Furniture carried = InteractibleFurniture.getInstance().getFurnitureManager().getByCarrier(player);
        if (carried == null) {
            player.sendMessage("Carry a furniture piece first (shift-right-click).");
            return true;
        }

        Furniture parent = findNearestFurniture(player, false);
        if (parent == null) {
            player.sendMessage("No nearby parent furniture found.");
            return true;
        }

        String slotId = findFirstEmptyFurnitureSlot(parent);
        if (slotId == null) {
            player.sendMessage("No empty furniture slot on nearest parent.");
            return true;
        }

        if (carried.isCarried()) {
            carried.stopCarrying();
        }

        if (FurnitureAttachmentHandler.attach(parent, slotId, carried, player)) {
            player.sendMessage("Attached " + carried.getId() + " to " + parent.getId() + " slot " + slotId + ".");
        } else {
            player.sendMessage("Attach failed.");
        }
        return true;
    }

    private boolean handleNestedDetach(Player player) {
        Furniture parent = findNearestFurnitureWithNested(player);
        if (parent == null) {
            player.sendMessage("No nearby parent with nested furniture found.");
            return true;
        }

        String slotId = findFirstOccupiedFurnitureSlot(parent);
        if (slotId == null) {
            player.sendMessage("No nested furniture to detach.");
            return true;
        }

        Furniture nested = FurnitureAttachmentHandler.detach(parent, slotId, player);
        if (nested == null) {
            player.sendMessage("Detach failed.");
            return true;
        }

        nested.carry(player);
        player.sendMessage("Detached " + nested.getId() + " from " + parent.getId() + " slot " + slotId + ".");
        return true;
    }

    private boolean handleDebug(CommandSender sender, String[] args) {
        if (!sender.hasPermission(DEBUG_PERMISSION)) {
            sender.sendMessage("You do not have permission to use furniture debug.");
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (args.length < 2) {
            player.sendMessage("Usage: /if debug <on|off>");
            return true;
        }

        InteractionDebugService debug = InteractibleFurniture.getInstance().getInteractionDebugService();
        if (args[1].equalsIgnoreCase("on")) {
            if (debug.isEnabled(player)) {
                player.sendMessage("Furniture interaction debug is already on.");
                return true;
            }
            debug.enable(player);
            player.sendMessage("Furniture interaction debug enabled.");
            return true;
        }
        if (args[1].equalsIgnoreCase("off")) {
            if (!debug.isEnabled(player)) {
                player.sendMessage("Furniture interaction debug is already off.");
                return true;
            }
            debug.disable(player);
            player.sendMessage("Furniture interaction debug disabled.");
            return true;
        }

        player.sendMessage("Usage: /if debug <on|off>");
        return true;
    }

    private Furniture findNearestFurniture(Player player, boolean requireNested) {
        Furniture nearest = null;
        double nearestDist = Double.MAX_VALUE;
        for (Furniture furniture : InteractibleFurniture.getInstance().getFurnitureManager().getPlacedFurniture().values()) {
            if (furniture.isCarried() || furniture.isAttached()) {
                continue;
            }
            if (requireNested && !furniture.hasNestedFurniture()) {
                continue;
            }
            if (furniture.getLoc().getWorld() == null
                    || !furniture.getLoc().getWorld().equals(player.getWorld())) {
                continue;
            }
            double dist = furniture.getLoc().distanceSquared(player.getLocation());
            if (dist > SEARCH_RADIUS * SEARCH_RADIUS || dist >= nearestDist) {
                continue;
            }
            nearest = furniture;
            nearestDist = dist;
        }
        return nearest;
    }

    private Furniture findNearestFurnitureWithNested(Player player) {
        return findNearestFurniture(player, true);
    }

    private String findFirstEmptyFurnitureSlot(Furniture parent) {
        FurnitureType type = parent.getType();
        if (type == null) {
            return null;
        }
        return type.getSlots().values().stream()
                .filter(slot -> slot.getSlotType() == SlotType.FURNITURE)
                .filter(slot -> !parent.hasActiveFurnitureSlot(slot.getId()))
                .map(SlotDefinition::getId)
                .sorted()
                .findFirst()
                .orElse(null);
    }

    private String findFirstOccupiedFurnitureSlot(Furniture parent) {
        return parent.getActiveFurnitureSlots().values().stream()
                .filter(slot -> slot.getNested() != null)
                .map(PlacedFurnitureSlot::getId)
                .sorted()
                .findFirst()
                .orElse(null);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            if (sender.hasPermission(RELOAD_PERMISSION)) {
                completions.add("reload");
                completions.add("nested");
            }
            if (sender.hasPermission(DEBUG_PERMISSION)) {
                completions.add("debug");
            }
            TabCleaner.cleanTab(completions, args);
            return completions;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("nested") && sender.hasPermission(RELOAD_PERMISSION)) {
            List<String> completions = new ArrayList<>();
            completions.add("attach");
            completions.add("detach");
            TabCleaner.cleanTab(completions, args);
            return completions;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("debug") && sender.hasPermission(DEBUG_PERMISSION)) {
            List<String> completions = new ArrayList<>();
            completions.add("on");
            completions.add("off");
            TabCleaner.cleanTab(completions, args);
            return completions;
        }
        return List.of();
    }
}
