package net.tfminecraft.manager.handlers;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.Vector3f;

import me.Plugins.TLibs.TLibs;
import net.tfminecraft.InteractibleFurniture;
import net.tfminecraft.enums.Display;
import net.tfminecraft.enums.SoundEffect;
import net.tfminecraft.events.FurniturePlaceEvent;
import net.tfminecraft.furniture.Furniture;
import net.tfminecraft.furniture.FurnitureType;
import net.tfminecraft.furniture.data.DisplayData;
import net.tfminecraft.furniture.data.ModelData;
import net.tfminecraft.utils.Direction;
import net.tfminecraft.utils.Keys;

import java.util.*;

public class FurniturePlacementHandler {

    public static boolean handlePlacement(Player player, Block clicked, BlockFace face,
                                          ItemStack held, Map<UUID, Furniture> placed) {

        for (FurnitureType type : net.tfminecraft.loaders.FurnitureLoader.getMap().values()) {
            if (!isValidFurnitureType(type, held)) continue;
            if (!isValidPlacementSurface(type, face)) continue;
            if (!type.isAllowedBlock(clicked.getType())) continue;
            if (isOriginOccupied(clicked, placed)) return true;

            Location target = calculateTargetLocation(clicked, face, type);
            float yaw = calculateYaw(player, face, type);

            // Flip layer vertically based on face
            List<boolean[][]> layers = type.getLayers();

            if (!hasEnoughSpace(layers, clicked, player, type)) return true;

            Entity display = spawnDisplayEntity(type, target, yaw, face);
            if(display == null) return true;
            Furniture furniture = new Furniture(type.getId(), display.getLocation(),
                    display.getUniqueId(), clicked.getLocation(), face);
            furniture.setYaw(yaw);
            FurniturePlaceEvent event = new FurniturePlaceEvent(furniture, player);
            Bukkit.getPluginManager().callEvent(event);
            if(event.isCancelled()) {
                display.remove();
                return false;
            }

            placeBarrierBlocks(layers, furniture, clicked, face);

            placed.put(display.getUniqueId(), furniture);
            InteractionHandler.spawnInteraction(furniture);
            InteractibleFurniture.getInstance().getFurnitureManager().persistFurniture(furniture);
            player.swingMainHand();
            player.getInventory().getItemInMainHand().setAmount(
                    player.getInventory().getItemInMainHand().getAmount() - 1);
            if(type.hasSoundEffect(SoundEffect.PLACE)) {
                String sound = type.getSoundEffectPath(SoundEffect.PLACE);
                display.getLocation().getWorld().playSound(display.getLocation(), sound, 1.0f, 1.0f);
            }
            return true;
        }
        return false;
    }

    public static boolean placeCarriedFurniture(
            Player player,
            Block clicked,
            BlockFace face,
            Furniture carried,
            Map<UUID, Furniture> placed
    ) {

        if (carried == null) return false;

        FurnitureType type = carried.getType();
        if (type == null) return false;
        if (!isValidPlacementSurface(type, face)) return false;
        if (!type.isAllowedBlock(clicked.getType())) return false;
        if (isOriginOccupied(clicked, placed)) return false;

        // Calculate target location and yaw exactly like normal placement
        Location target = calculateTargetLocation(clicked, face, type);
        float yaw = calculateYaw(player, face, type);

        // Validate space
        if (!hasEnoughSpace(type.getLayers(), clicked, player, type)) {
            return false;
        }

        // Spawn the new display entity
        ItemDisplay display = spawnDisplayEntity(type, target, yaw, face);
        if (display == null) return false;

        // apply
        carried.setFromDisplay(display, clicked, face);
        carried.setYaw(yaw);

        InteractibleFurniture.getInstance().getFurnitureManager().persistFurniture(carried);

        return true;
    }


    private static boolean isValidFurnitureType(FurnitureType type, ItemStack held) {
        if (type.getItemPath() == null || type.getItemPath().isEmpty()) return false;
        return TLibs.getItemAPI().getChecker().checkItemWithPath(held, type.getItemPath());
    }

