package gg.bckd00r.community.plugin.HMCCosmeticsRP.command;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import gg.bckd00r.community.plugin.HMCCosmeticsRP.HMCCosmeticsPackPlugin;
import gg.bckd00r.community.plugin.HMCCosmeticsRP.config.ConfigManager;
import gg.bckd00r.community.plugin.HMCCosmeticsRP.converter.java.BBModelToJsonConvert;
import gg.bckd00r.community.plugin.HMCCosmeticsRP.generator.CosmeticYMLGenerator;
import gg.bckd00r.community.plugin.HMCCosmeticsRP.util.FileUtils;
import gg.bckd00r.community.plugin.HMCCosmeticsRP.util.Utils;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CommandManager implements CommandExecutor, TabCompleter {
    private final HMCCosmeticsPackPlugin plugin;
    private final ConfigManager configManager;
    
    // Supported cosmetic types
    private static final List<String> SUPPORTED_COSMETIC_TYPES = Arrays.asList(
        "HELMET", "BACKPACK", "CHESTPLATE", "LEGGINGS", "BOOTS", "OFFHAND", "BALLOON"
    );

    public CommandManager(HMCCosmeticsPackPlugin plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
    }
    
    /**
     * Checks if a model filename contains a valid cosmetic type
     * @param fileName The filename without .bbmodel extension
     * @return true if the filename contains a supported cosmetic type
     */
    private boolean isValidCosmeticModel(String fileName) {
        // Remove _firstperson suffix for validation
        String baseName = fileName.replace("_firstperson", "");
        String[] nameParts = baseName.split("_");
        
        for (String part : nameParts) {
            if (SUPPORTED_COSMETIC_TYPES.contains(part.toUpperCase())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("hmcpack")) {
            return false;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();
        
        switch (subCommand) {
            case "generate":
                if (!sender.hasPermission("hmcpack.generate")) {
                    sendNoPermission(sender);
                    return true;
                }
                handleGenerate(sender);
                break;
                
            case "reload":
                if (!sender.hasPermission("hmcpack.reload")) {
                    sendNoPermission(sender);
                    return true;
                }
                handleReload(sender);
                break;
                
            case "data":
                if (!sender.hasPermission("hmcpack.data")) {
                    sendNoPermission(sender);
                    return true;
                }
                handleDataCommand(sender, args);
                break;
                
            case "senddata":
                if (!sender.hasPermission("hmcpack.generate")) {
                    sendNoPermission(sender);
                    return true;
                }
                handleSendData(sender);
                break;
                
            default:
                sendHelp(sender);
                break;
        }
        
        return true;
    }
    
    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== HMCCosmeticsRP Help ===");
        sender.sendMessage(ChatColor.YELLOW + "/hmcpack generate " + ChatColor.WHITE + "- Generate resource pack and YML configs from .bbmodel files");
        sender.sendMessage(ChatColor.YELLOW + "/hmcpack reload " + ChatColor.WHITE + "- Reload the plugin configuration");
        sender.sendMessage(ChatColor.YELLOW + "/hmcpack senddata " + ChatColor.WHITE + "- Send YML files from temp to cosmetics directory");
        sender.sendMessage(ChatColor.YELLOW + "/hmcpack data list " + ChatColor.WHITE + "- List all models in data.yml");
        sender.sendMessage(ChatColor.YELLOW + "/hmcpack data show <model> " + ChatColor.WHITE + "- Show model display settings");
        sender.sendMessage(ChatColor.YELLOW + "/hmcpack data set <model> <display> <property> <x> <y> <z> " + ChatColor.WHITE + "- Set model display values");
        sender.sendMessage(ChatColor.YELLOW + "/hmcpack data reset <model> " + ChatColor.WHITE + "- Reset model to default values");
    }
    
    private void sendNoPermission(CommandSender sender) {
        sender.sendMessage(ChatColor.RED + "You don't have permission to use this command!");
    }
    
    private void handleGenerate(CommandSender sender) {
        // Get input directory
        File inputDir = new File(plugin.getDataFolder(), "input");
        if (!inputDir.exists() || !inputDir.isDirectory()) {
            inputDir.mkdirs();
            sender.sendMessage(ChatColor.YELLOW + "Input directory not found, created one at: " + inputDir.getAbsolutePath());
            sender.sendMessage(ChatColor.YELLOW + "Please place your .bbmodel files in the input directory.");
            return;
        }
        
        // Process all .bbmodel files
        File[] bbmodelFiles = inputDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".bbmodel"));
        if (bbmodelFiles == null || bbmodelFiles.length == 0) {
            sender.sendMessage(ChatColor.YELLOW + "No .bbmodel files found in the input directory.");
            return;
        }
        
        // Separate main models from firstperson models
        Map<String, File> mainModels = new HashMap<>();
        Map<String, File> firstpersonModels = new HashMap<>();
        int skippedCount = 0;
        
        for (File file : bbmodelFiles) {
            String fileName = file.getName().replace(".bbmodel", "");
            
            // Validate if this is a supported cosmetic model
            if (!isValidCosmeticModel(fileName)) {
                sender.sendMessage(ChatColor.RED + "Skipped " + file.getName() + " - no valid cosmetic type found (supported: " + 
                    String.join(", ", SUPPORTED_COSMETIC_TYPES.stream().map(String::toLowerCase).collect(Collectors.toList())) + ")");
                skippedCount++;
                continue;
            }
            
            if (fileName.endsWith("_firstperson")) {
                String baseName = fileName.replace("_firstperson", "");
                firstpersonModels.put(baseName, file);
                Utils.debugMessage(sender, ChatColor.AQUA + "Found firstperson model: " + fileName + " for " + baseName);
            } else {
                mainModels.put(fileName, file);
            }
        }
        
        if (skippedCount > 0) {
            sender.sendMessage(ChatColor.YELLOW + "Skipped " + skippedCount + " invalid model file(s).");
        }
        
        if (mainModels.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "No valid cosmetic models found to process!");
            return;
        }
        
        sender.sendMessage(ChatColor.GOLD + "=== Starting Clean Generation Process ===");
        sender.sendMessage(ChatColor.YELLOW + "Step 1: Cleaning old files...");
        
        // Reset custom model data generator to clear any previous state
        if (configManager.useCustomModelData()) {
            plugin.getModelDataGenerator().reset();
        }
        
        String packId = configManager.getResourcePackId();
        File localOutputDir = new File(plugin.getDataFolder(), "output/" + packId);
        
        // Clean up old files before generation
        cleanupOldFiles(localOutputDir, sender);
        
        sender.sendMessage(ChatColor.YELLOW + "Step 2: Generating files locally...");
        
        // Create LOCAL output directories first
        String localModelsPath = localOutputDir.getAbsolutePath() + "/assets/" + configManager.getNamespace() + "/models/item/";
        String localTexturesPath = localOutputDir.getAbsolutePath() + "/assets/" + configManager.getNamespace() + "/textures/item/";
        String localItemsPath = localOutputDir.getAbsolutePath() + "/assets/" + configManager.getNamespace() + "/items/";
        
        try {
            Files.createDirectories(Paths.get(localModelsPath));
            Files.createDirectories(Paths.get(localTexturesPath));
            Files.createDirectories(Paths.get(localItemsPath));
            sender.sendMessage(ChatColor.GREEN + "✓ Created output directories");
        } catch (IOException e) {
            sender.sendMessage(ChatColor.RED + "Failed to create output directories: " + e.getMessage());
            return;
        }
        
        // Initialize YML generator
        CosmeticYMLGenerator ymlGenerator = new CosmeticYMLGenerator(plugin, configManager.getNamespace());
        
        // Counters for different types
        int helmetCount = 0;
        int backpackCount = 0;
        int chestplateCount = 0;
        int leggingsCount = 0;
        int bootsCount = 0;
        int offhandCount = 0;
        int balloonCount = 0;
        int otherCount = 0;
        
        // Map to track processed files by type
        Map<String, Integer> processedCounts = new HashMap<>();
        
        int successCount = 0;
        
        // Process all models (main models will be processed with their firstperson variants)
        for (Map.Entry<String, File> entry : mainModels.entrySet()) {
            String modelName = entry.getKey();
            File bbmodelFile = entry.getValue();
            
            try {
                String localOutputJsonPath = localModelsPath + modelName + ".json";
                
                // Convert BBModel to JSON (to LOCAL directory first)
                BBModelToJsonConvert.convert(
                    bbmodelFile.getAbsolutePath(),
                    localOutputJsonPath,
                    localTexturesPath,
                    configManager.getNamespace()
                );
                
                // Check if this model has a firstperson version
                File firstpersonFile = firstpersonModels.get(modelName);
                if (firstpersonFile != null) {
                    Utils.debugMessage(sender, ChatColor.GREEN + "Processing " + modelName + " with firstperson variant");
                    
                    // Convert firstperson model too
                    String firstpersonJsonPath = localModelsPath + modelName + "_firstperson.json";
                    BBModelToJsonConvert.convert(
                        firstpersonFile.getAbsolutePath(),
                        firstpersonJsonPath,
                        localTexturesPath,
                        configManager.getNamespace()
                    );
                }
                
                try {
                    // Generate YML data and get the type using the converted JSON file
                    File jsonFile = new File(localOutputJsonPath);
                    String type = ymlGenerator.generateYMLFiles(jsonFile, firstpersonFile != null);
                    
                    // Check if type is null (file was skipped)
                    if (type == null) {
                        plugin.getLogger().warning("Skipped processing " + modelName + " - no valid cosmetic type found");
                        continue;
                    }
                    
                    // Process firstperson model separately if it exists
                    if (firstpersonFile != null) {
                        try {
                            String firstpersonJsonPath = localModelsPath + modelName + "_firstperson.json";
                            File firstpersonJsonFile = new File(firstpersonJsonPath);
                            if (firstpersonJsonFile.exists()) {
                                // Process firstperson model like any other model
                                plugin.getLogger().info("Processing firstperson model: " + modelName + "_firstperson");
                                ymlGenerator.loadAndProcessModel(firstpersonJsonFile, modelName + "_firstperson");
                                plugin.getLogger().info("Successfully processed firstperson model: " + modelName + "_firstperson");
                            } else {
                                plugin.getLogger().warning("Firstperson JSON file not found: " + firstpersonJsonPath);
                            }
                        } catch (Exception e) {
                            plugin.getLogger().warning("Failed to process firstperson model for " + modelName + ": " + e.getMessage());
                            e.printStackTrace();
                        }
                    }
                    
                    // Count the type
                    processedCounts.merge(type.toLowerCase(), 1, Integer::sum);
                    
                    switch (type.toUpperCase()) {
                        case "HELMET":
                            helmetCount++;
                            break;
                        case "BACKPACK":
                            backpackCount++;
                            break;
                        case "CHESTPLATE":
                            chestplateCount++;
                            break;
                        case "LEGGINGS":
                            leggingsCount++;
                            break;
                        case "BOOTS":
                            bootsCount++;
                            break;
                        case "OFFHAND":
                            offhandCount++;
                            break;
                        case "BALLOON":
                            balloonCount++;
                            break;
                        default:
                            otherCount++;
                            break;
                    }
                } catch (Exception e) {
                    sender.sendMessage(ChatColor.RED + "Error processing " + bbmodelFile.getName() + ": " + e.getMessage());
                    plugin.getLogger().severe("Error processing " + bbmodelFile.getName() + ": " + e.getMessage());
                    continue;
                }
                
                successCount++;
                
            } catch (Exception e) {
                sender.sendMessage(ChatColor.RED + "Error processing " + bbmodelFile.getName() + ": " + e.getMessage());
            }
        }
        
        // Save all cosmetics to their respective type files
        ymlGenerator.saveAllCosmetics();
        
        // Generate custom model data files if enabled
        if (configManager.useCustomModelData()) {
            Utils.debugMessage(sender, ChatColor.YELLOW + "Generating legacy custom model data files...");
            plugin.getModelDataGenerator().generateModelJsons();
            Utils.debugMessage(sender, ChatColor.GREEN + "✓ Legacy custom model data files generated");
        }

        Utils.debugMessage(sender, ChatColor.GREEN + "✓ Step 2 Complete: All files generated locally");
        Utils.debugMessage(sender, ChatColor.YELLOW + "Step 3: Copying changed files to target output...");
        
        // Now copy only changed files from local output to final output
        int copiedFiles = copyChangedFilesToOutput(localOutputDir, sender);
        
        if (copiedFiles > 0) {
            Utils.debugMessage(sender, ChatColor.GREEN + "✓ Step 3 Complete: Copied " + copiedFiles + " changed files to target output");
        } else {
            Utils.debugMessage(sender, ChatColor.YELLOW + "✓ Step 3 Complete: No files needed copying (all up to date)");
        }
        
        // Send summary
        sender.sendMessage(ChatColor.GOLD + "=== Generation Complete ===");
        sender.sendMessage(ChatColor.GREEN + "Successfully processed " + ChatColor.YELLOW + successCount + 
                         ChatColor.GREEN + " models:");
        
        if (helmetCount > 0) sender.sendMessage(ChatColor.YELLOW + "- Helmets: " + ChatColor.WHITE + helmetCount);
        if (backpackCount > 0) sender.sendMessage(ChatColor.YELLOW + "- Backpacks: " + ChatColor.WHITE + backpackCount);
        if (chestplateCount > 0) sender.sendMessage(ChatColor.YELLOW + "- Chestplates: " + ChatColor.WHITE + chestplateCount);
        if (leggingsCount > 0) sender.sendMessage(ChatColor.YELLOW + "- Leggings: " + ChatColor.WHITE + leggingsCount);
        if (bootsCount > 0) sender.sendMessage(ChatColor.YELLOW + "- Boots: " + ChatColor.WHITE + bootsCount);
        if (balloonCount > 0) sender.sendMessage(ChatColor.YELLOW + "- Balloons: " + ChatColor.WHITE + balloonCount);
        if (offhandCount > 0) sender.sendMessage(ChatColor.YELLOW + "- Offhand Items: " + ChatColor.WHITE + offhandCount);
        if (otherCount > 0) sender.sendMessage(ChatColor.YELLOW + "- Other: " + ChatColor.WHITE + otherCount);
        
        // Show saved files
        sender.sendMessage(ChatColor.GOLD + "Saved to files:");
        for (Map.Entry<String, Integer> entry : processedCounts.entrySet()) {
            sender.sendMessage(ChatColor.YELLOW + "- " + entry.getKey() + ".yml: " + 
                             ChatColor.WHITE + entry.getValue() + " items");
        }
        
        // Copy files to hooks if needed
        // copyToHooks(sender);
        
        // Copy the generated pack to the transfer-to-path if specified
        // MOVED TO END: This ensures all files including firstperson namespace items are copied
        // Use the correct transfer method that uses 'resource-pack.transfer-to-path' config
        gg.bckd00r.community.plugin.HMCCosmeticsRP.pack.PackUtils.copyResourcePackToExternalPath();
        // Note: copyResourcePackToExternalPath() handles its own success/failure messaging
        
        sender.sendMessage(ChatColor.GREEN + "All cosmetics have been processed and saved successfully!");
        sender.sendMessage(ChatColor.GREEN + "Please reinstall the Resource Pack.");
    }
    
    private void handleReload(CommandSender sender) {
        try {
            configManager.loadConfig();
            plugin.getDataManager().loadData(); // Reload data.yml as well
            sender.sendMessage(ChatColor.GREEN + "Configuration and data.yml reloaded successfully!");
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "Failed to reload configuration: " + e.getMessage());
        }
    }
    
    private void handleDataCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendDataHelp(sender);
            return;
        }
        
        String subCommand = args[1].toLowerCase();
        
        switch (subCommand) {
            case "list":
                handleDataList(sender);
                break;
                
            case "show":
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "Usage: /hmcpack data show <model>");
                    return;
                }
                handleDataShow(sender, args[2]);
                break;
                
            case "set":
                if (args.length < 8) {
                    sender.sendMessage(ChatColor.RED + "Usage: /hmcpack data set <model> <display> <property> <x> <y> <z>");
                    sender.sendMessage(ChatColor.YELLOW + "Example: /hmcpack data set samurai_backpack head translation 0 -10 0");
                    return;
                }
                handleDataSet(sender, args[2], args[3], args[4], args[5], args[6], args[7]);
                break;
                
            case "reset":
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "Usage: /hmcpack data reset <model>");
                    return;
                }
                handleDataReset(sender, args[2]);
                break;
                
            default:
                sendDataHelp(sender);
                break;
        }
    }
    
    private void sendDataHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== Data Management Commands ===");
        sender.sendMessage(ChatColor.YELLOW + "/hmcpack data list " + ChatColor.WHITE + "- List all models");
        sender.sendMessage(ChatColor.YELLOW + "/hmcpack data show <model> " + ChatColor.WHITE + "- Show model settings");
        sender.sendMessage(ChatColor.YELLOW + "/hmcpack data set <model> <display> <property> <x> <y> <z> " + ChatColor.WHITE + "- Set values");
        sender.sendMessage(ChatColor.YELLOW + "/hmcpack data reset <model> " + ChatColor.WHITE + "- Reset to defaults");
        sender.sendMessage(ChatColor.GRAY + "Display types: head, head, firstperson_righthand, thirdperson_righthand, etc.");
        sender.sendMessage(ChatColor.GRAY + "Properties: translation, rotation, scale");
    }
    
    private void handleDataList(CommandSender sender) {
        try {
            Map<String, gg.bckd00r.community.plugin.HMCCosmeticsRP.config.DataManager.ModelData> models = plugin.getDataManager().getAllModels();
            
            if (models.isEmpty()) {
                sender.sendMessage(ChatColor.YELLOW + "No models found in data.yml. Generate some models first!");
                return;
            }
            
            sender.sendMessage(ChatColor.GOLD + "=== Models in data.yml (" + models.size() + ") ===");
            
            // Separate main models and firstperson models
            java.util.List<String> mainModels = new java.util.ArrayList<>();
            java.util.List<String> firstpersonModels = new java.util.ArrayList<>();
            
            for (String modelName : models.keySet()) {
                if (modelName.endsWith("_firstperson")) {
                    firstpersonModels.add(modelName);
                } else {
                    mainModels.add(modelName);
                }
            }
            
            // Show main models
            if (!mainModels.isEmpty()) {
                sender.sendMessage(ChatColor.AQUA + "Main Models:");
                for (String modelName : mainModels) {
                    gg.bckd00r.community.plugin.HMCCosmeticsRP.config.DataManager.ModelData modelData = models.get(modelName);
                    int displayCount = modelData.getDisplayData().size();
                    sender.sendMessage(ChatColor.YELLOW + "• " + ChatColor.WHITE + modelName + 
                        ChatColor.GRAY + " (" + displayCount + " display settings)");
                }
            }
            
            // Show firstperson models
            if (!firstpersonModels.isEmpty()) {
                sender.sendMessage(ChatColor.LIGHT_PURPLE + "Firstperson Models:");
                for (String modelName : firstpersonModels) {
                    gg.bckd00r.community.plugin.HMCCosmeticsRP.config.DataManager.ModelData modelData = models.get(modelName);
                    int displayCount = modelData.getDisplayData().size();
                    String baseName = modelName.replace("_firstperson", "");
                    sender.sendMessage(ChatColor.YELLOW + "• " + ChatColor.WHITE + modelName + 
                        ChatColor.GRAY + " (" + displayCount + " display settings) [FP: " + baseName + "]");
                }
            }
            
            sender.sendMessage(ChatColor.GRAY + "Use '/hmcpack data show <model>' for details");
            
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "Error listing models: " + e.getMessage());
        }
    }
    
    private void handleDataShow(CommandSender sender, String modelName) {
        try {
            gg.bckd00r.community.plugin.HMCCosmeticsRP.config.DataManager.ModelData modelData = plugin.getDataManager().getModelData(modelName);
            
            if (modelData == null || modelData.getDisplayData().isEmpty()) {
                sender.sendMessage(ChatColor.RED + "Model '" + modelName + "' not found in data.yml");
                return;
            }
            
            // Check if this is a firstperson model
            boolean isFirstperson = modelName.endsWith("_firstperson");
            String displayTitle = isFirstperson ? 
                "=== Firstperson Model: " + modelName + " ===" : 
                "=== Model: " + modelName + " ===";
            
            sender.sendMessage(ChatColor.GOLD + displayTitle);
            
            if (isFirstperson) {
                String baseName = modelName.replace("_firstperson", "");
                sender.sendMessage(ChatColor.GRAY + "Base model: " + ChatColor.WHITE + baseName);
                sender.sendMessage(ChatColor.GRAY + "Type: " + ChatColor.LIGHT_PURPLE + "Firstperson View Model");
            }
            
            for (Map.Entry<String, Map<String, Object>> displayEntry : modelData.getDisplayData().entrySet()) {
                String displayType = displayEntry.getKey();
                sender.sendMessage(ChatColor.YELLOW + "Display Type: " + ChatColor.WHITE + displayType);
                
                for (Map.Entry<String, Object> propertyEntry : displayEntry.getValue().entrySet()) {
                    String property = propertyEntry.getKey();
                    Object value = propertyEntry.getValue();
                    
                    String valueStr = "";
                    if (value instanceof List) {
                        List<?> list = (List<?>) value;
                        valueStr = String.format("[%.2f, %.2f, %.2f]", 
                            ((Number) list.get(0)).doubleValue(),
                            ((Number) list.get(1)).doubleValue(),
                            ((Number) list.get(2)).doubleValue());
                    } else {
                        valueStr = value.toString();
                    }
                    
                    sender.sendMessage(ChatColor.GRAY + "  " + property + ": " + ChatColor.WHITE + valueStr);
                }
            }
            
            sender.sendMessage(ChatColor.GRAY + "Use '/hmcpack data set " + modelName + " <display> <property> <x> <y> <z>' to modify");
            
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "Error showing model: " + e.getMessage());
        }
    }
    
    private void handleDataSet(CommandSender sender, String modelName, String displayType, String property, 
                              String xStr, String yStr, String zStr) {
        try {
            // Validate property
            if (!property.equals("translation") && !property.equals("rotation") && !property.equals("scale")) {
                sender.sendMessage(ChatColor.RED + "Invalid property! Use: translation, rotation, or scale");
                return;
            }
            
            // Parse coordinates
            double x, y, z;
            try {
                x = Double.parseDouble(xStr);
                y = Double.parseDouble(yStr);
                z = Double.parseDouble(zStr);
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "Invalid coordinates! Use numbers (e.g., 0 -10 0)");
                return;
            }
            
            // Get or create model data
            gg.bckd00r.community.plugin.HMCCosmeticsRP.config.DataManager.ModelData modelData = plugin.getDataManager().getModelData(modelName);
            if (modelData == null) {
                sender.sendMessage(ChatColor.RED + "Model '" + modelName + "' not found. Generate it first!");
                return;
            }
            
            // Show current value before changing
            Map<String, Object> currentDisplay = modelData.getDisplayData(displayType);
            if (currentDisplay.containsKey(property)) {
                Object currentValue = currentDisplay.get(property);
                if (currentValue instanceof List) {
                    List<?> list = (List<?>) currentValue;
                    sender.sendMessage(ChatColor.GRAY + "Current " + property + ": " + 
                        String.format("[%.2f, %.2f, %.2f]", 
                            ((Number) list.get(0)).doubleValue(),
                            ((Number) list.get(1)).doubleValue(),
                            ((Number) list.get(2)).doubleValue()));
                }
            }
            
            // Update the value
            Map<String, Object> displayData = new HashMap<>(currentDisplay);
            displayData.put(property, Arrays.asList(x, y, z));
            modelData.addDisplayData(displayType, displayData);
            
            // Save to data.yml
            plugin.getDataManager().updateModelData(modelName, modelData);
            
            // Check if this is a firstperson model for special messaging
            boolean isFirstperson = modelName.endsWith("_firstperson");
            String modelType = isFirstperson ? "firstperson model" : "model";
            
            sender.sendMessage(ChatColor.GREEN + "✓ Updated " + modelType + " " + modelName + "." + displayType + "." + property + 
                " to [" + x + ", " + y + ", " + z + "]");
            
            if (isFirstperson) {
                String baseName = modelName.replace("_firstperson", "");
                sender.sendMessage(ChatColor.LIGHT_PURPLE + "Note: This is a firstperson view model for " + baseName);
            }
            
            sender.sendMessage(ChatColor.YELLOW + "Use '/hmcpack generate' to apply changes to JSON files");
            
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "Error setting value: " + e.getMessage());
        }
    }
    
    private void handleDataReset(CommandSender sender, String modelName) {
        try {
            gg.bckd00r.community.plugin.HMCCosmeticsRP.config.DataManager.ModelData modelData = plugin.getDataManager().getModelData(modelName);
            
            if (modelData == null || modelData.getDisplayData().isEmpty()) {
                sender.sendMessage(ChatColor.RED + "Model '" + modelName + "' not found in data.yml");
                return;
            }
            
            // Try to find and read the original JSON file to get original display values
            gg.bckd00r.community.plugin.HMCCosmeticsRP.config.DataManager.ModelData originalModelData = 
                getOriginalModelDisplayData(modelName);
            
            if (originalModelData != null && !originalModelData.getDisplayData().isEmpty()) {
                // Use original display data from .bbmodel/.json file
                plugin.getDataManager().updateModelData(modelName, originalModelData);
                sender.sendMessage(ChatColor.GREEN + "✓ Reset " + modelName + " to original .bbmodel display values");
                sender.sendMessage(ChatColor.GRAY + "Restored " + originalModelData.getDisplayData().size() + " display configurations");
            } else {
                // Fallback to default values if original file not found
                gg.bckd00r.community.plugin.HMCCosmeticsRP.config.DataManager.ModelData newModelData = 
                    new gg.bckd00r.community.plugin.HMCCosmeticsRP.config.DataManager.ModelData();
                
                Map<String, Object> headDisplay = new HashMap<>();
                headDisplay.put("rotation", Arrays.asList(0.0, 0.0, 0.0));
                headDisplay.put("translation", Arrays.asList(0.0, 0.0, 0.0));
                headDisplay.put("scale", Arrays.asList(1.0, 1.0, 1.0));
                newModelData.addDisplayData("head", headDisplay);
                
                plugin.getDataManager().updateModelData(modelName, newModelData);
                sender.sendMessage(ChatColor.YELLOW + "⚠ Original .bbmodel file not found for " + modelName);
                sender.sendMessage(ChatColor.GREEN + "✓ Reset " + modelName + " to fallback default values (0,0,0 and 1,1,1 scale)");
            }
            
            sender.sendMessage(ChatColor.YELLOW + "Use '/hmcpack generate' to apply changes to JSON files");
            
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "Error resetting model: " + e.getMessage());
        }
    }
    
    /**
     * Reads the original display data from the converted JSON file of a .bbmodel
     * @param modelName The name of the model
     * @return ModelData with original display settings, or null if not found
     */
    private gg.bckd00r.community.plugin.HMCCosmeticsRP.config.DataManager.ModelData getOriginalModelDisplayData(String modelName) {
        try {
            // Look for the JSON file in the models directory
            File modelsDir = new File(plugin.getDataFolder(), "models");
            if (!modelsDir.exists()) {
                plugin.getLogger().warning("Models directory not found: " + modelsDir.getAbsolutePath());
                return null;
            }
            
            // Try different possible file names
            String[] possibleNames = {
                modelName + ".json",
                modelName.toLowerCase() + ".json",
                modelName.replace("_", "-") + ".json",
                modelName.replace("-", "_") + ".json"
            };
            
            File jsonFile = null;
            for (String fileName : possibleNames) {
                File testFile = new File(modelsDir, fileName);
                if (testFile.exists()) {
                    jsonFile = testFile;
                    plugin.getLogger().info("Found original JSON file: " + testFile.getAbsolutePath());
                    break;
                }
            }
            
            if (jsonFile == null) {
                plugin.getLogger().warning("Original JSON file not found for model: " + modelName);
                return null;
            }
            
            // Read and parse the JSON file
            String jsonContent = new String(Files.readAllBytes(jsonFile.toPath()));
            JsonObject modelJson = JsonParser.parseString(jsonContent).getAsJsonObject();
            
            // Extract display settings using DataManager's method
            gg.bckd00r.community.plugin.HMCCosmeticsRP.config.DataManager.ModelData originalData = 
                plugin.getDataManager().ensureModelExists(modelName, modelJson);
            
            plugin.getLogger().info("Successfully extracted original display data for: " + modelName);
            plugin.getLogger().info("Original display data: " + originalData.getDisplayData().toString());
            
            return originalData;
            
        } catch (Exception e) {
            plugin.getLogger().severe("Error reading original model data for " + modelName + ": " + e.getMessage());
            return null;
        }
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase("hmcpack")) {
            return null;
        }
        
        // Main command completions
        if (args.length == 1) {
            return Arrays.asList("generate", "reload", "senddata", "data")
                .stream()
                .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }
        
        // Data command completions
        if (args.length >= 2 && args[0].equalsIgnoreCase("data")) {
            return getDataTabCompletions(sender, args);
        }
        
        return null;
    }
    
    private List<String> getDataTabCompletions(CommandSender sender, String[] args) {
        // /hmcpack data <subcommand>
        if (args.length == 2) {
            return Arrays.asList("list", "show", "set", "reset")
                .stream()
                .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                .collect(Collectors.toList());
        }
        
        // /hmcpack data show <model>
        // /hmcpack data reset <model>
        if (args.length == 3 && (args[1].equalsIgnoreCase("show") || args[1].equalsIgnoreCase("reset"))) {
            return getModelNames(args[2]);
        }
        
        // /hmcpack data set <model> <display> <property> <x> <y> <z>
        if (args[1].equalsIgnoreCase("set")) {
            if (args.length == 3) {
                // Model name completion
                return getModelNames(args[2]);
            } else if (args.length == 4) {
                // Display type completion
                return Arrays.asList("head", "firstperson_righthand", "thirdperson_righthand", 
                                   "firstperson_lefthand", "thirdperson_lefthand", "gui", "ground", "fixed")
                    .stream()
                    .filter(s -> s.toLowerCase().startsWith(args[3].toLowerCase()))
                    .collect(Collectors.toList());
            } else if (args.length == 5) {
                // Property completion
                return Arrays.asList("translation", "rotation", "scale")
                    .stream()
                    .filter(s -> s.toLowerCase().startsWith(args[4].toLowerCase()))
                    .collect(Collectors.toList());
            } else if (args.length >= 6 && args.length <= 8) {
                // Coordinate completions - show current values if available
                return getCurrentCoordinateCompletions(args[2], args[3], args[4], args.length - 6);
            }
        }
        
        return null;
    }
    
    private List<String> getModelNames(String partial) {
        try {
            Map<String, gg.bckd00r.community.plugin.HMCCosmeticsRP.config.DataManager.ModelData> models = 
                plugin.getDataManager().getAllModels();
            
            return models.keySet()
                .stream()
                .filter(name -> name.toLowerCase().startsWith(partial.toLowerCase()))
                .collect(Collectors.toList());
        } catch (Exception e) {
            return Arrays.asList();
        }
    }
    
    private List<String> getCurrentCoordinateCompletions(String modelName, String displayType, String property, int coordinateIndex) {
        try {
            gg.bckd00r.community.plugin.HMCCosmeticsRP.config.DataManager.ModelData modelData = 
                plugin.getDataManager().getModelData(modelName);
            
            if (modelData != null && !modelData.getDisplayData().isEmpty()) {
                Map<String, Object> displayData = modelData.getDisplayData(displayType);
                if (displayData.containsKey(property)) {
                    Object value = displayData.get(property);
                    if (value instanceof List) {
                        List<?> list = (List<?>) value;
                        if (coordinateIndex < list.size()) {
                            String currentValue = String.valueOf(((Number) list.get(coordinateIndex)).doubleValue());
                            return Arrays.asList(currentValue);
                        }
                    }
                }
            }
            
            // Default suggestions based on coordinate index and property
            if (property.equals("scale")) {
                return Arrays.asList("1.0");
            } else if (property.equals("translation")) {
                return coordinateIndex == 1 ? Arrays.asList("0", "-10", "10") : Arrays.asList("0");
            } else if (property.equals("rotation")) {
                return Arrays.asList("0", "90", "-90", "180");
            }
            
        } catch (Exception e) {
            // Ignore errors and provide default suggestions
        }
        
        return Arrays.asList("0");
    }
    
    /**
     * Copies only changed files from local output to target output directory
     * @param localOutputDir The local output directory containing generated files
     * @param sender Command sender for feedback
     * @return Number of files copied
     */
    private int copyChangedFilesToOutput(File localOutputDir, CommandSender sender) {
        int copiedFiles = 0;
        
        try {
            // Get the final output directory paths
            String finalModelsPath = configManager.getModelsPath();
            String finalTexturesPath = configManager.getFullTexturesPath();
            
            // Create final output directories if they don't exist
            Files.createDirectories(Paths.get(finalModelsPath));
            Files.createDirectories(Paths.get(finalTexturesPath));
            
            // Copy models directory
            File localModelsDir = new File(localOutputDir, "assets/" + configManager.getNamespace() + "/models");
            if (localModelsDir.exists()) {
                copiedFiles += copyChangedFilesRecursively(localModelsDir, new File(finalModelsPath).getParentFile(), sender);
            }
            
            // Copy textures directory
            File localTexturesDir = new File(localOutputDir, "assets/" + configManager.getNamespace() + "/textures");
            if (localTexturesDir.exists()) {
                copiedFiles += copyChangedFilesRecursively(localTexturesDir, new File(finalTexturesPath).getParentFile(), sender);
            }
            
            // Copy other resource pack files (pack.mcmeta, etc.)
            File[] rootFiles = localOutputDir.listFiles();
            if (rootFiles != null) {
                for (File file : rootFiles) {
                    if (file.isFile()) {
                        File targetFile = new File(configManager.getOutputPath() + "/" + configManager.getResourcePackId(), file.getName());
                        if (isFileChanged(file, targetFile)) {
                            Files.copy(file.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                            sender.sendMessage(ChatColor.GRAY + "  → Copied " + file.getName());
                            copiedFiles++;
                        }
                    }
                }
            }
            
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "Error copying files to target output: " + e.getMessage());
            plugin.getLogger().severe("Error copying files to target output: " + e.getMessage());
        }
        
        return copiedFiles;
    }
    
    /**
     * Recursively copies changed files from source to target directory
     */
    private int copyChangedFilesRecursively(File sourceDir, File targetDir, CommandSender sender) throws IOException {
        int copiedFiles = 0;
        
        if (!sourceDir.exists() || !sourceDir.isDirectory()) {
            return 0;
        }
        
        Files.createDirectories(targetDir.toPath());
        
        File[] files = sourceDir.listFiles();
        if (files != null) {
            for (File file : files) {
                File targetFile = new File(targetDir, file.getName());
                
                if (file.isDirectory()) {
                    // Recursively copy subdirectories
                    copiedFiles += copyChangedFilesRecursively(file, targetFile, sender);
                } else {
                    // Check if file has changed and copy if needed
                    if (isFileChanged(file, targetFile)) {
                        Files.copy(file.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        String relativePath = sourceDir.getName() + "/" + file.getName();
                        sender.sendMessage(ChatColor.GRAY + "  → Copied " + relativePath);
                        copiedFiles++;
                    }
                }
            }
        }
        
        return copiedFiles;
    }
    
    /**
     * Checks if a source file is different from target file (or target doesn't exist)
     */
    private boolean isFileChanged(File sourceFile, File targetFile) {
        try {
            // If target doesn't exist, file is "changed"
            if (!targetFile.exists()) {
                return true;
            }
            
            // Compare file sizes first (quick check)
            if (sourceFile.length() != targetFile.length()) {
                return true;
            }
            
            // Compare last modified times
            if (sourceFile.lastModified() > targetFile.lastModified()) {
                return true;
            }
            
            // For small files, compare content to be sure
            if (sourceFile.length() < 1024 * 1024) { // Files smaller than 1MB
                byte[] sourceBytes = Files.readAllBytes(sourceFile.toPath());
                byte[] targetBytes = Files.readAllBytes(targetFile.toPath());
                return !Arrays.equals(sourceBytes, targetBytes);
            }
            
            // For larger files, trust size and timestamp
            return false;
            
        } catch (Exception e) {
            // If we can't compare, assume it's changed
            plugin.getLogger().warning("Could not compare files " + sourceFile.getName() + " and " + targetFile.getName() + ": " + e.getMessage());
            return true;
        }
    }

    
    private void handleSendData(CommandSender sender) {
        File tempDir = new File(plugin.getDataFolder(), "temp/" + configManager.getNamespace());
        File cosmeticsDir = new File("HMCCosmetics/cosmetics");
        
        if (!tempDir.exists() || !tempDir.isDirectory()) {
            sender.sendMessage(ChatColor.RED + "No temporary files found to send. Please run the convert command first.");
            return;
        }
        
        // Ensure target directory exists
        if (!cosmeticsDir.exists()) {
            cosmeticsDir.mkdirs();
        }
        
        // Copy all YML files from temp to cosmetics directory
        try {
            List<Path> files = Files.walk(tempDir.toPath())
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".yml"))
                .collect(Collectors.toList());
                
            if (files.isEmpty()) {
                sender.sendMessage(ChatColor.YELLOW + "No YML files found to send.");
                return;
            }
            
            int copied = 0;
            for (Path source : files) {
                Path target = cosmeticsDir.toPath().resolve(source.getFileName());
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                copied++;
            }
            
            // Also copy to output directory if needed
            File outputDir = new File(configManager.getOutputPath());
            if (outputDir.exists() || outputDir.mkdirs()) {
                File outputCosmeticsDir = new File(outputDir, "cosmetics");
                if (outputCosmeticsDir.exists() || outputCosmeticsDir.mkdirs()) {
                    for (Path source : files) {
                        Path target = outputCosmeticsDir.toPath().resolve(source.getFileName());
                        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
            
            sender.sendMessage(ChatColor.GREEN + "Successfully sent " + copied + " files to HMCCosmetics!");
            
        } catch (IOException e) {
            sender.sendMessage(ChatColor.RED + "Error sending files: " + e.getMessage());
        }
    }
    
    /**
     * Cleans up old files before generation to ensure fresh start
     * @param localOutputDir The local output directory to clean
     * @param sender Command sender for feedback
     */
    private void cleanupOldFiles(File localOutputDir, CommandSender sender) {
        try {
            // Delete old LOCAL output directory (plugin's own directory)
            if (localOutputDir.exists()) {
                sender.sendMessage(ChatColor.YELLOW + "  → Deleting old local output directory...");
                deleteDirectoryRecursively(localOutputDir.toPath());
                sender.sendMessage(ChatColor.GREEN + "  ✓ Deleted old local output directory");
            }
            
            // Delete old cosmetic YML files
            File cosmeticsDir = new File(plugin.getDataFolder(), "cosmetics");
            if (cosmeticsDir.exists()) {
                sender.sendMessage(ChatColor.YELLOW + "  → Deleting old cosmetic YML files...");
                deleteDirectoryRecursively(cosmeticsDir.toPath());
                sender.sendMessage(ChatColor.GREEN + "  ✓ Deleted old cosmetic YML files");
            }
            
            sender.sendMessage(ChatColor.GREEN + "✓ Step 1 Complete: Plugin directories cleaned");
            sender.sendMessage(ChatColor.GRAY + "  (Custom output path left untouched for safety)");
            
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "Error cleaning up old files: " + e.getMessage());
            plugin.getLogger().severe("Error cleaning up old files: " + e.getMessage());
        }
    }
    
    /**
     * Recursively deletes a directory and all its contents
     * @param path The path to delete
     * @throws IOException If an I/O error occurs
     */
    private void deleteDirectoryRecursively(Path path) throws IOException {
        if (Files.exists(path)) {
            Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }
                
                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }
}
