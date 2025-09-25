package gg.bckd00r.community.plugin.HMCCosmeticsRP.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;

public class ConfigManager {
    private final JavaPlugin plugin;
    private FileConfiguration config;

    // Default values
    private String resourcePackId = "HMCCosmetics";
    private String namespace = "hmc";
    private boolean useItemModelComponent = true;
    private final Map<String, String> defaultMaterials = new HashMap<>();
    private int customModelDataStart = 1000;
    private String customOutputPath = "";

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        // Save default config if it doesn't exist
        plugin.saveDefaultConfig();

        // Reload config from disk
        plugin.reloadConfig();
        config = plugin.getConfig();

        // Add default values if they don't exist
        config.addDefault("resource-pack.id", resourcePackId);
        config.addDefault("resource-pack.namespace", namespace);
        config.addDefault("settings.use-item-model-component", useItemModelComponent);
        config.addDefault("settings.custom-model-data.start-value", customModelDataStart);
        config.addDefault("output.custom-path", customOutputPath);
        
        // Add default materials configuration
        Map<String, String> defaultMaterialMap = new HashMap<>();
        defaultMaterialMap.put("HELMET", "LEATHER_HELMET");
        defaultMaterialMap.put("CHESTPLATE", "LEATHER_CHESTPLATE");
        defaultMaterialMap.put("LEGGINGS", "LEATHER_LEGGINGS");
        defaultMaterialMap.put("BOOTS", "LEATHER_BOOTS");
        defaultMaterialMap.put("BACKPACK", "LEATHER_CHESTPLATE");
        defaultMaterialMap.put("BALLOON", "LEATHER_HORSE_ARMOR");
        defaultMaterialMap.put("OFFHAND", "PAPER");
        
        config.addDefault("settings.default-materials", defaultMaterialMap);
        
        config.options().copyDefaults(true);
        
        // Save the config to ensure new defaults are written
        plugin.saveConfig();
        
        // Load the values
        resourcePackId = config.getString("resource-pack.id", resourcePackId);
        namespace = config.getString("resource-pack.namespace", namespace);
        useItemModelComponent = config.getBoolean("settings.use-item-model-component", useItemModelComponent);
        saveConfig();

        // Load values
        resourcePackId = config.getString("resource-pack.id", resourcePackId);
        namespace = config.getString("resource-pack.namespace", namespace).toLowerCase();
        
        // Load default materials
        if (config.isConfigurationSection("settings.default-materials")) {
            for (String key : config.getConfigurationSection("settings.default-materials").getKeys(false)) {
                defaultMaterials.put(key, config.getString("settings.default-materials." + key));
            }
        }
        
        // Load custom model data start value
        customModelDataStart = config.getInt("settings.custom-model-data.start-value", 21000);
        
        // Load custom output path
        customOutputPath = config.getString("output.custom-path", "");
    }

    public void saveConfig() {
        plugin.saveConfig();
    }

    public String getResourcePackId() {
        return resourcePackId;
    }

    public String getNamespace() {
        return namespace;
    }
    
    /**
     * Gets the default material for a cosmetic type
     * @param type The cosmetic type (e.g., "HELMET", "CHESTPLATE")
     * @return The material name as a string, or PAPER if not found
     */
    public String getDefaultMaterial(String type) {
        return defaultMaterials.getOrDefault(type.toUpperCase(), "PAPER");
    }
    
    /**
     * Gets the starting value for custom model data
     * @return The starting value for custom model data
     */
    public int getCustomModelDataStartValue() {
        return customModelDataStart;
    }
    
    /**
     * Gets the custom output path where the generated pack should be copied to
     * @return The custom output path, or null if not set
     */
    public String getCustomOutputPath() {
        return customOutputPath == null || customOutputPath.isEmpty() ? null : customOutputPath;
    }
    
    public boolean useItemModelComponent() {
        return useItemModelComponent;
    }

    /**
     * Gets the base output directory for models and textures
     * @return Path to the output directory
     */
    public String getOutputPath() {
        // Check for custom output path in config
        if (config.getConfigurationSection("output") != null &&
                config.getConfigurationSection("output").contains("custom-path")) {
            String customPath = config.getString("output.custom-path");
            if (customPath != null && !customPath.trim().isEmpty()) {
                return customPath.endsWith("/") ?
                        customPath.substring(0, customPath.length() - 1) :
                        customPath;
            }
        }

        // Default to plugin's data directory
        return plugin.getDataFolder().getAbsolutePath() + "/output";
    }

    public String getModelsPath() {
        return getOutputPath() + "/" + resourcePackId + "/assets/" + namespace + "/models/item/";
    }

    public String getTexturesPath() {
        return getOutputPath() + "/" + resourcePackId + "/assets/" + namespace + "/textures/item/";
    }

    public String getFullTexturesPath() {
        return getTexturesPath();
    }
}