    private static Location calculateTargetLocation(Block clicked, BlockFace face, FurnitureType type) {
        if (type.canPlaceInside()) {
            return clicked.getLocation().add(0.5, 0.0, 0.5);
        }

        Location target = clicked.getLocation().add(0.5, 1.0, 0.5);

        if (type.canPlaceOnRoof() && face == BlockFace.DOWN) {
            target = clicked.getLocation().add(0.5, 0.0, 0.5);
        }

        if (type.canPlaceOnWall() && isWallFace(face)) {
            switch (face) {
                case NORTH -> target.add(0, -0.5, -0.5);
                case SOUTH -> target.add(0, -0.5, 0.5);
                case EAST  -> target.add(0.5, -0.5, 0);
                case WEST  -> target.add(-0.5, -0.5, 0);
                default -> {}
            }
        }

        return target;
    }

    private static Direction getPlayerFacing(Player player) {
        float yaw = player.getLocation().getYaw();
        
        // Normalize yaw to 0-360 range
        while (yaw < 0) yaw += 360;
        while (yaw >= 360) yaw -= 360;
        
        // Snap to 45 degrees
        yaw = Math.round(yaw / 45f) * 45f;
        
        Direction facing = switch ((int) yaw) {
            case 0 -> Direction.SOUTH;
            case 45 -> Direction.SOUTH_WEST;
            case 90 -> Direction.WEST;
            case 135 -> Direction.NORTH_WEST;
            case 180 -> Direction.NORTH;
            case 225 -> Direction.NORTH_EAST;
            case 270 -> Direction.EAST;
            case 315 -> Direction.SOUTH_EAST;
            default -> Direction.SOUTH; // Fallback
        };
        return facing;
    }

    private static float calculateYaw(Player player, BlockFace face, FurnitureType type) {
        // For wall furniture, always face outward from wall
        if (type.canPlaceOnWall() && isWallFace(face)) {
            switch (face) {
                case NORTH -> { 
                    return 0;   // When facing north, model faces south
                }
                case SOUTH -> {
                    return 180; // When facing south, model faces north
                }
                case EAST -> {
                    return 270; // When facing east, model faces west
                }
                case WEST -> {
                    return 90;  // When facing west, model faces east
                }
                default -> {}
            }
        }

        // For ground placement, get player facing direction
        Direction facing = getPlayerFacing(player);
        
        // Convert direction to yaw - you can adjust these values as needed
        float yaw = switch (facing) {
            case NORTH -> 0;      // Adjust these values
            case NORTH_EAST -> 315;
            case EAST -> 270;
            case SOUTH_EAST -> 225;
            case SOUTH -> 180;
            case SOUTH_WEST -> 135;
            case WEST -> 90;
            case NORTH_WEST -> 45;
        };
        
        return yaw;
    }

    private static boolean isWallFace(BlockFace face) {
        return face == BlockFace.NORTH || face == BlockFace.SOUTH
                || face == BlockFace.EAST || face == BlockFace.WEST;
    }

    private static boolean isValidPlacementSurface(FurnitureType type, BlockFace face) {
        if (face == null) return false;
        return switch (face) {
            case UP -> type.canPlaceOnFloor();
            case DOWN -> type.canPlaceOnRoof();
            case NORTH, SOUTH, EAST, WEST -> type.canPlaceOnWall();
            default -> false;
        };
    }

    private static boolean isOriginOccupied(Block clicked, Map<UUID, Furniture> placed) {
        for (Furniture f : placed.values()) {
            if (f.isCarried()) continue;
            if (f.isOriginBlock(clicked)) return true;
        }
        return false;
    }

