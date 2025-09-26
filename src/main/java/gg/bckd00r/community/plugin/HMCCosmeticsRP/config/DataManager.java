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
        
        for (ModelData modelData : modelOverrides.values()) {
            modelData.setForceUpdate(true);
        }
    }

    private void loadModelOverrides() {
        modelOverrides.clear();
        if (!dataFile.exists()) {
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
        
        ConfigurationSection modelsSection = config.getConfigurationSection("models");
        if (modelsSection == null) {
            return;
        }
        
        for (String modelName : modelsSection.getKeys(false)) {
            ConfigurationSection modelSection = modelsSection.getConfigurationSection(modelName);
            if (modelSection != null) {
                ModelData modelData = new ModelData();
                
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
                            }
                        }
                    }
                }
                
                if (!modelData.getDisplayData().isEmpty()) {
                    modelOverrides.put(modelName.toLowerCase(), modelData);
                }
            }
        }
    }

    public void saveData() {
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save data.yml: " + e.getMessage());
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

    private Map<String, Map<String, Object>> extractDisplaySettings(JsonObject modelJson) {
        Map<String, Map<String, Object>> displaySettings = new HashMap<>();
        
        if (modelJson == null) {
            return displaySettings;
        }
        
        if (modelJson.has("display")) {
            JsonObject displayObj = modelJson.getAsJsonObject("display");
            
            for (String displayType : displayObj.keySet()) {
                
                if (displayObj.get(displayType).isJsonObject()) {
                    JsonObject typeObj = displayObj.getAsJsonObject(displayType);
                    Map<String, Object> settings = new HashMap<>();
                    
                    for (String key : Arrays.asList("rotation", "translation", "scale")) {
                        if (typeObj.has(key)) {
                            JsonElement element = typeObj.get(key);
                            
                            if (element.isJsonArray()) {
                                JsonArray array = element.getAsJsonArray();
                                List<Double> values = Arrays.asList(
                                    array.size() > 0 ? array.get(0).getAsDouble() : 0.0,
                                    array.size() > 1 ? array.get(1).getAsDouble() : 0.0,
                                    array.size() > 2 ? array.get(2).getAsDouble() : 0.0
                                );
                                settings.put(key, values);
                            }
                        }
                    }
                    
                    if (!settings.isEmpty()) {
                        displaySettings.put(displayType, settings);
                    }
                }
            }
        } else {
            Map<String, Object> headSettings = new HashMap<>();
            headSettings.put("rotation", Arrays.asList(0.0, 0.0, 0.0));
            headSettings.put("translation", Arrays.asList(0.0, 0.0, 0.0));
            headSettings.put("scale", Arrays.asList(1.0, 1.0, 1.0));
            displaySettings.put("head", headSettings);
        }
        
        return displaySettings;
    }
    
    public ModelData ensureModelExists(String modelName, JsonObject modelJson) {
        ModelData modelData = modelOverrides.get(modelName.toLowerCase());
        boolean isNewModel = (modelData == null);
        
        if (isNewModel) {
            modelData = new ModelData();
            
            if (modelJson != null) {
                Map<String, Map<String, Object>> displaySettings = extractDisplaySettings(modelJson);
                
                if (!displaySettings.isEmpty()) {
                    for (Map.Entry<String, Map<String, Object>> entry : displaySettings.entrySet()) {
                        modelData.addDisplayData(entry.getKey(), entry.getValue());
                    }
                }
                
                updateModelData(modelName, modelData);
            } else {
                Map<String, Object> headDisplay = new HashMap<>();
                headDisplay.put("rotation", Arrays.asList(0.0, 0.0, 0.0));
                headDisplay.put("translation", Arrays.asList(0.0, 0.0, 0.0));
                headDisplay.put("scale", Arrays.asList(1.0, 1.0, 1.0));
                modelData.addDisplayData("head", headDisplay);
                
                updateModelData(modelName, modelData);
            }
        } else {
            modelData.setForceUpdate(true);
        }
        
        return modelData;
    }
    
    public ModelData ensureModelExists(String modelName) {
        return ensureModelExists(modelName, null);
    }
    
    public void updateModelData(String modelName, ModelData modelData) {
        modelOverrides.put(modelName.toLowerCase(), modelData);
        
        if (dataConfig == null) {
            dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        }
        
        if (!dataConfig.contains("models")) {
            dataConfig.createSection("models");
        }
        
        String basePath = "models." + modelName;
        
        dataConfig.set(basePath + ".display", null);
        
        for (Map.Entry<String, Map<String, Object>> entry : modelData.getDisplayData().entrySet()) {
            String displayType = entry.getKey();
            
            for (Map.Entry<String, Object> dataEntry : entry.getValue().entrySet()) {
                String fullPath = basePath + ".display." + displayType + "." + dataEntry.getKey();
                Object value = dataEntry.getValue();
                
                dataConfig.set(fullPath, value);
            }
        }
        
        saveData();
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
