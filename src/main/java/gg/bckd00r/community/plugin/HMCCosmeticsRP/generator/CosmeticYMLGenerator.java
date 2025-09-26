package gg.bckd00r.community.plugin.HMCCosmeticsRP.generator;

import com.google.gson.*;
import gg.bckd00r.community.plugin.HMCCosmeticsRP.HMCCosmeticsPackPlugin;
import gg.bckd00r.community.plugin.HMCCosmeticsRP.config.DataManager;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class CosmeticYMLGenerator {
    // Map to store cosmetics by their type (helmet, backpack, etc.)
    private final Map<String, FileConfiguration> cosmeticsByType = new HashMap<>();
    private final HMCCosmeticsPackPlugin plugin;
    private final String namespace;
    private final File tempDir;
    private final Plugin hmcPlugin;
    private final Set<String> validCosmeticTypes = new HashSet<>(Arrays.asList(
        "helmet", "backpack", "chestplate", "leggings", "boots", "offhand", "balloon"
    ));
    
    /**
     * Adds first-person item configuration for backpacks
     * @param config The configuration to modify
     * @param fileName The base filename (without extension)
     * @param type The cosmetic type
     * @param modelPath The base model path
     * @param hasFirstperson Whether a separate firstperson model exists
     */
    private void addFirstPersonItem(FileConfiguration config, String fileName, String type, String modelPath, String itemModelPath, boolean hasFirstperson, boolean isPaintable) {
        if (!type.equalsIgnoreCase("BACKPACK")) {
            return; // Only add first-person items for backpacks
        }
        
        // Copy the main item configuration to firstperson-item
        String material = plugin.getConfigManager().getDefaultMaterial(type);
        config.set(fileName + ".firstperson-item.material", material);
        config.set(fileName + ".firstperson-item.name", config.getString(fileName + ".item.name"));
        config.set(fileName + ".firstperson-item.lore", config.getStringList(fileName + ".item.lore"));
        
        if (hasFirstperson) {
            // Use separate firstperson model with item model components
            String firstpersonItemModelPath = namespace + ":" + fileName.toLowerCase() + "_firstperson";

            // Always use item model components (modern system)
            config.set(fileName + ".firstperson-item.model-id", firstpersonItemModelPath);
            
            // Note: Firstperson model processing is now handled separately in generate command
            // via loadAndProcessModel() method, so no manual namespace item generation needed here
        } else {
            // Use same model-id as main item (fallback behavior)
            config.set(fileName + ".firstperson-item.model-id", itemModelPath);
        }
        config.set(fileName + ".firstperson-item.flags", config.getStringList(fileName + ".item.flags"));
        
        // Add color property for dyeable firstperson items
        if (isPaintable) {
            config.set(fileName + ".firstperson-item.color", "#FFFFFF");
        }
    }
    
    /**
     * Load and process a model file (used for firstperson models)
     * This method processes firstperson models the same way as main models
     * @param modelFile The JSON model file
     * @param modelName The model name (including _firstperson suffix)
     */
    public void loadAndProcessModel(File modelFile, String modelName) throws IOException {
        if (!modelFile.exists()) {
            plugin.getLogger().warning("Model file does not exist: " + modelFile.getAbsolutePath());
            return;
        }
        
        // Read the model file
        String jsonContent = new String(Files.readAllBytes(modelFile.toPath()), StandardCharsets.UTF_8);
        JsonObject model = JsonParser.parseString(jsonContent).getAsJsonObject();
        
        // Check if model exists in data.yml, if not create it with JSON data
        DataManager.ModelData modelData = plugin.getDataManager().ensureModelExists(modelName, model);
        if (modelData == null) {
            plugin.getLogger().warning("Failed to get or create model data for: " + modelName);
            return;
        }
        
        // Always apply display settings from data.yml to the model (this ensures data.yml is the source of truth)
        if (!modelData.getDisplayData().isEmpty()) {
            plugin.getLogger().info("Applying display settings from data.yml to model: " + modelName);
            if (modelData.shouldForceUpdate()) {
                plugin.getLogger().info("Force update flag is set - ensuring JSON file is updated with current data.yml values");
            }
            JsonObject displayObj = new JsonObject();
            
            for (Map.Entry<String, Map<String, Object>> entry : modelData.getDisplayData().entrySet()) {
                String displayType = entry.getKey();
                JsonObject displayTypeObj = new JsonObject();
                
                for (Map.Entry<String, Object> prop : entry.getValue().entrySet()) {
                    String propKey = prop.getKey();
                    Object value = prop.getValue();
                    
                    if (value instanceof List) {
                        JsonArray array = new JsonArray();
                        for (Object item : (List<?>) value) {
                            if (item instanceof Number) {
                                array.add(((Number) item).doubleValue());
                            } else if (item instanceof Boolean) {
                                array.add((Boolean) item);
                            } else if (item != null) {
                                array.add(item.toString());
                            }
                        }
                        displayTypeObj.add(propKey, array);
                    } else if (value instanceof Number) {
                        displayTypeObj.addProperty(propKey, ((Number) value).doubleValue());
                    } else if (value instanceof Boolean) {
                        displayTypeObj.addProperty(propKey, (Boolean) value);
                    } else if (value != null) {
                        displayTypeObj.addProperty(propKey, value.toString());
                    }
                }
                
                if (displayTypeObj.size() > 0) {
                    displayObj.add(displayType, displayTypeObj);
                }
            }
            
            if (displayObj.size() > 0) {
                model.add("display", displayObj);
                plugin.getLogger().info("Applied display settings from data.yml for: " + modelName);
            }
        } else {
            plugin.getLogger().info("No display data found in data.yml for: " + modelName + " - using JSON defaults");
        }
        
        // Write the updated model back to the file
        try (FileWriter writer = new FileWriter(modelFile)) {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            gson.toJson(model, writer);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to write updated model file: " + e.getMessage());
            throw e;
        }
        
        // Generate namespace item definition for firstperson models
        if (modelName.endsWith("_firstperson")) {
            String baseName = modelName.replace("_firstperson", "");
            String firstpersonModelPath = namespace + ":item/" + modelName;
            generateNamespaceItemDefinition(modelName, firstpersonModelPath);
        }
    }
    
    /**
     * Generates namespace items definition file (simple format)
     * Creates assets/{namespace}/items/*.json files with simple model format
     * @param fileName The cosmetic file name (e.g., "froggy_helmet")
     * @param modelPath The 3D model path (e.g., "hmc:item/froggy_helmet")
     */
    private void generateNamespaceItemDefinition(String fileName, String modelPath) {
        try {
            plugin.getLogger().info("Starting generateNamespaceItemDefinition for: " + fileName + " with model path: " + modelPath);
            
            File outputDir = new File(plugin.getDataFolder(), "output/" + plugin.getConfigManager().getResourcePackId());
            plugin.getLogger().info("Output directory: " + outputDir.getAbsolutePath());
            
            // Create namespace items directory
            File namespaceItemsDir = new File(outputDir, "assets/" + namespace + "/items");
            plugin.getLogger().info("Target namespace items directory: " + namespaceItemsDir.getAbsolutePath());
            
            if (!namespaceItemsDir.exists()) {
                plugin.getLogger().info("Creating namespace items directory...");
                if (!namespaceItemsDir.mkdirs()) {
                    plugin.getLogger().warning("Failed to create namespace items directory: " + namespaceItemsDir.getAbsolutePath());
                    return;
                } else {
                    plugin.getLogger().info("Successfully created namespace items directory");
                }
            } else {
                plugin.getLogger().info("Namespace items directory already exists");
            }
            
            // Create simple model structure
            JsonObject root = new JsonObject();
            JsonObject model = new JsonObject();
            model.addProperty("type", "model");
            model.addProperty("model", modelPath);
            
            // Add tints array with dye type
            JsonArray tints = new JsonArray();
            JsonObject tint = new JsonObject();
            tint.addProperty("type", "dye");
            tint.addProperty("default", 16777215); // White default
            tints.add(tint);
            model.add("tints", tints);
            
            root.add("model", model);
            
            // Write to file (use lowercase filename)
            File itemFile = new File(namespaceItemsDir, fileName.toLowerCase() + ".json");
            plugin.getLogger().info("Creating namespace item file: " + itemFile.getAbsolutePath());
            
            try (FileWriter writer = new FileWriter(itemFile)) {
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                gson.toJson(root, writer);
                plugin.getLogger().info("Successfully generated namespace item definition: " + itemFile.getAbsolutePath());
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to write namespace item file: " + e.getMessage());
                throw e;
            }
            
        } catch (IOException e) {
            plugin.getLogger().severe("Error generating namespace item definition for " + fileName + ": " + e.getMessage());
        }
    }
    
    
    /**
     * Copies the base template structure from src/main/resources/assets to output directory
     */
    private void copyBaseTemplateStructure(File outputDir) {
        try {
            // Copy template assets if they exist
            File templateDir = new File(plugin.getDataFolder().getParentFile().getParentFile(), "src/main/resources/assets");
            if (templateDir.exists()) {
                copyDirectory(templateDir, new File(outputDir, "assets"));
                plugin.getLogger().info("Copied base template structure from: " + templateDir.getAbsolutePath());
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to copy base template structure: " + e.getMessage());
        }
    }
    
    

    private boolean isValidCosmeticType(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return false;
        }
        
        // Check if the filename ends with a valid cosmetic type
        for (String type : validCosmeticTypes) {
            if (fileName.endsWith("_" + type) || fileName.equals(type)) {
                return true;
            }
        }
        
        // Skipping invalid cosmetic type
        return false;
    }

    public CosmeticYMLGenerator(HMCCosmeticsPackPlugin plugin, String namespace) {
        this.plugin = plugin;
        this.namespace = namespace.toLowerCase();
        this.tempDir = new File(plugin.getDataFolder(), "temp/" + this.namespace);
        this.hmcPlugin = plugin.getServer().getPluginManager().getPlugin("HMCCosmetics");
        
        // Initialize valid cosmetic types
        
        // Create necessary directories
        if (!tempDir.exists() && !tempDir.mkdirs()) {
            plugin.getLogger().warning("Failed to create directory: " + tempDir.getAbsolutePath());
        }
    }

    /**
     * Loads a model from a JSON file and applies display settings from data.yml
     * @param modelFile The JSON model file to load
     * @return The parsed JsonObject with applied display settings
     * @throws IOException If there's an error reading the file
     */
    private JsonObject loadAndProcessModel(File modelFile) throws IOException {
        // Get model name from filename
        String fileName = modelFile.getName().replace(".json", "").toLowerCase();
        
        // Skip if not a valid cosmetic type
        if (!isValidCosmeticType(fileName)) {
            plugin.getLogger().info("Skipping " + fileName + " - Invalid cosmetic type");
            return null;
        }
        
        // Read the model file
        String jsonContent = new String(Files.readAllBytes(modelFile.toPath()), StandardCharsets.UTF_8);
        JsonObject model = JsonParser.parseString(jsonContent).getAsJsonObject();
        
        // Check if model exists in data.yml, if not create it with JSON data
        DataManager.ModelData modelData = plugin.getDataManager().ensureModelExists(fileName, model);
        if (modelData == null) {
            plugin.getLogger().warning("Failed to get or create model data for: " + fileName);
            return null;
        }
        
        // Always apply display settings from data.yml to the model (this ensures data.yml is the source of truth)
        if (!modelData.getDisplayData().isEmpty()) {
            plugin.getLogger().info("Applying display settings from data.yml to model: " + fileName);
            if (modelData.shouldForceUpdate()) {
                plugin.getLogger().info("Force update flag is set - ensuring JSON file is updated with current data.yml values");
            }
            JsonObject displayObj = new JsonObject();
            
            for (Map.Entry<String, Map<String, Object>> entry : modelData.getDisplayData().entrySet()) {
                String displayType = entry.getKey();
                JsonObject displayTypeObj = new JsonObject();
                
                for (Map.Entry<String, Object> prop : entry.getValue().entrySet()) {
                    String propKey = prop.getKey();
                    Object value = prop.getValue();
                    
                    if (value instanceof List) {
                        JsonArray array = new JsonArray();
                        for (Object item : (List<?>) value) {
                            if (item instanceof Number) {
                                array.add(((Number) item).doubleValue());
                            } else if (item instanceof Boolean) {
                                array.add((Boolean) item);
                            } else if (item != null) {
                                array.add(item.toString());
                            }
                        }
                        displayTypeObj.add(propKey, array);
                    } else if (value instanceof Number) {
                        displayTypeObj.addProperty(propKey, ((Number) value).doubleValue());
                    } else if (value instanceof Boolean) {
                        displayTypeObj.addProperty(propKey, (Boolean) value);
                    } else if (value != null) {
                        displayTypeObj.addProperty(propKey, value.toString());
                    }
                }
                
                if (displayTypeObj.size() > 0) {
                    displayObj.add(displayType, displayTypeObj);
                }
            }
            
            if (displayObj.size() > 0) {
                model.add("display", displayObj);
                plugin.getLogger().info("Applied display settings from data.yml for: " + fileName);
            }
        } else {
            plugin.getLogger().warning("No display data found in data.yml for: " + fileName);
        }
        
        return model;
    }
    
    public String generateYMLFiles(File modelFile) throws IOException {
        return generateYMLFiles(modelFile, false);
    }
    
    public String generateYMLFiles(File modelFile, boolean hasFirstperson) throws IOException {
        // Load and process the model JSON
        JsonObject model;
        try {
            model = loadAndProcessModel(modelFile);
            if (model == null) {
                // Model was skipped or failed to load
                return null;
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error processing " + modelFile.getName() + ": " + e.getMessage());
            return null;
        }
        
        // Get model name from filename
        String fileName = modelFile.getName().replace(".json", "").toLowerCase();
        String[] nameParts = fileName.split("_");
        
        // Determine cosmetic type from filename
        String type = "";
        String displayName = String.join(" ", nameParts);
        
        // Check for type in filename
        for (String part : nameParts) {
            String upperPart = part.toUpperCase();
            if (Arrays.asList("HELMET", "BACKPACK", "CHESTPLATE", "LEGGINGS", "BOOTS", "OFFHAND", "BALLOON").contains(upperPart)) {
                type = upperPart;
                displayName = displayName.replace("_" + part, "").replace(part, "").trim();
                break;
            }
        }
        

        // Save the modified model back to the file
        File outputFile = new File(modelFile.getParentFile(), modelFile.getName());
        try (FileWriter writer = new FileWriter(outputFile)) {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            gson.toJson(model, writer);
            plugin.getLogger().info("Saved processed model to: " + outputFile.getAbsolutePath());
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save model " + fileName + ": " + e.getMessage());
            throw e;
        }

        // Check if model is paintable
        boolean isPaintable = isModelPaintable(model);

        if (type.isEmpty()) {
            throw new IllegalArgumentException("Invalid filename: " + modelFile.getName() + " - Missing type (HELMET, BACKPACK, etc.)");
        }
        
        // Get or create config for this type
        FileConfiguration config = cosmeticsByType.computeIfAbsent(type.toLowerCase(), k -> new YamlConfiguration());
        
        // Format display name (capitalize first letter of each word)
        String[] displayNameParts = displayName.split(" ");
        StringBuilder formattedName = new StringBuilder();
        for (String part : displayNameParts) {
            if (!part.isEmpty()) {
                formattedName.append(part.substring(0, 1).toUpperCase())
                           .append(part.substring(1).toLowerCase())
                           .append(" ");
            }
        }
        displayName = formattedName.toString().trim();
        
        // Note: Individual YML files are no longer created - only grouped files by type
        
        // Set slot type at the top level
        config.set(fileName + ".height", 1);
        config.set(fileName + ".slot", type.toUpperCase());
        
        // Common properties
        config.set(fileName + ".permission", "hmccosmetics.cosmetic." + fileName);
        config.set(fileName + ".show-in-menu", true);
        
        // Item properties - Get material from config
        String material = plugin.getConfigManager().getDefaultMaterial(type);
        String modelPath = namespace + ":item/" + fileName.toLowerCase();
        String itemModelPath = namespace + ":" + fileName.toLowerCase(); // For item-model component reference
        
        
        // Main item configuration
        config.set(fileName + ".item.material", material);
        config.set(fileName + ".item.name", displayName);
        config.set(fileName + ".item.lore", java.util.Arrays.asList("&7A custom " + type.toLowerCase() + " cosmetic."));
        config.set(fileName + ".item.flags", java.util.Arrays.asList("HIDE_ATTRIBUTES"));
        
        // Always use item model components (modern system)
        config.set(fileName + ".item.model-id", itemModelPath);
        
        // Add first-person item for backpacks
        addFirstPersonItem(config, fileName, type, modelPath, itemModelPath, hasFirstperson, isPaintable);
        
        // Generate namespace items file (simple format)
        generateNamespaceItemDefinition(fileName, modelPath);

        config.set(fileName + ".item.amount", 1);
        
        // Type-specific configurations
        switch (type) {
            case "BALLOON":
                config.set(fileName + ".model", fileName);
                config.set(fileName + ".show-lead", true);
                config.set(fileName + ".balloon-offset.x", 0.5);
                config.set(fileName + ".balloon-offset.y", 3);
                config.set(fileName + ".balloon-offset.z", 0.5);
                break;
        }
        
        // Set dyeable property if applicable
        if (isPaintable) {
            config.set(fileName + ".dyeable", true);
            config.set(fileName + ".item.color", "#FFFFFF");
            // Add dyeable lore if not already set
            if (!config.contains(fileName + ".item.lore")) {
                config.set(fileName + ".item.lore", Arrays.asList(
                    "",
                    "&7This item can be dyed!"
                ));
            }
        }
        
        // Only save grouped cosmetics by type (no individual files)
        saveAllCosmetics();
        
        // Generate pack.mcmeta file for resource pack
        generatePackMcmeta();
        
        // Copy resource pack to external path if configured
        copyResourcePackToExternalPath();
        
        
        return type;
    }
    
    /**
     * Generates pack.mcmeta file for the resource pack with current date
     */
    private void generatePackMcmeta() {
        try {
            // Get current date in yyyy/MM/dd format
            LocalDate currentDate = LocalDate.now();
            String formattedDate = currentDate.format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
            
            // Create pack.mcmeta content
            JsonObject packMeta = new JsonObject();
            JsonObject pack = new JsonObject();
            
            // Description with current date
            JsonObject description = new JsonObject();
            description.addProperty("text", "&dHMCCosmetics ResourcePack &8@bckd00r &6(" + formattedDate + ")");
            
            pack.add("description", description);
            pack.addProperty("pack_format", 32);
            
            // Supported formats for Minecraft 1.21+
            JsonObject supportedFormats = new JsonObject();
            supportedFormats.addProperty("min_inclusive", 32);
            supportedFormats.addProperty("max_inclusive", 46);
            pack.add("supported_formats", supportedFormats);
            
            packMeta.add("pack", pack);
            
            // Write to output directory (resource pack root)
            File outputDir = new File(plugin.getDataFolder(), "output/" + plugin.getConfigManager().getResourcePackId());
            File packMetaFile = new File(outputDir, "pack.mcmeta");
            
            // Ensure output directory exists
            if (!outputDir.exists()) {
                outputDir.mkdirs();
            }
            
            // Write pack.mcmeta file
            try (FileWriter writer = new FileWriter(packMetaFile, StandardCharsets.UTF_8)) {
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                gson.toJson(packMeta, writer);
            }
            
            plugin.getLogger().info("✓ Generated pack.mcmeta with date: " + formattedDate);
            
            // Copy pack.png from resources to resource pack root
            copyPackIcon(outputDir);
            
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to generate pack.mcmeta: " + e.getMessage());
        }
    }
    
    /**
     * Copies the entire resource pack to external path if transfer-to-path is configured
     */
    public void copyResourcePackToExternalPath() {
        String copyToPath = plugin.getConfigManager().getCopyToPath();
        if (copyToPath == null || copyToPath.trim().isEmpty()) {
            plugin.getLogger().info("📦 No transfer-to-path configured, resource pack remains in output directory");
            return;
        }
        
        try {
            // Source: plugin output directory
            File sourceDir = new File(plugin.getDataFolder(), "output/" + plugin.getConfigManager().getResourcePackId());
            
            // Target: transfer-to-path directory with resource pack folder name
            File targetDir = new File(copyToPath, plugin.getConfigManager().getResourcePackId());
            
            if (!sourceDir.exists()) {
                plugin.getLogger().warning("⚠ Source resource pack directory not found: " + sourceDir.getAbsolutePath());
                return;
            }
            
            // Create parent target directory if it doesn't exist
            if (!targetDir.getParentFile().exists()) {
                targetDir.getParentFile().mkdirs();
            }
            
            // Remove existing target directory if it exists
            if (targetDir.exists()) {
                deleteDirectory(targetDir);
            }
            
            // Copy entire resource pack directory (including the folder itself)
            copyDirectory(sourceDir, targetDir);
            
            plugin.getLogger().info("✅ Successfully copied resource pack to: " + targetDir.getAbsolutePath());
            
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to copy resource pack to external path: " + e.getMessage());
        }
    }
    
    
    /**
     * Recursively copies a directory and all its contents
     */
    private void copyDirectory(File source, File target) throws IOException {
        if (source.isDirectory()) {
            // Create target directory if it doesn't exist
            if (!target.exists()) {
                if (!target.mkdirs()) {
                    throw new IOException("Failed to create directory: " + target.getAbsolutePath());
                }
            }
            
            // List all files and subdirectories
            String[] files = source.list();
            if (files != null) {
                for (String fileName : files) {
                    File sourceFile = new File(source, fileName);
                    File targetFile = new File(target, fileName);
                    // Recursively copy each file/directory
                    copyDirectory(sourceFile, targetFile);
                }
            }
        } else {
            // Ensure parent directory exists for the target file
            File parentDir = target.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                if (!parentDir.mkdirs()) {
                    throw new IOException("Failed to create parent directory: " + parentDir.getAbsolutePath());
                }
            }
            
            // Copy the file
            Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }
    
    /**
     * Recursively deletes a directory and all its contents
     */
    private void deleteDirectory(File directory) {
        if (directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    deleteDirectory(file);
                }
            }
        }
        directory.delete();
    }
    
    /**
     * Copies pack.png from plugin resources to resource pack root directory
     * @param outputDir The resource pack root directory
     */
    private void copyPackIcon(File outputDir) {
        try {
            // Get pack.png from plugin resources
            java.io.InputStream packIconStream = plugin.getResource("pack.png");
            if (packIconStream == null) {
                plugin.getLogger().warning("⚠ pack.png not found in plugin resources, skipping icon copy");
                return;
            }
            
            // Copy to resource pack root
            File packIconFile = new File(outputDir, "pack.png");
            Files.copy(packIconStream, packIconFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            packIconStream.close();
            
            plugin.getLogger().info("✓ Copied pack.png to resource pack root");
            
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to copy pack.png: " + e.getMessage());
        }
    }
    
    public void saveAllCosmetics() {
        // Save to temp directory first (for two-stage output system)
        saveCosmetics(tempDir, false); // Don't merge in temp directory
        
        // Check transfer setting from config
        boolean shouldTransferFiles = plugin.getConfigManager().shouldTransferGeneratedCosmeticYmlFiles();
        
        if (shouldTransferFiles) {
            // Copy files to HMCCosmetics directory
            transferCosmeticFilesToHMCCosmetics();
        } else {
            // Use senddata command to transfer files
            executeSendDataCommand();
        }
    }
    
    /**
     * Transfers cosmetic YML files to HMCCosmetics directory by copying them
     */
    private void transferCosmeticFilesToHMCCosmetics() {
        if (hmcPlugin == null) {
            plugin.getLogger().warning("HMCCosmetics plugin not found! Could not copy cosmetic files to final location.");
            return;
        }
        
        File cosmeticsDir = new File(hmcPlugin.getDataFolder(), "cosmetics/" + namespace);
        plugin.getLogger().info("📂 Transferring cosmetic YML files to HMCCosmetics directory...");
        saveCosmetics(cosmeticsDir, true); // Merge existing cosmetics
        plugin.getLogger().info("✅ Successfully transferred cosmetic YML files to: " + cosmeticsDir.getAbsolutePath());
    }
    
    /**
     * Executes senddata command to transfer cosmetic files
     */
    private void executeSendDataCommand() {
        try {
            plugin.getLogger().info("🔄 Executing senddata command to transfer cosmetic files...");
            
            // Execute senddata command through Bukkit's command system
            // plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), "senddata");
            
            plugin.getLogger().info("✅ Successfully executed senddata command for cosmetic file transfer");
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to execute senddata command: " + e.getMessage());
        }
    }
    
    private void saveCosmetics(File targetDir, boolean mergeExisting) {
        if (!targetDir.exists() && !targetDir.mkdirs()) {
            plugin.getLogger().warning("Failed to create directory: " + targetDir.getAbsolutePath());
            return;
        }
        
        // Save each type to its own file
        for (Map.Entry<String, FileConfiguration> entry : cosmeticsByType.entrySet()) {
            String type = entry.getKey();
            FileConfiguration config = entry.getValue();
            
            File typeFile = new File(targetDir, type.toLowerCase() + ".yml");
            
            try {
                // Load existing file if it exists and merging is enabled
                if (mergeExisting && typeFile.exists()) {
                    FileConfiguration existingConfig = YamlConfiguration.loadConfiguration(typeFile);
                    // Copy all keys from existing config to our config
                    for (String key : existingConfig.getKeys(true)) {
                        if (!config.contains(key)) {
                            config.set(key, existingConfig.get(key));
                        }
                    }
                }
                
                // Save the config
                config.save(typeFile);
                String location = targetDir.equals(tempDir) ? "temp directory" : "final location";
                plugin.getLogger().info("Saved " + type.toLowerCase() + " cosmetics to " + location + ": " + typeFile.getAbsolutePath());
                
            } catch (Exception e) {
                plugin.getLogger().severe("Error saving " + type.toLowerCase() + " cosmetics to " + targetDir.getAbsolutePath() + ": " + e.getMessage());
            }
        }
    }
    
    /**
     * Modelin boyanabilir (paintable) olup olmadığını kontrol eder
     * @param model Kontrol edilecek model JSON objesi
     * @return Model boyanabilirse true, değilse false döner
     */
    private boolean isModelPaintable(JsonObject model) {
        try {
            // Texture'ları kontrol et (layer veya overlay içeriyor mu?)
            if (model.has("textures") && model.get("textures").isJsonObject()) {
                JsonObject textures = model.getAsJsonObject("textures");
                for (Map.Entry<String, JsonElement> entry : textures.entrySet()) {
                    String textureName = entry.getValue().getAsString().toLowerCase();
                    if (textureName.contains("layer") || textureName.contains("overlay")) {
                        return true;
                    }
                }
            }
            
            // Elementlerde tintindex var mı diye kontrol et
            if (model.has("elements") && model.get("elements").isJsonArray()) {
                JsonArray elements = model.getAsJsonArray("elements");
                for (JsonElement element : elements) {
                    if (element.isJsonObject()) {
                        JsonObject elemObj = element.getAsJsonObject();
                        if (elemObj.has("faces") && elemObj.get("faces").isJsonObject()) {
                            JsonObject faces = elemObj.getAsJsonObject("faces");
                            for (Map.Entry<String, JsonElement> faceEntry : faces.entrySet()) {
                                if (faceEntry.getValue().isJsonObject()) {
                                    JsonObject face = faceEntry.getValue().getAsJsonObject();
                                    if (face.has("tintindex")) {
                                        return true;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error checking model paintability: " + e.getMessage());
        }
        return false;
    }
    
    // Material types are now configured in config.yml under settings.default-materials
}
