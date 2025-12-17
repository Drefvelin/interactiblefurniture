package net.tfminecraft.manager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.xml.crypto.Data;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import net.tfminecraft.InteractibleFurniture;
import net.tfminecraft.database.Database;
import net.tfminecraft.events.FurnitureInteractEvent;
import net.tfminecraft.furniture.Furniture;
import net.tfminecraft.furniture.FurnitureSlot;
import net.tfminecraft.furniture.FurnitureType;
import net.tfminecraft.manager.handlers.FurniturePlacementHandler;
import net.tfminecraft.manager.handlers.FurnitureBreakHandler;
import net.tfminecraft.manager.handlers.SlotInteractionHandler;

/**
 * Manages placement and breaking of simple furniture instances.
 *
 * - Furniture is represented visually by an invisible ArmorStand.
 * - If a furniture type has `solid: true` a BARRIER block is placed at the furniture block.
 */
public class FurnitureManager implements Listener {
    private Database database;
    private final HashMap<Player, Long> cooldown = new HashMap<>();
    private final Map<UUID, Furniture> placed = new HashMap<>();

    public Database getDatabase() {
        return database;
    }

    public Furniture getByCarrier(Player p) {
        for(Furniture f : placed.values()) {
            if(!f.isCarried()) continue;
            if(f.getHolder().equals(p)) return f;
        }
        return null;
    }

    public void start() {
        this.database = new Database();
        saveCycle();
        lightCycle();
    }

    public void saveCycle() {
        new BukkitRunnable() {
            @Override
            public void run() {
                saveAllLoadedChunks();
            }
        }.runTaskTimerAsynchronously(InteractibleFurniture.getInstance(), 6000L, 6000L); // Every 5 minutes
    }

