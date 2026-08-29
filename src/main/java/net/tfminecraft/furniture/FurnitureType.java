package net.tfminecraft.furniture;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.util.Vector;

import net.tfminecraft.enums.Display;
import net.tfminecraft.enums.SoundEffect;
import net.tfminecraft.furniture.data.DisplayData;
import net.tfminecraft.furniture.data.FurnitureDataContainer;
import net.tfminecraft.furniture.data.InteractionData;
import net.tfminecraft.furniture.data.ModelData;

/**
 * FurnitureType holds configuration for a furniture "type" loaded from furniture.yml.
 * Supports new `layers` format describing arbitrary 1/3/5 sized matrices per Y-layer.
 * Also supports item slots with display settings.
 */
public class FurnitureType {
    private final String id;
    private final String itemPath; // TLibs item path used to check held item
    private final boolean placeOnFloor;
    private final boolean placeOnWall;
    private final boolean placeOnRoof;
    private final boolean rotateToPlayer; // rotate to face player at 45-degree increments
    private final boolean solid; // legacy: place a single barrier block at the furniture location
    private final boolean pickup; //allow picking up the furniture with right click (if slots are empty)
    private final boolean placeInside; // anchor to clicked block center instead of block above
    private final Set<Material> allowedBlocks; // empty = any block; non-empty = whitelist
    private final List<boolean[][]> layers; // parsed layers (each is size x size boolean matrix)
    private final Map<String, SlotDefinition> slots;
    private final FurnitureDataContainer data;
    private final Map<SoundEffect, String> soundEffects = new HashMap<>();
    private final DisplayData displayData;
    private final InteractionData interactionData;

