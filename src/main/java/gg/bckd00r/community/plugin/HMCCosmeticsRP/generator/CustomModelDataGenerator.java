package gg.bckd00r.community.plugin.HMCCosmeticsRP.generator;

import com.google.gson.*;
import gg.bckd00r.community.plugin.HMCCosmeticsRP.HMCCosmeticsPackPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class CustomModelDataGenerator {
    private final HMCCosmeticsPackPlugin plugin;
    private final Map<String, List<ModelEntry>> modelEntries = new HashMap<>();
    private final AtomicInteger currentModelData;
    
    public CustomModelDataGenerator(HMCCosmeticsPackPlugin plugin) {
        this.plugin = plugin;
        // REMOVED: Custom model data system - using fixed start value
        this.currentModelData = new AtomicInteger(1000);
    }
    
    /**
     * Registers a new model for custom model data
     * @param material The material this model is for (e.g., "PAPER", "LEATHER_HORSE_ARMOR")
     * @param modelPath The path to the model (e.g., "hmc:item/hat1")
     * @return The custom model data value assigned to this model
     */
    public int registerModel(String material, String modelPath) {
        int modelData = currentModelData.getAndIncrement();
        ModelEntry entry = ModelEntry.create(modelData, modelPath, material.toUpperCase());
        modelEntries.computeIfAbsent(material.toUpperCase(), k -> new ArrayList<>())
                  .add(entry);
        return modelData;
    }
    
    /**
     * Gets the model data value for a given material and model path
     * @param material The material to look up
     * @param modelPath The model path to look up
     * @return The custom model data value, or -1 if not found
     */
    public int getModelData(String material, String modelPath) {
        ModelEntry entry = ModelEntry.find(material, modelPath);
        return entry != null ? entry.getModelData() : -1;
    }
    
    /**
     * Generates all the necessary JSON files for custom model data
     * Creates both legacy (models/item/) and modern (items/) formats for maximum compatibility
     */
    public void generateModelJsons() {
        // Create the directory structure for both formats
        String packId = plugin.getConfigManager().getResourcePackId();
        File outputDir = new File(plugin.getDataFolder(), "output/" + packId);
        File assetsDir = new File(outputDir, "assets");
        File minecraftDir = new File(assetsDir, "minecraft");
        
        // Modern format: assets/minecraft/items/
        File itemsDir = new File(minecraftDir, "items");
        if (!itemsDir.exists() && !itemsDir.mkdirs()) {
            plugin.getLogger().warning("Failed to create directory: " + itemsDir.getAbsolutePath());
            return;
        }
        
        // Legacy format: assets/minecraft/models/item/
        File modelsDir = new File(minecraftDir, "models");
        File modelsItemDir = new File(modelsDir, "item");
        if (!modelsItemDir.exists() && !modelsItemDir.mkdirs()) {
            plugin.getLogger().warning("Failed to create directory: " + modelsItemDir.getAbsolutePath());
            return;
        }
        
        // Generate JSON for each material
        for (Map.Entry<String, List<ModelEntry>> entry : modelEntries.entrySet()) {
            String material = entry.getKey();
            List<ModelEntry> entries = entry.getValue();
            
            // === MODERN FORMAT (items/) ===
            JsonObject modernRoot = new JsonObject();
            JsonObject modernModelObj = new JsonObject();
            modernRoot.add("model", modernModelObj);
            
            modernModelObj.addProperty("type", "range_dispatch");
            modernModelObj.addProperty("property", "custom_model_data");

            JsonArray modernEntriesArray = getJsonElements(entries);
            modernModelObj.add("entries", modernEntriesArray);
            
            // Add fallback model for modern format
            JsonObject modernFallback = new JsonObject();
            modernFallback.addProperty("type", "model");
            modernFallback.addProperty("model", "item/" + material.toLowerCase());
            modernModelObj.add("fallback", modernFallback);
            
            // Write modern format to items directory
            File modernOutputFile = new File(itemsDir, material.toLowerCase() + ".json");
            try (FileWriter writer = new FileWriter(modernOutputFile)) {
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                gson.toJson(modernRoot, writer);
                plugin.getLogger().info("Generated modern format: " + modernOutputFile.getAbsolutePath());
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to write modern format for " + material + ": " + e.getMessage());
            }
            
            // === LEGACY FORMAT (models/item/) ===
            JsonObject legacyRoot = new JsonObject();
            JsonArray overrides = new JsonArray();
            
            // Add overrides for each model
            for (ModelEntry modelEntry : entries) {
                JsonObject override = new JsonObject();
                JsonObject predicate = new JsonObject();
                predicate.addProperty("custom_model_data", modelEntry.getModelData());
                override.add("predicate", predicate);
                override.addProperty("model", modelEntry.getModelPath());
                overrides.add(override);
            }
            
            legacyRoot.add("overrides", overrides);
            legacyRoot.addProperty("parent", "minecraft:item/generated");
            
            // Add textures
            JsonObject textures = new JsonObject();
            textures.addProperty("layer0", "minecraft:item/" + material.toLowerCase());
            legacyRoot.add("textures", textures);
            
            // Write legacy format to models/item directory
            File legacyOutputFile = new File(modelsItemDir, material.toLowerCase() + ".json");
            try (FileWriter writer = new FileWriter(legacyOutputFile)) {
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                gson.toJson(legacyRoot, writer);
                plugin.getLogger().info("Generated legacy format: " + legacyOutputFile.getAbsolutePath());
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to write legacy format for " + material + ": " + e.getMessage());
            }
        }
    }

    private static @NotNull JsonArray getJsonElements(List<ModelEntry> entries) {
        JsonArray entriesArray = new JsonArray();

        for (ModelEntry modelEntry : entries) {
            JsonObject entryObj = new JsonObject();
            entryObj.addProperty("threshold", modelEntry.modelData);

            JsonObject model = new JsonObject();
            model.addProperty("type", "model");
            model.addProperty("model", modelEntry.modelPath);

            entryObj.add("model", model);
            entriesArray.add(entryObj);
        }
        return entriesArray;
    }

    private static class ModelEntry {
        private static final Map<String, ModelEntry> ENTRY_CACHE = new HashMap<>();
        
        private final int modelData;
        private final String modelPath;
        private final String material;
        
        private ModelEntry(int modelData, String modelPath, String material) {
            this.modelData = modelData;
            this.modelPath = modelPath;
            this.material = material;
        }
        
        public static ModelEntry create(int modelData, String modelPath, String material) {
            String key = (material + ":" + modelPath).toLowerCase();
            ModelEntry entry = new ModelEntry(modelData, modelPath, material);
            ENTRY_CACHE.put(key, entry);
            return entry;
        }
        
        public static ModelEntry find(String material, String modelPath) {
            if (material == null || modelPath == null) {
                return null;
            }
            return ENTRY_CACHE.get((material + ":" + modelPath).toLowerCase());
        }
        
        public int getModelData() {
            return modelData;
        }

        public String getModelPath() {
            return modelPath;
        }
        
        public String getMaterial() {
            return material;
        }
        
        @Override
        public String toString() {
            return "ModelEntry{" +
                    "modelData=" + modelData +
                    ", modelPath='" + modelPath + '\'' +
                    ", material='" + material + '\'' +
                    '}';
        }
    }
}
