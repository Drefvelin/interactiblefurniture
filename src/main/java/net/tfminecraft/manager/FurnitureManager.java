package net.tfminecraft.manager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import net.tfminecraft.InteractibleFurniture;
import net.tfminecraft.database.Database;
import net.tfminecraft.events.FurnitureInteractEvent;
import net.tfminecraft.events.FurniturePunchEvent;
import net.tfminecraft.furniture.Furniture;
import net.tfminecraft.furniture.PlacedSlot;
import net.tfminecraft.furniture.FurnitureType;
import net.tfminecraft.furniture.SlotDefinition;
import net.tfminecraft.manager.handlers.FurniturePlacementHandler;
import net.tfminecraft.manager.handlers.FurnitureBreakHandler;
import net.tfminecraft.manager.handlers.FurnitureRestoreHandler;
import net.tfminecraft.manager.handlers.InteractionHandler;
import net.tfminecraft.manager.handlers.SlotInteractionHandler;
import net.tfminecraft.utils.CoordinateUtils;

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
    private final Set<Database.ChunkKey> dirtyChunks = new HashSet<>();

    public Database getDatabase() {
        return database;
    }

    public void visitSavedFurniture(Consumer<Furniture> visitor) {
        if (database == null) {
            return;
        }
        database.visitSavedFurniture(visitor);
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
                saveDirtyChunks();
            }
        }.runTaskTimer(InteractibleFurniture.getInstance(), 1200L, 1200L);
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

                    for (PlacedSlot slot : f.getActiveSlots().values()) {
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
            BlockFace face = e.getBlockFace();
            for (Furniture f : placed.values()) {
                if (!isValidInteraction(f, clicked, face)) continue;
                Vector clickPoint = CoordinateUtils.calculateClickPoint(p, clicked, face);
                if (SlotInteractionHandler.tryAttachCarried(p, f, carried, clickPoint)) {
                    e.setCancelled(true);
                    return;
                }
            }
            boolean placedCarried = FurniturePlacementHandler.placeCarriedFurniture(p, clicked, face, carried, placed);
            if (placedCarried) {
                e.setCancelled(true);
                return;
            }
        }

        // First check if they clicked a furniture with slots
        for (Furniture f : placed.values()) {
            if (!isValidInteraction(f, clicked, e.getBlockFace())) continue;

            Vector clickPoint = CoordinateUtils.calculateClickPoint(p, clicked, e.getBlockFace());
            if (processFurnitureInteraction(p, f, clickPoint, e.getBlockFace(), clicked)) {
                e.setCancelled(true);
                return;
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
    public void onPlayerInteractAtEntity(PlayerInteractAtEntityEvent e) {
        if (!(e.getRightClicked() instanceof Interaction interaction)) return;

        Furniture f = InteractionHandler.resolveFurniture(interaction, placed);
        if (f == null) return;

        Player p = e.getPlayer();
        if (cooldown.containsKey(p)) {
            long last = cooldown.get(p);
            if (System.currentTimeMillis() < last) return;
        }
        cooldown.put(p, System.currentTimeMillis() + 200);

        Vector clickPoint = interaction.getLocation().toVector().add(e.getClickedPosition());

        Furniture carried = null;
        for (Furniture candidate : placed.values()) {
            if (candidate.isCarried() && candidate.getHolder().equals(p)) {
                carried = candidate;
                break;
            }
        }
        if (carried != null && !f.isCarried()) {
            if (SlotInteractionHandler.tryAttachCarried(p, f, carried, clickPoint)) {
                e.setCancelled(true);
                return;
            }
        }

        if (f.isCarried()) return;

        if (processFurnitureInteraction(p, f, clickPoint, BlockFace.UP, null)) {
            e.setCancelled(true);
        }
    }

    private boolean processFurnitureInteraction(Player p, Furniture f, Vector clickPoint, BlockFace faceHint, Block clicked) {
        Entity furnitureEntity = Bukkit.getEntity(f.getEntityId());
        if (!(furnitureEntity instanceof ItemDisplay display)) return false;

        FurnitureType type = f.getType();
        if (type == null) return false;

        SlotDefinition hitSlot = null;
        Furniture interactFurniture = f;
        if (!type.getSlots().isEmpty() && clickPoint != null) {
            SlotInteractionHandler.SlotHitTarget target =
                    SlotInteractionHandler.findBestSlotHit(clickPoint, f, display, p.getInventory().getItemInMainHand());
            if (target != null) {
                interactFurniture = target.furniture();
                hitSlot = target.slot();
            }
        }

        FurnitureInteractEvent event = new FurnitureInteractEvent(p, interactFurniture, hitSlot, clickPoint);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return true;

        boolean alreadyCarrying = getByCarrier(p) != null;

        if (p.getInventory().getItemInMainHand().getType().equals(Material.AIR)
                && p.isSneaking()
                && type.canCarry()
                && !f.isCarried()
                && !alreadyCarrying) {
            f.carry(p);
            return true;
        }

        if (!p.isSneaking()
                && type.canPickup()
                && p.getInventory().getItemInMainHand().getType().equals(Material.AIR)
                && f.getActiveSlots().isEmpty()
                && f.getActiveFurnitureSlots().isEmpty()
                && !alreadyCarrying) {
            FurnitureBreakHandler.removeFurniture(f.getEntityId(), placed, p, "picked-up");
            return true;
        }

        if (hitSlot != null && hitSlot.isInteractible()) {
            Entity interactEntity = Bukkit.getEntity(interactFurniture.getEntityId());
            if (interactEntity instanceof ItemDisplay interactDisplay
                    && SlotInteractionHandler.handleSlotInteraction(
                            p, interactFurniture, interactDisplay, hitSlot, Action.RIGHT_CLICK_BLOCK)) {
                return true;
            }
        } else if (hitSlot == null && clicked != null && !type.getSlots().isEmpty()) {
            return SlotInteractionHandler.handleSlotInteraction(p, clicked, Action.RIGHT_CLICK_BLOCK, f, display, faceHint);
        }

        return event.isCancelled();
    }


    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        Block broken = e.getBlock();
        Player p = e.getPlayer();

        Set<UUID> toRemove = new HashSet<>();
        Set<Block> connectedBarriers = new HashSet<>();

        for (Map.Entry<UUID, Furniture> en : placed.entrySet()) {
            Furniture f = en.getValue();
            boolean ownsBroken = f.isOriginBlock(broken) || f.getBarrierBlocks().contains(broken);
            if (!ownsBroken) continue;

            toRemove.add(en.getKey());
            connectedBarriers.addAll(f.getBarrierBlocks());
        }

        if (!toRemove.isEmpty()) {
            for (Map.Entry<UUID, Furniture> en : placed.entrySet()) {
                if (toRemove.contains(en.getKey())) continue;
                Furniture f = en.getValue();
                for (Block b : f.getBarrierBlocks()) {
                    if (connectedBarriers.contains(b)) {
                        toRemove.add(en.getKey());
                        break;
                    }
                }
            }
            boolean blocked = false;
            for (UUID id : toRemove) {
                if (!FurnitureBreakHandler.removeFurniture(id, placed, p, "attached-block-broken")) {
                    blocked = true;
                }
            }
            if (blocked) {
                e.setCancelled(true);
            }
            return;
        }

        if (broken.getType() == Material.BARRIER) {
            e.setCancelled(true);
            broken.setType(Material.AIR);
        }
    }

    @EventHandler
    public void onPlayerLeftClick(PlayerInteractEvent e) {
        if (e.getAction() != Action.LEFT_CLICK_BLOCK && e.getAction() != Action.LEFT_CLICK_AIR) {
            return;
        }
        Player p = e.getPlayer();

        if (e.getAction() == Action.LEFT_CLICK_BLOCK) {
            Block clicked = e.getClickedBlock();
            if (clicked != null) {
                for (Map.Entry<UUID, Furniture> en : placed.entrySet()) {
                    Furniture f = en.getValue();

                    for (Block b : f.getBarrierBlocks()) {
                        if (b.equals(clicked)) {
                            punchThenBreak(f, p, "barrier-punched");
                            e.setCancelled(true);
                            return;
                        }
                    }

                    FurnitureType ft = f.getType();
                    if (ft == null || ft.isSolid()) continue;

                    if (f.matchesOrigin(clicked, e.getBlockFace())) {
                        punchThenBreak(f, p, "attached-block-hit");
                        e.setCancelled(true);
                        return;
                    }
                }
            }
        }

        Interaction interaction = raycastInteraction(p);
        if (interaction == null) {
            return;
        }

        Furniture furniture = InteractionHandler.resolveFurniture(interaction, placed);
        if (furniture == null) {
            return;
        }
        FurnitureType type = furniture.getType();
        if (type == null || type.isSolid()) {
            return;
        }

        punchThenBreak(furniture, p, "interaction-punched");
        e.setCancelled(true);
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player player)) {
            return;
        }

        Entity target = e.getEntity();
        if (target instanceof Interaction interaction) {
            e.setCancelled(true);
            Furniture furniture = InteractionHandler.resolveFurniture(interaction, placed);
            if (furniture == null) {
                return;
            }
            FurnitureType type = furniture.getType();
            if (type == null || type.isSolid()) {
                return;
            }
            punchThenBreak(furniture, player, "interaction-attacked");
            return;
        }

        if (!(target instanceof ItemDisplay)) {
            return;
        }
        UUID id = target.getUniqueId();
        Furniture furniture = placed.get(id);
        if (furniture == null) {
            return;
        }
        e.setCancelled(true);
        punchThenBreak(furniture, player, "entity-damage");
    }

    private boolean punchThenBreak(Furniture furniture, Player player, String reason) {
        if (furniture == null || player == null) {
            return false;
        }
        if (isOnCooldown(player)) {
            return true;
        }
        setCooldown(player);

        FurniturePunchEvent punch = new FurniturePunchEvent(player, furniture);
        Bukkit.getPluginManager().callEvent(punch);
        if (punch.isCancelled()) {
            return true;
        }

        breakResolvedFurniture(furniture, player, reason);
        return true;
    }

    private boolean isOnCooldown(Player player) {
        Long last = cooldown.get(player);
        return last != null && System.currentTimeMillis() < last;
    }

    private void setCooldown(Player player) {
        cooldown.put(player, System.currentTimeMillis() + 200);
    }

    private void breakResolvedFurniture(Furniture furniture, Player player, String reason) {
        if (furniture == null) {
            return;
        }
        if (placed.containsKey(furniture.getEntityId())) {
            FurnitureBreakHandler.removeFurniture(furniture.getEntityId(), placed, player, reason);
            return;
        }
        if (furniture.isAttached()) {
            FurnitureBreakHandler.breakNestedFurniture(furniture, placed, player, reason);
        }
    }

    private static Interaction raycastInteraction(Player player) {
        Location eye = player.getEyeLocation();
        RayTraceResult hit = player.getWorld().rayTrace(
                eye,
                eye.getDirection(),
                5.0,
                org.bukkit.FluidCollisionMode.NEVER,
                true,
                0.0,
                entity -> entity instanceof Interaction);
        if (hit == null || !(hit.getHitEntity() instanceof Interaction interaction)) {
            return null;
        }
        return interaction;
    }

	public Map<UUID, Furniture> getPlacedFurniture() {
		return placed;
	}

    /** Remove without dropping the furniture item (plugin cleanup). */
    public boolean removePlugin(Furniture furniture) {
        if (furniture == null) {
            return false;
        }
        return FurnitureBreakHandler.removeFurniture(furniture.getEntityId(), placed, null, "plugin");
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        List<Furniture> loaded = database.loadChunk(chunk);

        boolean changed = false;
        for (Furniture f : loaded) {
            Furniture restored = FurnitureRestoreHandler.restore(f);
            if (restored != null) {
                placed.put(restored.getEntityId(), restored);
            } else {
                changed = true;
            }
        }
        FurnitureRestoreHandler.reconcileChunk(chunk, placed);
        if (changed || dirtyChunks.contains(Database.ChunkKey.fromChunk(chunk))) {
            persistChunk(chunk);
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
            if (f.isCarried()) continue;
            if (f.getLoc().getChunk().equals(chunk)) {
                inChunk.add(f);
            }
        }

        if (!inChunk.isEmpty()) {
            database.saveChunk(chunk, inChunk);
            dirtyChunks.remove(Database.ChunkKey.fromChunk(chunk));

            // Remove them from active memory (avoid holding unloaded chunk data)
            inChunk.forEach(f -> placed.remove(f.getEntityId()));
        }
    }

    public Set<Furniture> getFurnitureInChunk(Chunk chunk) {
        return getFurnitureInChunk(chunk, false);
    }

    public Set<Furniture> getFurnitureForSave(Chunk chunk) {
        return getFurnitureInChunk(chunk, true);
    }

    private Set<Furniture> getFurnitureInChunk(Chunk chunk, boolean includeCarried) {
        Set<Furniture> set = new HashSet<>();
        for (Furniture f : placed.values()) {
            if (!includeCarried && f.isCarried()) continue;
            if (f.getLoc().getChunk().equals(chunk)) {
                set.add(f);
            }
        }
        return set;
    }

    public void markDirty(Furniture furniture) {
        if (furniture == null || furniture.getLoc() == null || furniture.getLoc().getWorld() == null) return;
        dirtyChunks.add(Database.ChunkKey.fromLocation(furniture.getLoc()));
    }

    public void persistFurniture(Furniture furniture) {
        if (furniture == null || furniture.getLoc() == null || furniture.getLoc().getWorld() == null) return;
        Furniture root = resolvePersistRoot(furniture);
        if (root == null || root.getLoc() == null || root.getLoc().getWorld() == null) return;
        persistChunk(root.getLoc().getChunk());
    }

    private Furniture resolvePersistRoot(Furniture furniture) {
        if (furniture == null || !furniture.isAttached()) {
            return furniture;
        }
        UUID parentId = furniture.getParentEntityId();
        if (parentId == null) {
            return furniture;
        }
        Furniture parent = placed.get(parentId);
        if (parent == null) {
            return furniture;
        }
        return resolvePersistRoot(parent);
    }

    public void persistChunk(Chunk chunk) {
        database.saveChunk(chunk, getFurnitureForSave(chunk));
        dirtyChunks.remove(Database.ChunkKey.fromChunk(chunk));
    }

    public void loadAlreadyLoadedChunks() {
        int totalLoaded = 0;

        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                List<Furniture> furnitureList = database.loadChunk(chunk);

                boolean changed = false;
                for (Furniture f : furnitureList) {
                    Furniture restored = FurnitureRestoreHandler.restore(f);
                    if (restored != null) {
                        placed.put(restored.getEntityId(), restored);
                    } else {
                        changed = true;
                    }
                }
                FurnitureRestoreHandler.reconcileChunk(chunk, placed);
                if (changed || dirtyChunks.contains(Database.ChunkKey.fromChunk(chunk))) {
                    persistChunk(chunk);
                }

                if (!furnitureList.isEmpty()) {
                    totalLoaded += furnitureList.size();
                }
            }
        }

        Bukkit.getLogger().info("[Furniture] Loaded " + totalLoaded + " furniture(s) from already-loaded chunks.");
        Bukkit.getScheduler().runTaskLater(InteractibleFurniture.getInstance(), this::reconcileLoadedChunks, 2L);
    }

    public void reconcileLoadedChunks() {
        int removed = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                int before = chunk.getEntities().length;
                FurnitureRestoreHandler.reconcileChunk(chunk, placed);
                removed += Math.max(0, before - chunk.getEntities().length);
            }
        }
        if (removed > 0) {
            Bukkit.getLogger().info("[Furniture] Removed " + removed + " orphan furniture entit(ies) after startup.");
        }
    }

    public void saveDirtyChunks() {
        if (dirtyChunks.isEmpty()) return;
        Set<Database.ChunkKey> snapshot = new HashSet<>(dirtyChunks);
        for (Database.ChunkKey key : snapshot) {
            World world = Bukkit.getWorld(key.world());
            if (world == null) {
                dirtyChunks.remove(key);
                continue;
            }
            if (!world.isChunkLoaded(key.x(), key.z())) {
                dirtyChunks.remove(key);
                continue;
            }
            Chunk chunk = world.getChunkAt(key.x(), key.z());
            Set<Furniture> list = getFurnitureForSave(chunk);
            database.saveChunk(chunk, list);
            dirtyChunks.remove(key);
        }
    }

    public void saveAllLoadedChunks() {
        Map<Chunk, Set<Furniture>> chunkMap = new HashMap<>();

        for (Furniture f : placed.values()) {
            if (f.isCarried()) continue;
            Chunk c = f.getLoc().getChunk();
            chunkMap.computeIfAbsent(c, k -> new HashSet<>()).add(f);
        }

        int total = 0;
        for (Map.Entry<Chunk, Set<Furniture>> entry : chunkMap.entrySet()) {
            Chunk chunk = entry.getKey();
            Set<Furniture> list = entry.getValue();

            if (!list.isEmpty()) {
                database.saveChunk(chunk, list);
                dirtyChunks.remove(Database.ChunkKey.fromChunk(chunk));
                total += list.size();
            }
        }

        Bukkit.getLogger().info("[Furniture] Saved " + total + " furniture(s) across " + chunkMap.size() + " loaded chunk(s).");
    }

    public void deleteCarried() {
        for (Furniture f : new ArrayList<>(placed.values())) {
            if (f.isCarried()) f.remove(false);
        }
    }
}

