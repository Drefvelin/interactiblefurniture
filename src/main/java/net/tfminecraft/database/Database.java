package net.tfminecraft.database;

import com.google.gson.*;
import net.tfminecraft.InteractibleFurniture;
import net.tfminecraft.furniture.Furniture;
import net.tfminecraft.furniture.FurnitureSlot;
import net.tfminecraft.loaders.FurnitureLoader;
import net.tfminecraft.furniture.data.FurnitureDataContainer;
import net.tfminecraft.furniture.data.ModelData;
import net.tfminecraft.enums.Display;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockFace;

import java.io.*;
import java.util.*;

/**
 * Handles saving and loading furniture data to disk.
 * Each chunk's furniture is stored in its own JSON file:
 *   data/chunks/<world>/<chunkX>_<chunkZ>.json
 */
public class Database {

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    private final File chunkDataFolder;

    public Database() {
        File baseFolder = new File(InteractibleFurniture.getInstance().getDataFolder(), "data/chunks");
        if (!baseFolder.exists()) baseFolder.mkdirs();
        this.chunkDataFolder = baseFolder;
    }

    // ------------------------------------------------------------------------
    //  CHUNK STRUCTURE
    // ------------------------------------------------------------------------

    public record ChunkKey(String world, int x, int z) {
        static ChunkKey fromLocation(Location loc) {
            return new ChunkKey(
                    loc.getWorld().getName(),
                    loc.getBlockX() >> 4,
                    loc.getBlockZ() >> 4
            );
        }

        static ChunkKey fromChunk(Chunk c) {
            return new ChunkKey(c.getWorld().getName(), c.getX(), c.getZ());
        }

        File toFile(File root) {
            File worldDir = new File(root, world);
            if (!worldDir.exists()) worldDir.mkdirs();
            return new File(worldDir, x + "_" + z + ".json");
        }
    }

    // ------------------------------------------------------------------------
    //  SAVE / LOAD CHUNK
    // ------------------------------------------------------------------------

    public void saveChunk(String world, int chunkX, int chunkZ, Collection<Furniture> furniture) {
        File file = new ChunkKey(world, chunkX, chunkZ).toFile(chunkDataFolder);

        List<Map<String, Object>> serialized = furniture.stream()
                .map(this::serializeFurniture)
                .toList();

        try (Writer writer = new FileWriter(file)) {
            GSON.toJson(Map.of("furniture", serialized), writer);
        } catch (IOException e) {
            InteractibleFurniture.getInstance().getLogger()
                    .warning("Failed to save furniture for chunk " + world + " " + chunkX + "," + chunkZ);
            e.printStackTrace();
        }
    }

    public List<Furniture> loadChunk(String world, int chunkX, int chunkZ) {
        File file = new ChunkKey(world, chunkX, chunkZ).toFile(chunkDataFolder);
        if (!file.exists()) return List.of();

        try (Reader reader = new FileReader(file)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            JsonArray arr = root.getAsJsonArray("furniture");

            List<Furniture> list = new ArrayList<>();
            for (JsonElement el : arr) {
                JsonObject obj = el.getAsJsonObject();
                Furniture f = deserializeFurniture(obj);
                if (f != null) list.add(f);
            }
            return list;

        } catch (IOException e) {
            InteractibleFurniture.getInstance().getLogger()
                    .warning("Failed to load furniture for chunk " + world + " " + chunkX + "," + chunkZ);
            e.printStackTrace();
            return List.of();
        }
    }

    // ------------------------------------------------------------------------
    //  FURNITURE SERIALIZATION
    // ------------------------------------------------------------------------

    private Map<String, Object> serializeFurniture(Furniture f) {
        Map<String, Object> obj = new HashMap<>();
        obj.put("id", f.getId());
        obj.put("type", f.getType().getId());
        obj.put("entityId", f.getEntityId().toString());
        obj.put("location", serializeLocation(f.getLoc()));

        f.getOriginBlockLocation().ifPresent(loc -> obj.put("originBlock", serializeLocation(loc)));
        f.getOriginBlockFace().ifPresent(face -> obj.put("originBlockFace", face.name()));

        // Barrier blocks
        if (!f.getBarrierBlocks().isEmpty()) {
            obj.put("barrierBlocks", f.getBarrierBlocks().stream()
                    .map(b -> serializeLocation(b.getLocation()))
                    .toList());
        }

        // Active slots
        if (!f.getActiveSlots().isEmpty()) {
            Map<String, Object> slots = new HashMap<>();
            for (var entry : f.getActiveSlots().entrySet()) {
                FurnitureSlot slot = entry.getValue();
                if (slot.getDisplayStandId() != null) {
                    slots.put(entry.getKey(), Map.of(
                            "displayStandId", slot.getDisplayStandId().toString()
                    ));
                }
            }
            obj.put("activeSlots", slots);
        }

        // --------------------------------------------------------------------
        // NEW: SAVE FurnitureDataContainer (variables + modelOverride)
        // --------------------------------------------------------------------
        FurnitureDataContainer data = f.getType().getData();
        Map<String, Object> dataMap = new HashMap<>();

        // Save variables
        if (!data.getVariables().isEmpty()) {
            dataMap.put("variables", data.getVariables());
        }

        // Save model override
        if (data.getModelOverride() != null) {
            ModelData m = data.getModelOverride();
            dataMap.put("modelOverride", Map.of(
                    "display", m.getDisplay().name(),
                    "model", m.getModel()
            ));
        }

        if (!dataMap.isEmpty()) {
            obj.put("data", dataMap);
        }

        return obj;
    }