    public void lightCycle() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Furniture f : placed.values()) {
                    f.tick();
                    Entity ent = Bukkit.getEntity(f.getEntityId());
                    if (!(ent instanceof ItemDisplay display)) continue;

                    Block block = f.getLoc().getBlock();
                    int blockLight = block.getLightFromBlocks();
                    int skyLight = block.getLightFromSky();

                    display.setBrightness(new ItemDisplay.Brightness(blockLight, skyLight));

                    for (FurnitureSlot slot : f.getActiveSlots().values()) {
                        UUID displayId = slot.getDisplayStandId();
                        if (displayId == null) continue;

                        Entity slotEnt = Bukkit.getEntity(displayId);
                        if (!(slotEnt instanceof ItemDisplay slotDisplay)) continue;

                        slotDisplay.setBrightness(new ItemDisplay.Brightness(blockLight, skyLight));
                    }
                }
            }
        }.runTaskTimer(InteractibleFurniture.getInstance(), 0L, 1L); // sync, once per second
    }


    public Furniture getByLocation(Location loc) {
        for (Furniture f : placed.values()) {
            if(f.isCarried()) continue;
            if (f.getLoc().equals(loc)) {
                return f;
            }
        }
        return null;
    }

    private boolean isValidInteraction(Furniture f, Block clicked, BlockFace face) {
        FurnitureType type = f.getType();
        if (type == null) return false;

        if (type.isSolid()) {
            // For solid furniture, check if they clicked any barrier block
            return f.getBarrierBlocks().contains(clicked);
        } else {
            // For non-solid furniture, only accept clicks on the attached block face
            return f.isOriginBlock(clicked) && f.matchesOrigin(clicked, face);
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player p = e.getPlayer();
        Block clicked = e.getClickedBlock();
        if (clicked == null) return;

        // Cooldown check
        if (cooldown.containsKey(p)) {
            long last = cooldown.get(p);
            if (System.currentTimeMillis() < last) {
                // Still on cooldown
                return;
            }
        }
        // Set new cooldown (200 ms from now)
        cooldown.put(p, System.currentTimeMillis() + 200);

        // --- NEW: placing furniture while carrying ---
        Furniture carried = null;
        for (Furniture f : placed.values()) {
            if (f.isCarried() && f.getHolder().equals(p)) {
                carried = f;
                break;
            }
        }

        if (carried != null) {
            // Player right-clicked while carrying → attempt placement
            boolean placedCarried = FurniturePlacementHandler.placeCarriedFurniture(p, clicked, e.getBlockFace(), carried, placed);
            if (placedCarried) {
                e.setCancelled(true);
                return;
            }
        }

        // First check if they clicked a furniture with slots
        for (Furniture f : placed.values()) {
            if (!isValidInteraction(f, clicked, e.getBlockFace())) continue;

            Entity furnitureEntity = Bukkit.getEntity(f.getEntityId());
            FurnitureType type = f.getType();
            if (type == null) continue;

            FurnitureInteractEvent event = new FurnitureInteractEvent(p, f);
            Bukkit.getPluginManager().callEvent(event);
            if(event.isCancelled()) return;

            if(type.canPickup() && p.getInventory().getItemInMainHand().getType().equals(Material.AIR) && f.getActiveSlots().isEmpty()) {
                // Attempt to pick up the furniture
                FurnitureBreakHandler.removeFurniture(f.getEntityId(), placed, p, "picked-up");
                e.setCancelled(true);
                return;
            }


            // Handle slot interaction if furniture has slots
            if (!type.getSlots().isEmpty()) {
                boolean handled = SlotInteractionHandler.handleSlotInteraction(p, clicked, e.getAction(), f, furnitureEntity, e.getBlockFace());
                if (handled) {
                    e.setCancelled(true);
                    return;
                }
            }
        }

        // If we get here, they weren't interacting with a slot
        // Only process right-clicks for furniture placement
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack held = p.getInventory().getItemInMainHand();
        if (held == null || held.getType() == Material.AIR) return;
        
        boolean handled = FurniturePlacementHandler.handlePlacement(p, clicked, e.getBlockFace(), held, placed);
        if (handled) {
            e.setCancelled(true);
        }
	}


    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        Block broken = e.getBlock();
        Player p = e.getPlayer();

        // Find all furniture that need to be removed (either directly or due to connected barriers)
        Set<UUID> toRemove = new HashSet<>();
        Set<Block> connectedBarriers = new HashSet<>();

        // First pass - find directly affected furniture and collect their barrier locations
        for (Map.Entry<UUID, Furniture> en : placed.entrySet()) {
            Furniture f = en.getValue();
            boolean affected = false;

            // Check barrier blocks first
            for (Block b : f.getBarrierBlocks()) {
                if (b.equals(broken)) {
                    affected = true;
                }
                // Add all barrier locations to our set
                connectedBarriers.add(b);
            }

            // Check origin block
            if (!affected && f.isOriginBlock(broken)) {
                affected = true;
            }

            if (affected) {
                toRemove.add(en.getKey());
            }
        }

        // Second pass - find furniture with barriers at the same locations
        if (!connectedBarriers.isEmpty()) {
            for (Map.Entry<UUID, Furniture> en : placed.entrySet()) {
                if (toRemove.contains(en.getKey())) continue; // Skip already marked furniture

                Furniture f = en.getValue();
                for (Block b : f.getBarrierBlocks()) {
                    if (connectedBarriers.contains(b)) {
                        toRemove.add(en.getKey());
                        break;
                    }
                }
            }
        }

        if (!toRemove.isEmpty()) {
            for (UUID id : toRemove) {
                FurnitureBreakHandler.removeFurniture(id, placed, p, "attached-block-broken");
            }
            p.sendMessage(ChatColor.RED + "[Furniture] Removed " + toRemove.size() + " furniture(s) attached to the broken block.");
        }
    }

    @EventHandler
    public void onPlayerLeftClick(PlayerInteractEvent e) {
        if (e.getAction() != Action.LEFT_CLICK_BLOCK) return;
        Block clicked = e.getClickedBlock();
        if (clicked == null) return;
        Player p = e.getPlayer();

        for (Map.Entry<UUID, Furniture> en : placed.entrySet()) {
            Furniture f = en.getValue();
            
            // Check for barrier block hits
            for (Block b : f.getBarrierBlocks()) {
                if (b.equals(clicked)) {
                    FurnitureBreakHandler.removeFurniture(en.getKey(), placed, p, "barrier-punched");
                    e.setCancelled(true);
                    return;
                }
            }

            // Check for non-solid furniture origin block hits
            FurnitureType ft = f.getType();
            if (ft == null || ft.isSolid()) continue;

            if (f.matchesOrigin(clicked, e.getBlockFace())) {
                FurnitureBreakHandler.removeFurniture(en.getKey(), placed, p, "attached-block-hit");
                e.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent e) {
        Entity target = e.getEntity();
        if (!(target instanceof ItemDisplay)) return;
        UUID id = target.getUniqueId();
        if (!placed.containsKey(id)) return;

        if (e.getDamager() instanceof Player) {
            Player p = (Player) e.getDamager();
            e.setCancelled(true);
            FurnitureBreakHandler.removeFurniture(id, placed, p, "entity-damage");
        }
    }

	public Map<UUID, Furniture> getPlacedFurniture() {
		return placed;
	}

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        List<Furniture> loaded = database.loadChunk(chunk);

        for (Furniture f : loaded) {
            // Respawn the entity if missing (optional safety)
            if (Bukkit.getEntity(f.getEntityId()) == null) {
                Bukkit.getPlayerExact("drefvelin").sendMessage("no display found by uuid");
                // TODO: implement respawn logic (spawn ItemDisplay again)
            }

            placed.put(f.getEntityId(), f);
        }

        if (!loaded.isEmpty()) {
            Bukkit.getLogger().info("[Furniture] Loaded " + loaded.size() + " furniture(s) in chunk " +
                    chunk.getX() + ", " + chunk.getZ());
        }
    }

    public void pulse(Player p) {
        cooldown.put(p, System.currentTimeMillis() + 200);
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        Chunk chunk = event.getChunk();

        // Collect furniture in this chunk
        Set<Furniture> inChunk = new HashSet<>();
        for (Furniture f : placed.values()) {
            if (f.getLoc().getChunk().equals(chunk)) {
                inChunk.add(f);
            }
        }

        if (!inChunk.isEmpty()) {
            database.saveChunk(chunk, inChunk);

            // Remove them from active memory (avoid holding unloaded chunk data)
            inChunk.forEach(f -> placed.remove(f.getEntityId()));

            Bukkit.getLogger().info("[Furniture] Saved & unloaded " + inChunk.size()
                    + " furniture(s) from chunk " + chunk.getX() + ", " + chunk.getZ());
        }
    }

    public Set<Furniture> getFurnitureInChunk(Chunk chunk) {
        Set<Furniture> set = new HashSet<>();
        for (Furniture f : placed.values()) {
            if(f.isCarried()) continue;
            if (f.getLoc().getChunk().equals(chunk)) {
                set.add(f);
            }
        }
        return set;
    }

    public void loadAlreadyLoadedChunks() {
        int totalLoaded = 0;

        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                List<Furniture> furnitureList = database.loadChunk(chunk);

                for (Furniture f : furnitureList) {
                    placed.put(f.getEntityId(), f);

                    // Respawn ItemDisplay if needed (safety)
                    if (Bukkit.getEntity(f.getEntityId()) == null) {
                        // TODO: spawn logic (e.g. reload furniture visuals)
                        // furnitureRespawnHandler.spawnFurniture(f);
                    }
                }

                if (!furnitureList.isEmpty()) {
                    totalLoaded += furnitureList.size();
                }
            }
        }

        Bukkit.getLogger().info("[Furniture] Loaded " + totalLoaded + " furniture(s) from already-loaded chunks.");
    }

    public void saveAllLoadedChunks() {
        Map<Chunk, Set<Furniture>> chunkMap = new HashMap<>();

        // Group all furniture by their chunk
        for (Furniture f : placed.values()) {
            Chunk c = f.getLoc().getChunk();
            chunkMap.computeIfAbsent(c, k -> new HashSet<>()).add(f);
        }

        int total = 0;
        for (Map.Entry<Chunk, Set<Furniture>> entry : chunkMap.entrySet()) {
            Chunk chunk = entry.getKey();
            Set<Furniture> list = entry.getValue();

            if (!list.isEmpty()) {
                database.saveChunk(chunk, list);
                total += list.size();
            }
        }

        Bukkit.getLogger().info("[Furniture] Saved " + total + " furniture(s) across " + chunkMap.size() + " loaded chunk(s).");
    }

    public void deleteCarried() {
        for(Furniture f : new ArrayList<>(placed.values())) {
            if(f.isCarried()) f.remove(false);
            placed.remove(f.getEntityId());
        }
    }
}

