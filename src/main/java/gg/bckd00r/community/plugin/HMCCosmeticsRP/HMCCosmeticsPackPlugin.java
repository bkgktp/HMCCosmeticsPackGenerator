package gg.bckd00r.community.plugin.HMCCosmeticsRP;

import gg.bckd00r.community.plugin.HMCCosmeticsRP.config.DataManager;
import gg.bckd00r.community.plugin.HMCCosmeticsRP.generator.MenuYMLGenerator;
import org.bukkit.plugin.java.JavaPlugin;

import gg.bckd00r.community.plugin.HMCCosmeticsRP.command.CommandManager;
import gg.bckd00r.community.plugin.HMCCosmeticsRP.config.ConfigManager;
import gg.bckd00r.community.plugin.HMCCosmeticsRP.generator.CustomModelDataGenerator;

import java.io.File;
import java.util.Set;

public final class HMCCosmeticsPackPlugin extends JavaPlugin {
    private static HMCCosmeticsPackPlugin instance;
    private ConfigManager configManager;
    private DataManager dataManager;
    private CommandManager commandManager;
    private CustomModelDataGenerator modelDataGenerator;

    @Override
    public void onEnable() {
        // Set instance
        instance = this;
        
        // Save default config if it doesn't exist
        saveDefaultConfig();
        
        // Initialize managers
        this.configManager = new ConfigManager(this);
        this.dataManager = new DataManager(this);
        
        // Create required directories on startup
        createRequiredDirectories();
        
        // Initialize model data generator
        this.modelDataGenerator = new CustomModelDataGenerator(this);
        
        // Initialize command manager and register command
        this.commandManager = new CommandManager(this);
        getCommand("hmcpack").setExecutor(commandManager);
        getCommand("hmcpack").setTabCompleter(commandManager);
        
        getLogger().info("HMCCosmeticsRP has been enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("HMCCosmeticsRP has been disabled!");
    }
    
    /**
     * Get the plugin instance
     * @return The plugin instance
     */
    public static HMCCosmeticsPackPlugin getInstance() {
        return instance;
    }
    
    /**
     * Get the configuration manager
     * @return The configuration manager instance
     */
    public ConfigManager getConfigManager() {
        return configManager;
    }
    
    /**
     * Get the data manager instance
     * @return The data manager instance
     */
    public DataManager getDataManager() {
        return dataManager;
    }
    
    /**
     * Get the command manager
     * @return The command manager instance
     */
    public CommandManager getCommandManager() {
        return commandManager;
    }
    
    /**
     * Get the model data generator
     * @return The model data generator instance
     */
    public CustomModelDataGenerator getModelDataGenerator() {
        return modelDataGenerator;
    }
    
    /**
     * Generate menu files for cosmetics
     * @param namespace The namespace for the cosmetics
     * @param validCosmeticTypes Set of valid cosmetic types to generate menus for
     * @param cosmeticsByType Map of cosmetic types to their item names
     * @return true if menu files were generated successfully, false otherwise
     */
    public boolean generateMenus(String namespace, Set<String> validCosmeticTypes, java.util.Map<String, java.util.List<String>> cosmeticsByType) {
        if (validCosmeticTypes == null || validCosmeticTypes.isEmpty()) {
            getLogger().warning("No cosmetic types found to generate menus for");
            return false;
        }
        
        try {
            MenuYMLGenerator menuGenerator = new MenuYMLGenerator(this, namespace, validCosmeticTypes);
            boolean success = menuGenerator.generateMenuYMLFiles(cosmeticsByType);
            if (success) {
                getLogger().info("✓ Successfully generated menu files for " + namespace);
            } else {
                getLogger().warning("Failed to generate menu files for " + namespace);
            }
            return success;
        } catch (Exception e) {
            getLogger().severe("Failed to generate menu files: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Creates required directories on plugin startup
     */
    private void createRequiredDirectories() {
        try {
            // Create input directory for BBModel files
            File inputDir = new File(getDataFolder(), "input");
            if (!inputDir.exists()) {
                if (inputDir.mkdirs()) {
                    getLogger().info("✓ Created input directory: " + inputDir.getAbsolutePath());
                } else {
                    getLogger().warning("✗ Failed to create input directory: " + inputDir.getAbsolutePath());
                }
            }
            
            // Create output directory for generated resource pack
            File outputDir = new File(getDataFolder(), "output");
            if (!outputDir.exists()) {
                if (outputDir.mkdirs()) {
                    getLogger().info("✓ Created output directory: " + outputDir.getAbsolutePath());
                } else {
                    getLogger().warning("✗ Failed to create output directory: " + outputDir.getAbsolutePath());
                }
            }
            
            // Create temp directory for temporary files
            File tempDir = new File(getDataFolder(), "temp");
            if (!tempDir.exists()) {
                if (tempDir.mkdirs()) {
                    getLogger().info("✓ Created temp directory: " + tempDir.getAbsolutePath());
                } else {
                    getLogger().warning("✗ Failed to create temp directory: " + tempDir.getAbsolutePath());
                }
            }
            
            // Create resource pack structure directories
            String packId = configManager.getResourcePackId();
            String namespace = configManager.getNamespace();
            
            // Create main resource pack directory
            File packDir = new File(outputDir, packId);
            if (!packDir.exists()) {
                packDir.mkdirs();
            }
            
            // Create assets directory structure
            File assetsDir = new File(packDir, "assets");
            File namespaceDir = new File(assetsDir, namespace);
            File itemsDir = new File(namespaceDir, "items");
            File modelsDir = new File(namespaceDir, "models");
            File modelItemDir = new File(modelsDir, "item");
            File texturesDir = new File(namespaceDir, "textures");
            File textureItemDir = new File(texturesDir, "item");
            
            // Create all directories
            File[] dirsToCreate = {assetsDir, namespaceDir, itemsDir, modelsDir, modelItemDir, texturesDir, textureItemDir};
            for (File dir : dirsToCreate) {
                if (!dir.exists()) {
                    dir.mkdirs();
                }
            }
            
            getLogger().info("✓ All required directories created successfully!");
            
        } catch (Exception e) {
            getLogger().severe("✗ Failed to create required directories: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Reload the plugin configuration
     */
    public void reloadPlugin() {
        reloadConfig();
        configManager.loadConfig();
    }
}