    private Furniture deserializeFurniture(JsonObject obj) {
        String typeId = obj.get("type").getAsString();
        if (FurnitureLoader.getByString(typeId) == null) return null;

        Location loc = deserializeLocation(obj.getAsJsonObject("location"));
        UUID entityId = UUID.fromString(obj.get("entityId").getAsString());

        Location originLoc = null;
        BlockFace originFace = null;

        if (obj.has("originBlock")) {
            originLoc = deserializeLocation(obj.getAsJsonObject("originBlock"));
        }
        if (obj.has("originBlockFace")) {
            try {
                originFace = BlockFace.valueOf(obj.get("originBlockFace").getAsString());
            } catch (Exception ignored) {}
        }

        Furniture furniture = new Furniture(typeId, loc, entityId, originLoc, originFace);

        // Restore active slots
        if (obj.has("activeSlots")) {
            JsonObject slots = obj.getAsJsonObject("activeSlots");
            for (String key : slots.keySet()) {
                JsonObject sObj = slots.getAsJsonObject(key);
                FurnitureSlot slot = furniture.getType().getSlot(key);
                if (slot == null) continue;

                if (sObj.has("displayStandId")) {
                    UUID dispId = UUID.fromString(sObj.get("displayStandId").getAsString());
                    slot.setDisplayStandId(dispId);
                }
                furniture.addActiveSlot(slot);
            }
        }

        // --------------------------------------------------------------------
        // NEW: LOAD FurnitureDataContainer (variables + modelOverride)
        // --------------------------------------------------------------------
        if (obj.has("data")) {
            JsonObject dataObj = obj.getAsJsonObject("data");
            FurnitureDataContainer data = furniture.getType().getData();

            // Load variables
            if (dataObj.has("variables")) {
                Map<String, Object> vars = new HashMap<>();
                JsonObject varObj = dataObj.getAsJsonObject("variables");
                for (String key : varObj.keySet()) {
                    JsonElement v = varObj.get(key);
                    if (v.isJsonPrimitive()) {
                        JsonPrimitive p = v.getAsJsonPrimitive();
                        if (p.isNumber()) vars.put(key, p.getAsNumber());
                        else if (p.isBoolean()) vars.put(key, p.getAsBoolean());
                        else vars.put(key, p.getAsString());
                    }
                }
                data.setVariables(vars);
            }

            // Load model override
            if (dataObj.has("modelOverride")) {
                JsonObject o = dataObj.getAsJsonObject("modelOverride");
                try {
                    Display d = Display.valueOf(o.get("display").getAsString());
                    String model = o.get("model").getAsString();
                    data.setModelOverride(new ModelData(d, model));
                } catch (Exception ignored) {}
            }
        }

        return furniture;
    }

    // ------------------------------------------------------------------------
    //  LOCATION
    // ------------------------------------------------------------------------

    private Map<String, Object> serializeLocation(Location loc) {
        Map<String, Object> m = new HashMap<>();
        m.put("world", loc.getWorld().getName());
        m.put("x", loc.getX());
        m.put("y", loc.getY());
        m.put("z", loc.getZ());
        return m;
    }

    private Location deserializeLocation(JsonObject obj) {
        World w = Bukkit.getWorld(obj.get("world").getAsString());
        if (w == null) return null;
        return new Location(
                w,
                obj.get("x").getAsDouble(),
                obj.get("y").getAsDouble(),
                obj.get("z").getAsDouble()
        );
    }

    // ------------------------------------------------------------------------
    //  EVENTS
    // ------------------------------------------------------------------------

    public void saveChunk(Chunk chunk, Collection<Furniture> furniture) {
        saveChunk(chunk.getWorld().getName(), chunk.getX(), chunk.getZ(), furniture);
    }

    public List<Furniture> loadChunk(Chunk chunk) {
        return loadChunk(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
    }
}