    public FurnitureType(String id, ConfigurationSection cfg) {
        this.id = id;
        this.itemPath = cfg.getString("item", "");
        if (cfg.isConfigurationSection("placement_options")) {
            ConfigurationSection placement = cfg.getConfigurationSection("placement_options");
            this.placeOnFloor = placement.getBoolean("floor", false);
            this.placeOnWall = placement.getBoolean("wall", false);
            this.placeOnRoof = placement.getBoolean("roof", false);
        } else {
            this.placeOnFloor = false;
            this.placeOnWall = false;
            this.placeOnRoof = false;
        }
        this.rotateToPlayer = cfg.getBoolean("rotate", true);
        this.solid = cfg.getBoolean("solid", false);
        this.pickup = cfg.getBoolean("pickup", false);
        this.placeInside = cfg.getBoolean("place-inside", false);
        this.allowedBlocks = new HashSet<>();
        for (String blockName : cfg.getStringList("allowed-blocks")) {
            try {
                allowedBlocks.add(Material.valueOf(blockName.toUpperCase()));
            } catch (IllegalArgumentException ignored) {}
        }
        this.displayData = cfg.isConfigurationSection("display") ? new DisplayData(cfg.getConfigurationSection("display")) : new DisplayData();
        this.interactionData = cfg.isConfigurationSection("interaction")
                ? new InteractionData(cfg.getConfigurationSection("interaction"))
                : null;
        this.layers = new ArrayList<>();
        this.slots = new HashMap<>();
        // templates map (optional)
        Map<String, SlotTemplate> templates = new HashMap<>();

        // Parse slots if present
        if (cfg.isConfigurationSection("slots")) {
            ConfigurationSection slotsSec = cfg.getConfigurationSection("slots");

            // If a nested templates section exists under slots, parse it first
            if (slotsSec.isConfigurationSection("templates")) {
                parseSlotTemplates(slotsSec.getConfigurationSection("templates"), templates);
            }

            for (String slotKey : slotsSec.getKeys(false)) {
                // skip the nested templates key
                if (slotKey.equals("templates")) continue;

                ConfigurationSection slotCfg = slotsSec.getConfigurationSection(slotKey);
                if (slotCfg == null) continue;

                // Parse location (layer,row,col)
                String locStr = slotCfg.getString("location", "1,1,1");
                String[] locParts = locStr.split(",");
                if (locParts.length != 3) continue;

                int layer = Integer.parseInt(locParts[0].trim());
                int row = Integer.parseInt(locParts[1].trim());
                int col = Integer.parseInt(locParts[2].trim());

                // Parse slot details (subslots inside this location group)
                for (String subSlotKey : slotCfg.getKeys(false)) {
                    if (subSlotKey.equals("location")) continue;
                    ConfigurationSection subSlotCfg = slotCfg.getConfigurationSection(subSlotKey);
                    if (subSlotCfg == null) continue;

                    // prepare base template if referenced
                    SlotTemplate base = null;
                    if (subSlotCfg.isString("template")) {
                        String tmplName = subSlotCfg.getString("template");
                        if (tmplName != null && templates.containsKey(tmplName)) base = templates.get(tmplName);
                    }
                    // also allow parent slot group to define a template
                    if (base == null && slotCfg.isString("template")) {
                        String tmplName = slotCfg.getString("template");
                        if (tmplName != null && templates.containsKey(tmplName)) base = templates.get(tmplName);
                    }

                    // Get offset (override template if present)
                    Vector offset = base != null ? base.offset : null;
                    if (subSlotCfg.isConfigurationSection("offset")) {
                        ConfigurationSection offsetCfg = subSlotCfg.getConfigurationSection("offset");
                        offset = new Vector(
                                offsetCfg.getDouble("x", offset != null ? offset.getX() : 0.0),
                                offsetCfg.getDouble("y", offset != null ? offset.getY() : 0.0),
                                offsetCfg.getDouble("z", offset != null ? offset.getZ() : 0.0)
                        );
                    }

                    // Get whitelist (template or explicit)
                    List<String> whitelist = base != null ? base.whitelist : null;
                    if (subSlotCfg.isList("whitelist") || subSlotCfg.isString("whitelist")) {
                        whitelist = subSlotCfg.getStringList("whitelist");
                    }

                    // Get display settings (merge template then override)
                    Vector displayRot = base != null ? base.displayRot : null;
                    Vector displayScale = base != null ? base.displayScale : null;
                    Vector displayPos = base != null ? base.displayPos : null;
                    if (subSlotCfg.isConfigurationSection("display")) {
                        ConfigurationSection displayCfg = subSlotCfg.getConfigurationSection("display");

                        if (displayCfg.isConfigurationSection("rotation")) {
                            ConfigurationSection rotCfg = displayCfg.getConfigurationSection("rotation");
                            displayRot = new Vector(
                                    rotCfg.getDouble("x", displayRot != null ? displayRot.getX() : 0.0),
                                    rotCfg.getDouble("y", displayRot != null ? displayRot.getY() : 0.0),
                                    rotCfg.getDouble("z", displayRot != null ? displayRot.getZ() : 0.0)
                            );
                        }

                        if (displayCfg.isConfigurationSection("scale")) {
                            ConfigurationSection scaleCfg = displayCfg.getConfigurationSection("scale");
                            displayScale = new Vector(
                                    scaleCfg.getDouble("x", displayScale != null ? displayScale.getX() : 0.6),
                                    scaleCfg.getDouble("y", displayScale != null ? displayScale.getY() : 0.6),
                                    scaleCfg.getDouble("z", displayScale != null ? displayScale.getZ() : 0.6)
                            );
                        }

                        if (displayCfg.isConfigurationSection("position")) {
                            ConfigurationSection posCfg = displayCfg.getConfigurationSection("position");
                            displayPos = new Vector(
                                    posCfg.getDouble("x", displayPos != null ? displayPos.getX() : 0.0),
                                    posCfg.getDouble("y", displayPos != null ? displayPos.getY() : 0.0),
                                    posCfg.getDouble("z", displayPos != null ? displayPos.getZ() : 0.0)
                            );
                        }
                    }

                    SlotDefinition slot = new SlotDefinition(
                            subSlotKey, layer, row, col,
                            offset, whitelist,
                            displayRot, displayScale, displayPos,
                            subSlotCfg.contains("interactible")
                                    ? subSlotCfg.getBoolean("interactible")
                                    : whitelist != null && !whitelist.isEmpty());
                    slots.put(subSlotKey, slot);
                }
            }
        }

        if(cfg.isConfigurationSection("model")) {
            data = new FurnitureDataContainer(cfg.getConfigurationSection("model"));
        } else {
            data = new FurnitureDataContainer(new ModelData(Display.ITEM_DISPLAY, "v.paper"));
        }

        if(cfg.isConfigurationSection("sound")) {
            ConfigurationSection soundsSec = cfg.getConfigurationSection("sound");
            for (String soundKey : soundsSec.getKeys(false)) {
                try {
                    SoundEffect effect = SoundEffect.valueOf(soundKey.toUpperCase());
                    String soundPath = soundsSec.getString(soundKey, "");
                    if (!soundPath.isEmpty()) {
                        soundEffects.put(effect, soundPath);
                    }
                } catch (IllegalArgumentException ex) {
                    // Invalid sound effect key, ignore
                }
            }
        }

        // Also allow top-level templates under 'slot-templates' or 'templates'
        if (cfg.isConfigurationSection("slot-templates")) {
            parseSlotTemplates(cfg.getConfigurationSection("slot-templates"), templates);
        } else if (cfg.isConfigurationSection("templates")) {
            parseSlotTemplates(cfg.getConfigurationSection("templates"), templates);
        }

        // parse `layers` if present
        if (cfg.isConfigurationSection("layers")) {
            ConfigurationSection layersSec = cfg.getConfigurationSection("layers");
            // collect numeric keys and sort
            List<Integer> numericKeys = new ArrayList<>();
            for (String k : layersSec.getKeys(false)) {
                try { numericKeys.add(Integer.parseInt(k)); } catch (NumberFormatException ex) { }
            }
            Collections.sort(numericKeys);

            for (int key : numericKeys) {
                Object raw = layersSec.get(String.valueOf(key));
                List<String> rows = new ArrayList<>();
                if (raw instanceof List) {
                    for (Object o : (List<?>) raw) rows.add(String.valueOf(o));
                } else if (layersSec.isConfigurationSection(String.valueOf(key))) {
                    ConfigurationSection layerSec = layersSec.getConfigurationSection(String.valueOf(key));
                    // if the layer is a section with numeric keys or plain keys
                    for (String rowKey : layerSec.getKeys(false)) {
                        Object val = layerSec.get(rowKey);
                        if (val != null) rows.add(String.valueOf(val));
                    }
                } else if (raw != null) {
                    rows.add(String.valueOf(raw));
                }

                if (rows.isEmpty()) continue;
                int size = rows.size();
                if (size % 2 == 0 || size > 5) continue; // must be odd and <=5
                boolean[][] mat = new boolean[size][size];
                for (int r = 0; r < size; r++) {
                    String row = rows.get(r).trim();
                    for (int c = 0; c < Math.min(row.length(), size); c++) {
                        char ch = row.charAt(c);
                        mat[r][c] = (ch == 'X' || ch == 'x');
                    }
                }
                layers.add(mat);
            }
        }

        // legacy fallback: if layers not defined and solid==true, provide single 1x1 layer
        if (layers.isEmpty() && this.solid) {
            boolean[][] m = new boolean[1][1]; m[0][0] = true; layers.add(m);
        }
    }

