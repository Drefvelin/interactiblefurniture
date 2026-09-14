package net.tfminecraft.manager.handlers;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import me.Plugins.TLibs.TLibs;
import net.tfminecraft.InteractibleFurniture;
import net.tfminecraft.enums.SoundEffect;
import net.tfminecraft.events.FurnitureBreakEvent;
import net.tfminecraft.furniture.Furniture;
import net.tfminecraft.furniture.FurnitureType;
import net.tfminecraft.furniture.PlacedFurnitureSlot;
import net.tfminecraft.furniture.PlacedSlot;
import net.tfminecraft.furniture.SlotDefinition;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class FurnitureBreakHandler {

    public static boolean removeFurniture(UUID furnitureId, Map<UUID, Furniture> placed,
            Player breaker, String reason) {
        return removeFurniture(furnitureId, placed, breaker, reason, true);
    }

    public static boolean removeFurniture(UUID furnitureId, Map<UUID, Furniture> placed,
            Player breaker, String reason, boolean dropslots) {
        if (!placed.containsKey(furnitureId)) {
            return false;
        }

        Set<UUID> connected = collectConnectedFurniture(furnitureId, placed);
        List<UUID> ordered = sortBreakOrder(connected, placed);

        boolean success = false;
        boolean blocked = false;
        for (UUID id : ordered) {
            if (!placed.containsKey(id)) {
                continue;
            }
            String pieceReason = id.equals(furnitureId) ? reason : "connected-break";
            if (removeFurnitureInternal(id, placed, breaker, pieceReason, dropslots)) {
                success = true;
            } else {
                blocked = true;
            }
        }
        return success && !blocked;
    }

    private static boolean removeFurnitureInternal(UUID furnitureId, Map<UUID, Furniture> placed,
            Player breaker, String reason, boolean dropslots) {
        if (!placed.containsKey(furnitureId)) return false;
        FurnitureBreakEvent event = new FurnitureBreakEvent(placed.get(furnitureId), breaker);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return false;
        Furniture furniture = placed.remove(furnitureId);
        if (furniture == null) return false;
        clearUnsharedBarriers(furniture, placed);
        Chunk chunk = furniture.getLoc().getChunk();
        InteractibleFurniture.getInstance().getFurnitureManager().persistChunk(chunk);
        if (furniture.getType() != null && furniture.getType().hasSoundEffect(SoundEffect.BREAK)) {
            String sound = furniture.getType().getSoundEffectPath(SoundEffect.BREAK);
            furniture.getLoc().getWorld().playSound(furniture.getLoc(), sound, 1.0f, 1.0f);
        }
        destroyFurniture(furniture, breaker, reason, dropslots, true, new HashSet<>());
        return true;
    }

    private static Set<UUID> collectConnectedFurniture(UUID seedId, Map<UUID, Furniture> placed) {
        Set<UUID> connected = new HashSet<>();
        if (!placed.containsKey(seedId)) {
            return connected;
        }

        connected.add(seedId);
        boolean changed = true;
        while (changed) {
            changed = false;
            int sizeBefore = connected.size();
            Set<Block> checkedBarriers = new HashSet<>();

            for (UUID id : new ArrayList<>(connected)) {
                Furniture furniture = placed.get(id);
                if (furniture == null) {
                    continue;
                }

                for (Block barrier : new ArrayList<>(furniture.getBarrierBlocks())) {
                    findConnectedRecursive(barrier, placed, connected, checkedBarriers);
                }

                for (Map.Entry<UUID, Furniture> entry : placed.entrySet()) {
                    UUID otherId = entry.getKey();
                    if (connected.contains(otherId)) {
                        continue;
                    }
                    Furniture other = entry.getValue();
                    if (shouldExcludeFromConnectedBreak(other)) {
                        continue;
                    }
                    Location origin = other.getOriginBlockLocation().orElse(null);
                    if (origin == null) {
                        continue;
                    }
                    if (furniture.getBarrierBlocks().contains(origin.getBlock())) {
                        connected.add(otherId);
                    }
                }
            }

            connected.removeIf(id -> !id.equals(seedId)
                    && placed.containsKey(id)
                    && shouldExcludeFromConnectedBreak(placed.get(id)));

            if (connected.size() > sizeBefore) {
                changed = true;
            }
        }

        return connected;
    }

    private static boolean shouldExcludeFromConnectedBreak(Furniture furniture) {
        return furniture.isCarried() || furniture.isAttached();
    }

    private static List<UUID> sortBreakOrder(Set<UUID> ids, Map<UUID, Furniture> placed) {
        List<UUID> ordered = new ArrayList<>(ids);
        ordered.sort((a, b) -> {
            Furniture fa = placed.get(a);
            Furniture fb = placed.get(b);
            double ya = fa != null ? fa.getLoc().getY() : 0;
            double yb = fb != null ? fb.getLoc().getY() : 0;
            int cmp = Double.compare(yb, ya);
            if (cmp != 0) {
                return cmp;
            }
            return a.compareTo(b);
        });
        return ordered;
    }

    public static boolean breakNestedFurniture(Furniture nested, Map<UUID, Furniture> placed,
            Player breaker, String reason) {
        if (nested == null || !nested.isAttached()) {
            return false;
        }
        UUID parentId = nested.getParentEntityId();
        String slotId = nested.getParentSlotId();
        if (parentId == null || slotId == null) {
            return false;
        }

        Furniture parent = InteractionHandler.findFurniture(parentId, placed);
        if (parent == null) {
            return false;
        }

        FurnitureBreakEvent event = new FurnitureBreakEvent(nested, breaker);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return false;
        }

        nested.clearAttachment();
        parent.removeActiveFurnitureSlot(slotId);

        if (nested.getType() != null && nested.getType().hasSoundEffect(SoundEffect.BREAK)) {
            String sound = nested.getType().getSoundEffectPath(SoundEffect.BREAK);
            nested.getLoc().getWorld().playSound(nested.getLoc(), sound, 1.0f, 1.0f);
        }

        destroyFurniture(nested, breaker, reason, true, true, new HashSet<>());
        InteractibleFurniture.getInstance().getFurnitureManager().persistFurniture(parent);
        return true;
    }

    private static void destroyFurniture(Furniture furniture, Player breaker, String reason,
            boolean dropslots, boolean dropRootItem, Set<UUID> visited) {
        if (furniture == null || furniture.getEntityId() == null) {
            return;
        }
        if (visited.contains(furniture.getEntityId())) {
            return;
        }
        visited.add(furniture.getEntityId());

        dropNestedFurnitureSlots(furniture, breaker, reason, dropslots, visited);
        dropSlotItems(furniture, dropslots);
        if (dropRootItem) {
            dropFurnitureItem(breaker, furniture, reason);
        }
        furniture.removeInteractionEntity();
        removeEntity(furniture.getEntityId());
    }

    private static void dropNestedFurnitureSlots(Furniture furniture, Player breaker, String reason,
            boolean dropslots, Set<UUID> visited) {
        for (PlacedFurnitureSlot placed : new ArrayList<>(furniture.getActiveFurnitureSlots().values())) {
            Furniture nested = placed.getNested();
            if (nested == null) {
                continue;
            }
            SlotDefinition def = placed.getDefinition().orElse(null);
            boolean dropNestedItem = dropslots && def != null && def.dropsOnBreak();
            nested.clearAttachment();
            destroyFurniture(nested, breaker, reason, dropslots, dropNestedItem, visited);
        }
        furniture.clearActiveFurnitureSlots();
    }

    private static void clearUnsharedBarriers(Furniture furniture, Map<UUID, Furniture> placed) {
        for (Block block : new ArrayList<>(furniture.getBarrierBlocks())) {
            if (block.getType() != Material.BARRIER) continue;
            boolean shared = false;
            for (Furniture other : placed.values()) {
                if (other.getBarrierBlocks().contains(block)) {
                    shared = true;
                    break;
                }
            }
            if (!shared) {
                block.setType(Material.AIR);
            }
        }
        furniture.clearBarrierBlocks();
    }

    public static Set<UUID> findConnectedFurniture(Block startBlock, Map<UUID, Furniture> placed) {
        Set<UUID> connected = new HashSet<>();
        Set<Block> checkedBarriers = new HashSet<>();
        findConnectedRecursive(startBlock, placed, connected, checkedBarriers);
        return connected;
    }

    public static void findConnectedRecursive(Block block, Map<UUID, Furniture> placed,
            Set<UUID> connected, Set<Block> checkedBarriers) {
        if (checkedBarriers.contains(block)) return;
        checkedBarriers.add(block);

        for (Map.Entry<UUID, Furniture> entry : placed.entrySet()) {
            if (connected.contains(entry.getKey())) continue;

            Furniture f = entry.getValue();
            if (f.getBarrierBlocks().contains(block) || f.isOriginBlock(block)) {
                connected.add(entry.getKey());
                for (Block b : f.getBarrierBlocks()) {
                    findConnectedRecursive(b, placed, connected, checkedBarriers);
                }
            }
        }
    }

    private static void dropFurnitureItem(Player p, Furniture furniture, String reason) {
        FurnitureType type = furniture.getType();
        if (type == null) return;

        ItemStack furnitureItem = TLibs.getItemAPI().getCreator().getItemFromPath(type.getItemPath());
        if (furnitureItem == null) return;
        if (reason == null || (!reason.equals("picked-up") && !reason.equals("plugin"))) {
            Location dropLoc = furniture.getLoc();
            furniture.getLoc().getWorld().dropItemNaturally(dropLoc, furnitureItem);
        } else if (p != null && reason.equals("picked-up")) {
            p.swingMainHand();
            p.getInventory().setItemInMainHand(furnitureItem);
        }
    }

    private static void dropSlotItems(Furniture furniture, boolean dropslots) {
        Location dropLoc = furniture.getLoc();
        if (dropLoc.getWorld() == null) return;

        for (PlacedSlot slot : new ArrayList<>(furniture.getActiveSlots().values())) {
            if (dropslots) {
                SlotDefinition def = slot.getDefinition();
                if (def != null && !def.dropsOnBreak()) {
                    slot.removeDisplayStand(dropLoc.getWorld());
                    continue;
                }
                ItemStack item = slot.getCurrentItem();
                if (item != null) {
                    dropLoc.getWorld().dropItemNaturally(dropLoc, item);
                }
            }
            slot.removeDisplayStand(dropLoc.getWorld());
        }
        furniture.clearActiveSlots();
    }

    private static void removeEntity(UUID furnitureId) {
        Entity entity = Bukkit.getEntity(furnitureId);
        if (entity != null) {
            entity.remove();
        }
    }
}
