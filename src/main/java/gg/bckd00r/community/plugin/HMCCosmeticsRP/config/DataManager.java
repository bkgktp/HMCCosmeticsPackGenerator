package gg.bckd00r.community.plugin.HMCCosmeticsRP.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DataManager {
    private final JavaPlugin plugin;
    private File dataFile;
    private FileConfiguration dataConfig;
    private final Map<String, ModelData> modelOverrides = new HashMap<>();

    public DataManager(JavaPlugin plugin) {
        this.plugin = plugin;
        loadData();
    }

    public void loadData() {
        dataFile = new File(plugin.getDataFolder(), "data.yml");
        if (!dataFile.exists()) {
            plugin.saveResource("data.yml", false);
        }
        
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        loadModelOverrides();
        
        // After reloading data.yml, mark all existing models for force update
        // This ensures that JSON files will be updated with the current data.yml values
        plugin.getLogger().info("Marking all loaded models for force update after data.yml reload");
        for (ModelData modelData : modelOverrides.values()) {
            modelData.setForceUpdate(true);
        }
    }

    private void loadModelOverrides() {
        modelOverrides.clear();
        if (!dataFile.exists()) {
            plugin.getLogger().info("data.yml not found, will be created when needed");
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
        
        // Check if we have the models section
        ConfigurationSection modelsSection = config.getConfigurationSection("models");
        if (modelsSection == null) {
            plugin.getLogger().info("No models section found in data.yml, will be created when needed");
            return;
        }
        
        for (String modelName : modelsSection.getKeys(false)) {
            ConfigurationSection modelSection = modelsSection.getConfigurationSection(modelName);
            if (modelSection != null) {
                ModelData modelData = new ModelData();
                
                // Load display settings
                ConfigurationSection displaySection = modelSection.getConfigurationSection("display");
                if (displaySection != null) {
                    for (String displayType : displaySection.getKeys(false)) {
                        if (displaySection.isConfigurationSection(displayType)) {
                            ConfigurationSection typeSection = displaySection.getConfigurationSection(displayType);
                            Map<String, Object> displayData = new HashMap<>();
                            for (String key : typeSection.getKeys(false)) {
                                Object value = typeSection.get(key);
                                if (value != null) {
                                    displayData.put(key, value);
                                }
                            }
                            if (!displayData.isEmpty()) {
                                modelData.addDisplayData(displayType, displayData);
                                plugin.getLogger().fine("Loaded display settings for " + modelName + "." + displayType);
                            }
                        }
                    }
                }
                
                if (!modelData.getDisplayData().isEmpty()) {
                    modelOverrides.put(modelName.toLowerCase(), modelData);
                    plugin.getLogger().info("Loaded model overrides for: " + modelName);
                } else {
                    plugin.getLogger().warning("No valid display data found for model: " + modelName);
                }
            }
        }
    }

    public void saveData() {
        try {
            plugin.getLogger().info("Attempting to save data.yml to: " + dataFile.getAbsolutePath());
            plugin.getLogger().info("Data config contents: " + dataConfig.saveToString());
            dataConfig.save(dataFile);
            plugin.getLogger().info("Successfully saved data.yml");
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save data.yml: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public ModelData getModelData(String modelName) {
        String key = modelName.toLowerCase();
        if (!modelOverrides.containsKey(key)) {
            // Create a new ModelData if it doesn't exist
            ModelData modelData = new ModelData();
            modelOverrides.put(key, modelData);
            return modelData;
        }
        return modelOverrides.get(key);
    }
    
    public Map<String, ModelData> getAllModels() {
        return new HashMap<>(modelOverrides);
    }

    /**
     * Extracts display settings from a model's JSON
     * @param modelJson The model's JSON object
     * @return Map of display settings by display type
     */
    private Map<String, Map<String, Object>> extractDisplaySettings(JsonObject modelJson) {
        Map<String, Map<String, Object>> displaySettings = new HashMap<>();
        
        plugin.getLogger().info("Extracting display settings from JSON...");
        
        if (modelJson == null) {
            plugin.getLogger().warning("Model JSON is null, cannot extract display settings");
            return displaySettings;
        }
        
        plugin.getLogger().info("Model JSON keys: " + modelJson.keySet().toString());
        
        if (modelJson.has("display")) {
            plugin.getLogger().info("Found display section in JSON");
            JsonObject displayObj = modelJson.getAsJsonObject("display");
            plugin.getLogger().info("Display object keys: " + displayObj.keySet().toString());
            
            for (String displayType : displayObj.keySet()) {
                plugin.getLogger().info("Processing display type: " + displayType);
                
                if (displayObj.get(displayType).isJsonObject()) {
                    JsonObject typeObj = displayObj.getAsJsonObject(displayType);
                    Map<String, Object> settings = new HashMap<>();
                    
                    plugin.getLogger().info("Display type " + displayType + " keys: " + typeObj.keySet().toString());
                    
                    // Extract rotation, translation, and scale
                    for (String key : Arrays.asList("rotation", "translation", "scale")) {
                        if (typeObj.has(key)) {
                            JsonElement element = typeObj.get(key);
                            plugin.getLogger().info("Found " + key + " in " + displayType + ": " + element.toString());
                            
                            if (element.isJsonArray()) {
                                JsonArray array = element.getAsJsonArray();
                                List<Double> values = Arrays.asList(
                                    array.size() > 0 ? array.get(0).getAsDouble() : 0.0,
                                    array.size() > 1 ? array.get(1).getAsDouble() : 0.0,
                                    array.size() > 2 ? array.get(2).getAsDouble() : 0.0
                                );
                                settings.put(key, values);
                                plugin.getLogger().info("Added " + key + " = " + values.toString());
                            }
                        }
                    }
                    
                    if (!settings.isEmpty()) {
                        displaySettings.put(displayType, settings);
                        plugin.getLogger().info("Added display settings for " + displayType + ": " + settings.toString());
                    } else {
                        plugin.getLogger().warning("No valid settings found for display type: " + displayType);
                    }
                } else {
                    plugin.getLogger().warning("Display type " + displayType + " is not a JSON object");
                }
            }
        } else {
            plugin.getLogger().info("No display section found in JSON, creating default settings");
            // If no display section exists, create default head display settings
            Map<String, Object> headSettings = new HashMap<>();
            headSettings.put("rotation", Arrays.asList(0.0, 0.0, 0.0));
            headSettings.put("translation", Arrays.asList(0.0, 0.0, 0.0));
            headSettings.put("scale", Arrays.asList(1.0, 1.0, 1.0));
            displaySettings.put("head", headSettings);
            plugin.getLogger().info("Created default head display settings");
        }
        
        plugin.getLogger().info("Final extracted display settings: " + displaySettings.toString());
        return displaySettings;
    }
    
    /**
     * Ensures that a model exists in the data file with default values if it doesn't exist
     * @param modelName The name of the model to ensure exists
     * @param modelJson Optional JSON object containing the model data to extract display settings from
     * @return The existing or newly created ModelData
     */
    public ModelData ensureModelExists(String modelName, JsonObject modelJson) {
        plugin.getLogger().info("=== ENSURE MODEL EXISTS START ===");
        plugin.getLogger().info("Checking if model exists in data.yml: " + modelName);
        plugin.getLogger().info("Model JSON provided: " + (modelJson != null));
        
        ModelData modelData = modelOverrides.get(modelName.toLowerCase());
        boolean isNewModel = (modelData == null);
        
        plugin.getLogger().info("Is new model: " + isNewModel);
        plugin.getLogger().info("Current modelOverrides size: " + modelOverrides.size());
        plugin.getLogger().info("Current modelOverrides keys: " + modelOverrides.keySet().toString());
        
        if (isNewModel) {
            plugin.getLogger().info("Model not found in data.yml, creating new entry: " + modelName);
            modelData = new ModelData();
            
            // For new models, extract data from JSON if available
            if (modelJson != null) {
                plugin.getLogger().info("Extracting display settings from JSON for new model: " + modelName);
                Map<String, Map<String, Object>> displaySettings = extractDisplaySettings(modelJson);
                
                plugin.getLogger().info("Extracted " + displaySettings.size() + " display settings from JSON");
                
                if (!displaySettings.isEmpty()) {
                    plugin.getLogger().info("Adding " + displaySettings.size() + " display settings to model data");
                    
                    // Add all display settings from the model JSON
                    for (Map.Entry<String, Map<String, Object>> entry : displaySettings.entrySet()) {
                        plugin.getLogger().info("Adding display data: " + entry.getKey() + " = " + entry.getValue().toString());
                        modelData.addDisplayData(entry.getKey(), entry.getValue());
                    }
                    
                    plugin.getLogger().info("Model data after adding display settings: " + modelData.getDisplayData().toString());
                } else {
                    plugin.getLogger().warning("No display settings extracted from JSON, this should not happen as we create defaults");
                }
                
                // Save the new model data to data.yml
                plugin.getLogger().info("Calling updateModelData to save to data.yml...");
                updateModelData(modelName, modelData);
                plugin.getLogger().info("Finished saving new model data to data.yml: " + modelName);
            } else {
                // No JSON provided, use defaults
                plugin.getLogger().warning("No model JSON provided for new model, using defaults: " + modelName);
                Map<String, Object> headDisplay = new HashMap<>();
                headDisplay.put("rotation", Arrays.asList(0.0, 0.0, 0.0));
                headDisplay.put("translation", Arrays.asList(0.0, 0.0, 0.0));
                headDisplay.put("scale", Arrays.asList(1.0, 1.0, 1.0));
                modelData.addDisplayData("head", headDisplay);
                
                // Save the new model data to data.yml
                updateModelData(modelName, modelData);
            }
        } else {
            plugin.getLogger().info("Model already exists in data.yml, using existing data: " + modelName);
            plugin.getLogger().info("Existing model data: " + modelData.getDisplayData().toString());
            
            // For existing models, we should still ensure the JSON file gets updated with current data.yml values
            // This is important when data.yml has been manually edited and reloaded
            plugin.getLogger().info("Marking existing model for update to ensure JSON reflects current data.yml values");
            modelData.setForceUpdate(true);
        }
        
        plugin.getLogger().info("=== ENSURE MODEL EXISTS END ===");
        return modelData;
    }
    
    // Overloaded method for backward compatibility
    public ModelData ensureModelExists(String modelName) {
        return ensureModelExists(modelName, null);
    }
    
    public void updateModelData(String modelName, ModelData modelData) {
        plugin.getLogger().info("Updating model data for: " + modelName);
        plugin.getLogger().info("Display data: " + modelData.getDisplayData().toString());
        
        modelOverrides.put(modelName.toLowerCase(), modelData);
        
        // Ensure data config is properly initialized
        if (dataConfig == null) {
            plugin.getLogger().warning("DataConfig is null, reinitializing...");
            dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        }
        
        // Ensure models section exists
        if (!dataConfig.contains("models")) {
            plugin.getLogger().info("Creating models section in data.yml");
            dataConfig.createSection("models");
        }
        
        // Update the config - save under models.modelName
        String basePath = "models." + modelName;
        plugin.getLogger().info("Setting data at path: " + basePath);
        
        // Clear existing display data for this model
        dataConfig.set(basePath + ".display", null);
        
        // Save display settings
        for (Map.Entry<String, Map<String, Object>> entry : modelData.getDisplayData().entrySet()) {
            String displayType = entry.getKey();
            plugin.getLogger().info("Processing display type: " + displayType);
            
            for (Map.Entry<String, Object> dataEntry : entry.getValue().entrySet()) {
                String fullPath = basePath + ".display." + displayType + "." + dataEntry.getKey();
                Object value = dataEntry.getValue();
                
                plugin.getLogger().info("Setting " + fullPath + " = " + value + " (type: " + value.getClass().getSimpleName() + ")");
                dataConfig.set(fullPath, value);
            }
        }
        
        // Verify the data was set correctly
        plugin.getLogger().info("Verifying data was set for: " + basePath);
        if (dataConfig.contains(basePath)) {
            plugin.getLogger().info("Data exists at path: " + basePath);
        } else {
            plugin.getLogger().warning("Data NOT found at path: " + basePath);
        }
        
        // Save the data to file
        saveData();
        plugin.getLogger().info("Successfully saved model data for: " + modelName);
    }

    public static class ModelData {
        private final Map<String, Map<String, Object>> displayData = new HashMap<>();
        private boolean forceUpdate = false;

        public void addDisplayData(String displayType, Map<String, Object> data) {
            displayData.put(displayType.toLowerCase(), data);
        }

        public Map<String, Map<String, Object>> getDisplayData() {
            return displayData;
        }

        public Map<String, Object> getDisplayData(String displayType) {
            return displayData.getOrDefault(displayType, new HashMap<>());
        }

        public void setForceUpdate(boolean forceUpdate) {
            this.forceUpdate = forceUpdate;
        }

        public boolean shouldForceUpdate() {
            return forceUpdate;
        }
    }
}
