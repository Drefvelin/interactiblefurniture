package net.tfminecraft.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;

import me.Plugins.TLibs.Utils.TabCleaner;
import net.tfminecraft.InteractibleFurniture;

public final class IfCommand implements CommandExecutor, TabCompleter {

    private static final String RELOAD_PERMISSION = "interactiblefurniture.reload";

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || !args[0].equalsIgnoreCase("reload")) {
            sender.sendMessage("Usage: /if reload");
            return true;
        }
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

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && sender.hasPermission(RELOAD_PERMISSION)) {
            List<String> completions = new ArrayList<>();
            completions.add("reload");
            TabCleaner.cleanTab(completions, args);
            return completions;
        }
        return List.of();
    }
}