    // ---- Space checking ----
    private static boolean hasEnoughSpace(List<boolean[][]> layers, Block clicked,
                                          Player player, FurnitureType type) {
        if (layers == null || layers.isEmpty()) return true;

        Block centerBlock = clicked.getRelative(0, 1, 0);
        for (int ly = 0; ly < layers.size(); ly++) {
            boolean[][] mat = layers.get(ly);
            int size = mat.length;
            int centerIdx = size / 2;

            for (int r = 0; r < size; r++) {
                for (int c = 0; c < size; c++) {
                    if (!mat[r][c]) continue;

                    Block blockTarget = centerBlock.getRelative(c - centerIdx, ly, r - centerIdx);
                    Material mt = blockTarget.getType();
                    if (!(mt == Material.AIR || mt == Material.BARRIER)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    // ---- Barrier placement ----
    public static void placeBarrierBlocks(List<boolean[][]> layers, Furniture furniture,
                                           Block clicked, BlockFace face) {
        if (layers == null || layers.isEmpty()) return;

        Block center = clicked.getRelative(face);
        for (int ly = 0; ly < layers.size(); ly++) {
            boolean[][] mat = layers.get(ly);
            int size = mat.length;
            int centerIdx = size / 2;

            for (int r = 0; r < size; r++) {
                for (int c = 0; c < size; c++) {
                    if (!mat[r][c]) continue;

                    Block blockTarget = center.getRelative(c - centerIdx, ly, r - centerIdx);
                    if (blockTarget.getType() == Material.AIR) {
                        blockTarget.setType(Material.BARRIER);
                        furniture.addBarrierBlock(blockTarget);
                    } else if (blockTarget.getType() == Material.BARRIER) {
                        furniture.addBarrierBlock(blockTarget);
                    }
                }
            }
        }
    }

    // ---- Display entity ----
    public static ItemDisplay spawnDisplayEntity(FurnitureType type, Location target, float yaw, BlockFace face) {
        Location spawnLoc = applyFaceOffset(target.clone(), face);
        if (InteractibleFurniture.getInstance().getFurnitureManager().getByLocation(spawnLoc) != null) return null;
        return spawnDisplayAt(type, spawnLoc, yaw, face);
    }

    public static Location applyFaceOffset(Location spawnLoc, BlockFace face) {
        switch (face) {
            case UP:
                spawnLoc.add(0, 0.5, 0);
                break;
            case DOWN:
                spawnLoc.add(0, -0.5, 0);
                break;
            case NORTH:
                spawnLoc.add(0, 0, -0.5);
                break;
            case SOUTH:
                spawnLoc.add(0, 0, 0.5);
                break;
            case EAST:
                spawnLoc.add(0.5, 0, 0);
                break;
            case WEST:
                spawnLoc.add(-0.5, 0, 0);
                break;
            default:
                break;
        }
        return spawnLoc;
    }

    public static void tagDisplay(ItemDisplay display, UUID furnitureId) {
        display.getPersistentDataContainer().set(
                Keys.furnitureDisplay(),
                PersistentDataType.STRING,
                furnitureId.toString());
    }

    public static ItemDisplay spawnDisplayAt(FurnitureType type, Location spawnLoc, float yaw, BlockFace face) {
        return spawnDisplayAt(type, spawnLoc, yaw, face, type.getData().getCurrentModelData());
    }

    public static ItemDisplay spawnDisplayAt(Furniture furniture, Location spawnLoc, float yaw, BlockFace face) {
        return spawnDisplayAt(furniture.getType(), spawnLoc, yaw, face, furniture.getCurrentModelData());
    }

    public static ItemDisplay spawnDisplayAt(FurnitureType type, Location spawnLoc, float yaw, BlockFace face,
            ModelData model) {
        if (type == null || model == null || !model.getDisplay().equals(Display.ITEM_DISPLAY)) {
            return null;
        }
        return spawnLoc.getWorld().spawn(spawnLoc, ItemDisplay.class, disp -> {
            disp.setItemStack(TLibs.getItemAPI().getCreator().getItemFromPath(model.getModel()));
            disp.setBillboard(org.bukkit.entity.Display.Billboard.FIXED);
            org.joml.Quaternionf rotation = new org.joml.Quaternionf();
            DisplayData data = type.getDisplayData();
            rotation.rotateY((float) Math.toRadians(data.getyRot() + yaw));
            if (data.getxRot() != 0) rotation.rotateX((float) Math.toRadians(data.getxRot()));
            if (data.getzRot() != 0) rotation.rotateZ((float) Math.toRadians(data.getzRot()));

            switch (face) {
                case UP:
                    break;
                case DOWN:
                    rotation.rotateX((float) Math.toRadians(-180));
                    break;
                case NORTH, SOUTH, EAST, WEST:
                    rotation.rotateX((float) Math.toRadians(-90));
                    break;
                default:
                    break;
            }

            disp.setTransformation(new Transformation(
                    new Vector3f((float) data.getxPos(), (float) data.getyPos(), (float) data.getzPos()),
                    rotation,
                    new Vector3f((float) data.getxScale(), (float) data.getyScale(), (float) data.getzScale()),
                    new org.joml.Quaternionf()
            ));
            disp.setBrightness(new org.bukkit.entity.Display.Brightness(
                    spawnLoc.getBlock().getLightFromBlocks(), spawnLoc.getBlock().getLightFromSky()));
            disp.setShadowRadius(0f);
            disp.setShadowStrength(0f);
            disp.setViewRange(50f);
            disp.setPersistent(true);
            tagDisplay(disp, disp.getUniqueId());
        });
    }
}
