package net.tfminecraft.utils;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public class CoordinateUtils {
    public static Vector calculateClickPoint(Player player, Block clicked, BlockFace face) {
        // Get face normal and center
        Vector faceNormal = new Vector(face.getModX(), face.getModY(), face.getModZ());
        Vector faceCenter = clicked.getLocation().add(0.5, 0.5, 0.5).toVector()
                .add(new Vector(
                    face.getModX() * 0.5,
                    face.getModY() * 0.5,
                    face.getModZ() * 0.5
                ));

        // Player's look ray
        Vector look = player.getLocation().getDirection();
        Vector origin = player.getEyeLocation().toVector();

        // Find intersection with face plane
        double denom = look.dot(faceNormal);
        if (Math.abs(denom) < 1e-6) {
            return faceCenter; // Ray is parallel to face, use center
        }

        double t = (faceCenter.clone().subtract(origin)).dot(faceNormal) / denom;
        return origin.clone().add(look.clone().multiply(t));
    }

    public static double calculateDistance(Vector slotPoint, Vector clickPoint, BlockFace face) {
        if (face == BlockFace.UP || face == BlockFace.DOWN) {
            // For up/down faces, compare X and Z coordinates (ignore Y)
            return Math.sqrt(
                Math.pow(slotPoint.getX() - clickPoint.getX(), 2) +
                Math.pow(slotPoint.getZ() - clickPoint.getZ(), 2)
            );
        }
        
        // For other faces, use 3D distance
        return slotPoint.distance(clickPoint);
    }
}