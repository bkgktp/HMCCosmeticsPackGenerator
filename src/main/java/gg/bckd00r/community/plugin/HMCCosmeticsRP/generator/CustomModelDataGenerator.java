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
        this.currentModelData = new AtomicInteger(plugin.getConfigManager().getCustomModelDataStartValue());
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
     */
    public void generateModelJsons() {
        if (plugin.getConfigManager().useItemModelComponent()) {
            return; // Skip if using item model components
        }
        
        // Create the directory structure: output/{packId}/assets/minecraft/items/
        String packId = plugin.getConfigManager().getResourcePackId();
        File outputDir = new File(plugin.getDataFolder(), "output/" + packId);
        File assetsDir = new File(outputDir, "assets");
        File minecraftDir = new File(assetsDir, "minecraft");
        File itemsDir = new File(minecraftDir, "items");
        
        if (!itemsDir.exists() && !itemsDir.mkdirs()) {
            plugin.getLogger().warning("Failed to create directory: " + itemsDir.getAbsolutePath());
            return;
        }
        
        // Generate JSON for each material
        for (Map.Entry<String, List<ModelEntry>> entry : modelEntries.entrySet()) {
            String material = entry.getKey();
            List<ModelEntry> entries = entry.getValue();
            
            JsonObject root = new JsonObject();
            JsonObject modelObj = new JsonObject();
            root.add("model", modelObj);
            
            modelObj.addProperty("type", "range_dispatch");
            modelObj.addProperty("property", "custom_model_data");

            JsonArray entriesArray = getJsonElements(entries);

            modelObj.add("entries", entriesArray);
            
            // Add fallback model
            JsonObject fallback = new JsonObject();
            fallback.addProperty("type", "model");
            fallback.addProperty("model", "item/" + material.toLowerCase());
            modelObj.add("fallback", fallback);
            
            // Write to file in the items directory
            File outputFile = new File(itemsDir, material.toLowerCase() + ".json");
            try (FileWriter writer = new FileWriter(outputFile)) {
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                gson.toJson(root, writer);
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to write custom model data for " + material + ": " + e.getMessage());
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