    public FurnitureDataContainer getData() {
        return data;
    }
    public boolean hasSoundEffect(SoundEffect effect) {
        return soundEffects.containsKey(effect);
    }
    public String getSoundEffectPath(SoundEffect effect) {
        return soundEffects.get(effect);
    }
    public String getId() { return id; }
    public String getItemPath() { return itemPath; }
    public boolean canPlaceOnFloor() { return placeOnFloor; }
    public boolean canPlaceOnWall() { return placeOnWall; }
    public boolean canPlaceOnRoof() { return placeOnRoof; }
    public boolean shouldRotateToPlayer() { return rotateToPlayer; }
    public boolean isSolid() { return solid; }
    public List<boolean[][]> getLayers() { return layers; }
    public Map<String, SlotDefinition> getSlots() { return slots; }
    public boolean canPickup() { return pickup; }
    public boolean canPlaceInside() { return placeInside; }
    public boolean isAllowedBlock(Material material) {
        return allowedBlocks.isEmpty() || allowedBlocks.contains(material);
    }
    public DisplayData getDisplayData() { return displayData; }
    public boolean hasInteraction() { return interactionData != null; }
    public InteractionData getInteractionData() { return interactionData; }
    
    /**
     * Get a specific slot by its ID
     */
    public SlotDefinition getSlot(String id) {
        return slots.get(id);
    }

    public List<SlotDefinition> getSlotsForBlock(int layer, int row, int col) {
        List<SlotDefinition> result = new ArrayList<>();
        for (SlotDefinition slot : slots.values()) {
            if (slot.getLayer() == layer && slot.getRow() == row && slot.getCol() == col) {
                result.add(slot);
            }
        }
        return result;
    }

    // ---------- Slot template parsing helpers ----------
    private void parseSlotTemplates(ConfigurationSection tmplSec, Map<String, SlotTemplate> out) {
        for (String tname : tmplSec.getKeys(false)) {
            ConfigurationSection tcfg = tmplSec.getConfigurationSection(tname);
            if (tcfg == null) continue;

            Vector offset = null;
            if (tcfg.isConfigurationSection("offset")) {
                ConfigurationSection off = tcfg.getConfigurationSection("offset");
                offset = new Vector(off.getDouble("x", 0), off.getDouble("y", 0), off.getDouble("z", 0));
            }

            List<String> whitelist = tcfg.getStringList("whitelist");

            Vector displayRot = null;
            Vector displayScale = null;
            Vector displayPos = null;
            if (tcfg.isConfigurationSection("display")) {
                ConfigurationSection d = tcfg.getConfigurationSection("display");
                if (d.isConfigurationSection("rotation")) {
                    ConfigurationSection r = d.getConfigurationSection("rotation");
                    displayRot = new Vector(r.getDouble("x", 0), r.getDouble("y", 0), r.getDouble("z", 0));
                }
                if (d.isConfigurationSection("scale")) {
                    ConfigurationSection s = d.getConfigurationSection("scale");
                    displayScale = new Vector(s.getDouble("x", 0.6), s.getDouble("y", 0.6), s.getDouble("z", 0.6));
                }
                if (d.isConfigurationSection("position")) {
                    ConfigurationSection p = d.getConfigurationSection("position");
                    displayPos = new Vector(p.getDouble("x", 0), p.getDouble("y", 0), p.getDouble("z", 0));
                }
            }

            out.put(tname, new SlotTemplate(offset, whitelist, displayRot, displayScale, displayPos));
        }
    }

    private static class SlotTemplate {
        final Vector offset;
        final List<String> whitelist;
        final Vector displayRot;
        final Vector displayScale;
        final Vector displayPos;

        SlotTemplate(Vector offset, List<String> whitelist, Vector displayRot, Vector displayScale, Vector displayPos) {
            this.offset = offset;
            this.whitelist = whitelist;
            this.displayRot = displayRot;
            this.displayScale = displayScale;
            this.displayPos = displayPos;
        }
    }
}
