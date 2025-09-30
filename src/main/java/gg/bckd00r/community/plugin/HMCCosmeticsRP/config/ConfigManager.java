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
    private final Map<String, String> defaultMaterials = new HashMap<>();
    private String copyToPath = "";
    private boolean transferGeneratedCosmeticYmlFiles = true;
    
    // Bedrock Edition support
    private boolean bedrockEnabled  = false;
    private boolean bedrockSeparateDirectory = true;

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
        config.addDefault("resource-pack.transfer-to-path", copyToPath);
        config.addDefault("settings.transfer-generated-cosmetic-yml-files", transferGeneratedCosmeticYmlFiles);
        
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
        
        // Add Bedrock Edition defaults
        config.addDefault("bedrock.enabled", bedrockEnabled);
        config.addDefault("bedrock.separate-directory", bedrockSeparateDirectory);
        
        config.options().copyDefaults(true);
        
        // Save the config to ensure new defaults are written
        plugin.saveConfig();
        
        // Load the values
        resourcePackId = config.getString("resource-pack.id", resourcePackId);
        namespace = config.getString("resource-pack.namespace", namespace).toLowerCase();
        
        // Load default materials
        if (config.isConfigurationSection("settings.default-materials")) {
            for (String key : config.getConfigurationSection("settings.default-materials").getKeys(false)) {
                defaultMaterials.put(key, config.getString("settings.default-materials." + key));
            }
        }
        
        // Load transfer-to-path and transfer settings
        copyToPath = config.getString("resource-pack.transfer-to-path", "");
        transferGeneratedCosmeticYmlFiles = config.getBoolean("settings.transfer-generated-cosmetic-yml-files", true);
        
        // Load Bedrock settings
        bedrockEnabled = config.getBoolean("bedrock.enabled", false);
        bedrockSeparateDirectory = config.getBoolean("bedrock.separate-directory", true);
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
     * Gets the transfer-to-path where the generated pack should be copied to
     * @return The transfer-to-path, or null if not set
     */
    public String getCopyToPath() {
        return copyToPath == null || copyToPath.isEmpty() ? null : copyToPath;
    }
    
    /**
     * Gets whether to transfer generated cosmetic YML files to HMCCosmetics
     * @return true to copy files to HMCCosmetics/cosmetics, false to use senddata command
     */
    public boolean shouldTransferGeneratedCosmeticYmlFiles() {
        return transferGeneratedCosmeticYmlFiles;
    }
    
    // REMOVED: useItemModelComponent() method

    /**
     * Gets the base output directory for models and textures
     * @return Path to the output directory
     */
    public String getOutputPath() {
        // Always use plugin's data directory for initial generation
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
    
    /**
     * Gets whether Bedrock Edition pack generation is enabled
     * @return true if Bedrock pack generation is enabled
     */
    public boolean isBedrockEnabled() {
        return bedrockEnabled;
    }
    
    /**
     * Gets whether Bedrock packs should be generated in a separate directory
     * @return true if Bedrock packs should be in separate directory
     */
    public boolean isBedrockSeparateDirectory() {
        return bedrockSeparateDirectory;
    }
}
