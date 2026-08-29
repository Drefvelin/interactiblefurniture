package net.tfminecraft.database;

import com.google.gson.*;
import net.tfminecraft.InteractibleFurniture;
import net.tfminecraft.furniture.Furniture;
import net.tfminecraft.furniture.PlacedSlot;
import net.tfminecraft.loaders.FurnitureLoader;
import net.tfminecraft.furniture.data.ModelData;
import net.tfminecraft.enums.Display;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
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
        public static ChunkKey fromLocation(Location loc) {
            return new ChunkKey(
                    loc.getWorld().getName(),
                    loc.getBlockX() >> 4,
                    loc.getBlockZ() >> 4
            );
        }

        public static ChunkKey fromChunk(Chunk c) {
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
        File tmp = new File(file.getParentFile(), file.getName() + ".tmp");
        File bak = new File(file.getParentFile(), file.getName() + ".bak");

        List<Map<String, Object>> serialized = furniture.stream()
                .map(this::serializeFurniture)
                .toList();

        try (Writer writer = new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8)) {
            GSON.toJson(Map.of("furniture", serialized), writer);
        } catch (IOException e) {
            InteractibleFurniture.getInstance().getLogger()
                    .warning("Failed to save furniture for chunk " + world + " " + chunkX + "," + chunkZ);
            e.printStackTrace();
            return;
        }

        try {
            if (file.exists()) {
                Files.copy(file.toPath(), bak.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            try {
                Files.move(tmp.toPath(), file.toPath(),
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicFailed) {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            InteractibleFurniture.getInstance().getLogger()
                    .warning("Failed to replace furniture file for chunk " + world + " " + chunkX + "," + chunkZ);
            e.printStackTrace();
        }
    }

    public List<Furniture> loadChunk(String world, int chunkX, int chunkZ) {
        File file = new ChunkKey(world, chunkX, chunkZ).toFile(chunkDataFolder);
        File bak = new File(file.getParentFile(), file.getName() + ".bak");

        List<Furniture> loaded = tryReadChunkFile(file);
        if (loaded != null) return loaded;

        loaded = tryReadChunkFile(bak);
        if (loaded != null) {
            InteractibleFurniture.getInstance().getLogger()
                    .warning("Loaded furniture backup for chunk " + world + " " + chunkX + "," + chunkZ);
            return loaded;
        }
        return List.of();
    }

    private List<Furniture> tryReadChunkFile(File file) {
        if (file == null || !file.exists()) return null;

        try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (parsed == null || !parsed.isJsonObject()) return null;
            JsonObject root = parsed.getAsJsonObject();
            if (!root.has("furniture") || !root.get("furniture").isJsonArray()) {
                return List.of();
            }
            JsonArray arr = root.getAsJsonArray("furniture");

            List<Furniture> list = new ArrayList<>();
            for (JsonElement el : arr) {
                if (!el.isJsonObject()) continue;
                Furniture f = deserializeFurniture(el.getAsJsonObject());
                if (f != null) list.add(f);
            }
            return list;
        } catch (Exception e) {
            InteractibleFurniture.getInstance().getLogger()
                    .warning("Failed to parse furniture file " + file.getName() + ": " + e.getMessage());
            return null;
        }
    }

    // ------------------------------------------------------------------------
    //  FURNITURE SERIALIZATION
    // ------------------------------------------------------------------------

    private Map<String, Object> serializeFurniture(Furniture f) {
        Map<String, Object> obj = new HashMap<>();
        obj.put("id", f.getId());
        obj.put("type", f.getId());
        obj.put("entityId", f.getEntityId().toString());
        obj.put("location", serializeLocation(f.getLoc()));
        obj.put("yaw", f.getYaw());
        obj.put("carried", f.isCarried() || f.isPersistedCarried());

        f.getOriginBlockLocation().ifPresent(loc -> obj.put("originBlock", serializeLocation(loc)));
        f.getOriginBlockFace().ifPresent(face -> obj.put("originBlockFace", face.name()));

        if (!f.getBarrierBlocks().isEmpty()) {
            obj.put("barrierBlocks", f.getBarrierBlocks().stream()
                    .map(b -> serializeLocation(b.getLocation()))
                    .toList());
        }

        if (!f.getActiveSlots().isEmpty()) {
            Map<String, Object> slots = new HashMap<>();
            for (var entry : f.getActiveSlots().entrySet()) {
                PlacedSlot slot = entry.getValue();
                Map<String, Object> slotMap = new HashMap<>();
                if (slot.getDisplayStandId() != null) {
                    slotMap.put("displayStandId", slot.getDisplayStandId().toString());
                }
                ItemStack item = slot.getCurrentItem();
                if (item == null && slot.getDisplayStandId() != null) {
                    Entity stand = Bukkit.getEntity(slot.getDisplayStandId());
                    if (stand instanceof ItemDisplay display) {
                        item = display.getItemStack();
                    }
                }
                String encoded = ItemStackCodec.serialize(item);
                if (encoded != null) {
                    slotMap.put("item", encoded);
                }
                slots.put(entry.getKey(), slotMap);
            }
            obj.put("activeSlots", slots);
        }

        Map<String, Object> dataMap = new HashMap<>();

        if (!f.getVariables().isEmpty()) {
            dataMap.put("variables", f.getVariables());
        }

        if (f.getModelOverride() != null) {
            ModelData m = f.getModelOverride();
            dataMap.put("modelOverride", Map.of(
                    "display", m.getDisplay().name(),
                    "model", m.getModel()
            ));
        }

        if (!dataMap.isEmpty()) {
            obj.put("data", dataMap);
        }

        if (f.getInteractionEntityId() != null) {
            obj.put("interactionEntityId", f.getInteractionEntityId().toString());
        }

        return obj;
    }

    private Furniture deserializeFurniture(JsonObject obj) {
        String typeId = obj.has("type") ? obj.get("type").getAsString() : obj.get("id").getAsString();
        if (FurnitureLoader.getByString(typeId) == null) return null;

        Location loc = deserializeLocation(obj.getAsJsonObject("location"));
        if (loc == null) return null;
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

        if (obj.has("yaw")) {
            furniture.setYaw(obj.get("yaw").getAsFloat());
        }
        if (obj.has("carried") && obj.get("carried").getAsBoolean()) {
            furniture.setPersistedCarried(true);
        }

        if (obj.has("interactionEntityId")) {
            try {
                furniture.setInteractionEntityId(UUID.fromString(obj.get("interactionEntityId").getAsString()));
            } catch (IllegalArgumentException ignored) {}
        }

        if (obj.has("barrierBlocks") && obj.get("barrierBlocks").isJsonArray()) {
            for (JsonElement el : obj.getAsJsonArray("barrierBlocks")) {
                if (!el.isJsonObject()) continue;
                Location barrierLoc = deserializeLocation(el.getAsJsonObject());
                if (barrierLoc == null) continue;
                furniture.addBarrierBlock(barrierLoc.getBlock());
            }
        }

        if (obj.has("activeSlots")) {
            JsonObject slots = obj.getAsJsonObject("activeSlots");
            for (String key : slots.keySet()) {
                JsonObject sObj = slots.getAsJsonObject(key);
                if (furniture.getType() == null || furniture.getType().getSlot(key) == null) continue;
                PlacedSlot slot = furniture.getOrCreatePlacedSlot(key);

                if (sObj.has("displayStandId")) {
                    UUID dispId = UUID.fromString(sObj.get("displayStandId").getAsString());
                    slot.setDisplayStandId(dispId);
                }
                if (sObj.has("item")) {
                    ItemStack item = ItemStackCodec.deserialize(sObj.get("item").getAsString());
                    if (item != null) {
                        slot.setModel(item);
                    }
                }
            }
        }

        if (obj.has("data")) {
            JsonObject dataObj = obj.getAsJsonObject("data");
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
                furniture.setVariables(vars);
            }

            if (dataObj.has("modelOverride")) {
                JsonObject o = dataObj.getAsJsonObject("modelOverride");
                try {
                    Display d = Display.valueOf(o.get("display").getAsString());
                    String model = o.get("model").getAsString();
                    furniture.setModelOverride(new ModelData(d, model));
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
        if (obj == null) return null;
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
