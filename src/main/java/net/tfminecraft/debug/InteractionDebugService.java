package net.tfminecraft.debug;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import net.tfminecraft.InteractibleFurniture;

public final class InteractionDebugService implements Listener {

    private static final long TICK_INTERVAL = 10L;

    private final InteractibleFurniture plugin;
    private final Set<UUID> enabledPlayers = new HashSet<>();
    private BukkitTask task;

    public InteractionDebugService(InteractibleFurniture plugin) {
        this.plugin = plugin;
    }

    public boolean isEnabled(Player player) {
        return enabledPlayers.contains(player.getUniqueId());
    }

    public boolean enable(Player player) {
        if (!enabledPlayers.add(player.getUniqueId())) {
            return false;
        }
        ensureTaskRunning();
        return true;
    }

    public boolean disable(Player player) {
        if (!enabledPlayers.remove(player.getUniqueId())) {
            return false;
        }
        if (enabledPlayers.isEmpty()) {
            stopTask();
        }
        return true;
    }

    public void stop() {
        enabledPlayers.clear();
        stopTask();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        if (enabledPlayers.remove(id) && enabledPlayers.isEmpty()) {
            stopTask();
        }
    }

    private void ensureTaskRunning() {
        if (task != null) {
            return;
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, TICK_INTERVAL, TICK_INTERVAL);
    }

    private void stopTask() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tick() {
        for (UUID id : new HashSet<>(enabledPlayers)) {
            Player player = Bukkit.getPlayer(id);
            if (player == null || !player.isOnline()) {
                enabledPlayers.remove(id);
                continue;
            }
            InteractionDebugRenderer.renderFor(player);
        }
        if (enabledPlayers.isEmpty()) {
            stopTask();
        }
    }
}
