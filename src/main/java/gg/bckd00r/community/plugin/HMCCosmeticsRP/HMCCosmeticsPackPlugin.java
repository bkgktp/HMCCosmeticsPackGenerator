package gg.bckd00r.community.plugin.HMCCosmeticsRP;

import gg.bckd00r.community.plugin.HMCCosmeticsRP.config.DataManager;
import org.bukkit.plugin.java.JavaPlugin;

import gg.bckd00r.community.plugin.HMCCosmeticsRP.command.CommandManager;
import gg.bckd00r.community.plugin.HMCCosmeticsRP.config.ConfigManager;
import gg.bckd00r.community.plugin.HMCCosmeticsRP.generator.CustomModelDataGenerator;

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
     * Reload the plugin configuration
     */
    public void reloadPlugin() {
        reloadConfig();
        configManager.loadConfig();
    }
}
