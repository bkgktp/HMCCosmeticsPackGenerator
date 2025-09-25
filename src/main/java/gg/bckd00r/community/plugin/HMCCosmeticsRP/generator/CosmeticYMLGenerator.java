package gg.bckd00r.community.plugin.HMCCosmeticsRP.generator;

import com.google.gson.*;
import gg.bckd00r.community.plugin.HMCCosmeticsRP.HMCCosmeticsPackPlugin;
import gg.bckd00r.community.plugin.HMCCosmeticsRP.config.DataManager;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

public class CosmeticYMLGenerator {
    // Map to store cosmetics by their type (helmet, backpack, etc.)
    private final Map<String, FileConfiguration> cosmeticsByType = new HashMap<>();
    private final HMCCosmeticsPackPlugin plugin;
    private final String namespace;
    private final File tempDir;
    private final Plugin hmcPlugin;
    private final CustomModelDataGenerator modelDataGenerator;
    private final AtlasGenerator atlasGenerator;
    private final Set<String> validCosmeticTypes = new HashSet<>(Arrays.asList(
        "helmet", "backpack", "chestplate", "leggings", "boots", "offhand", "balloon"
    ));
    
    /**
     * Applies model overrides from data.yml to the model
     * @param model The model JSON to modify
     * @param modelName The name of the model (used to look up overrides)
     */
    /**
     * Adds first-person item configuration for backpacks
     * @param config The configuration to modify
     * @param fileName The base filename (without extension)
     * @param type The cosmetic type
     * @param modelPath The base model path
     * @param hasFirstperson Whether a separate firstperson model exists
     */
    private void addFirstPersonItem(FileConfiguration config, String fileName, String type, String modelPath, boolean hasFirstperson) {
        if (!type.equalsIgnoreCase("BACKPACK")) {
            return; // Only add first-person items for backpacks
        }
        
        // Copy the main item configuration to firstperson-item
        String material = plugin.getConfigManager().getDefaultMaterial(type);
        config.set(fileName + ".firstperson-item.material", material);
        config.set(fileName + ".firstperson-item.name", config.getString(fileName + ".item.name"));
        config.set(fileName + ".firstperson-item.lore", config.getStringList(fileName + ".item.lore"));
        
        if (hasFirstperson) {
            // Use separate firstperson model with its own model-data
            String firstpersonModelPath = namespace + ":item/" + fileName.toLowerCase() + "_firstperson";
            
            // Add firstperson texture to atlas
            atlasGenerator.addTexture(fileName.toLowerCase() + "_firstperson");
            
            if (plugin.getConfigManager().useItemModelComponent()) {
                // Use item model components
                config.set(fileName + ".firstperson-item.model-id", firstpersonModelPath);
            } else {
                // Use custom model data - register separate ID for firstperson
                int firstpersonModelData = modelDataGenerator.registerModel(material, firstpersonModelPath);
                config.set(fileName + ".firstperson-item.model-data", firstpersonModelData);
                
                plugin.getLogger().info("Registered firstperson model for " + fileName + " with model-data: " + firstpersonModelData);
            }
        } else {
            // Use same model-data as main item (fallback behavior)
            if (plugin.getConfigManager().useItemModelComponent()) {
                config.set(fileName + ".firstperson-item.model-id", modelPath);
            } else {
                config.set(fileName + ".firstperson-item.model-data", config.getInt(fileName + ".item.model-data"));
            }
        }
        config.set(fileName + ".firstperson-item.flags", config.getStringList(fileName + ".item.flags"));
    }
    
    private void applyModelOverrides(JsonObject model, String modelName) {
        // Always get fresh data from DataManager to ensure we have the latest changes
        DataManager.ModelData modelData = plugin.getDataManager().getModelData(modelName);
        if (modelData == null || modelData.getDisplayData().isEmpty()) {
            plugin.getLogger().fine("No display overrides found for model: " + modelName);
            return;
        }

        plugin.getLogger().info("Applying display overrides for model: " + modelName);
        
        // Get or create the display object in the model
        JsonObject display = model.has("display") ? model.getAsJsonObject("display") : new JsonObject();
        
        // Apply each display override
        for (Map.Entry<String, Map<String, Object>> entry : modelData.getDisplayData().entrySet()) {
            String displayType = entry.getKey();
            JsonObject displayTypeObj = display.has(displayType) ? 
                display.getAsJsonObject(displayType) : new JsonObject();
            
            // Add each property to the display type
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
                
                plugin.getLogger().fine(String.format("Applied override: %s.%s.%s = %s", 
                    displayType, propKey, propKey, value));
            }
            
            display.add(displayType, displayTypeObj);
        }
        
        // Always update the display object, even if empty, to ensure consistency
        model.add("display", display);
        
        // Force update the model data to disk
        if (modelData.shouldForceUpdate()) {
            plugin.getDataManager().updateModelData(modelName, modelData);
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
        
        plugin.getLogger().fine("Skipping " + fileName + " - Invalid cosmetic type");
        return false;
    }

    public CosmeticYMLGenerator(HMCCosmeticsPackPlugin plugin, String namespace) {
        this.plugin = plugin;
        this.namespace = namespace.toLowerCase();
        this.tempDir = new File(plugin.getDataFolder(), "temp/" + this.namespace);
        this.hmcPlugin = plugin.getServer().getPluginManager().getPlugin("HMCCosmetics");
        this.modelDataGenerator = new CustomModelDataGenerator(plugin);
        this.atlasGenerator = new AtlasGenerator(plugin, this.namespace);
        
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
            if (plugin.getConfig().getBoolean("debug", false)) {
                e.printStackTrace();
            }
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
        
        // Model overrides are already applied in loadAndProcessModel method
        // No need to apply them again here

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
        
        // Add texture to atlas
        atlasGenerator.addTexture(fileName.toLowerCase());
        
        // Main item configuration
        config.set(fileName + ".item.material", material);
        config.set(fileName + ".item.name", displayName);
        config.set(fileName + ".item.lore", java.util.Arrays.asList("&7A custom " + type.toLowerCase() + " cosmetic."));
        config.set(fileName + ".item.flags", java.util.Arrays.asList("HIDE_ATTRIBUTES"));
        
        if (plugin.getConfigManager().useItemModelComponent()) {
            // Use item model components
            config.set(fileName + ".item.model-id", modelPath);
        } else {
            // Use custom model data
            int customModelData = modelDataGenerator.registerModel(material, modelPath);
            config.set(fileName + ".item.model-data", customModelData);
        }
        
        // Add first-person item for backpacks
        addFirstPersonItem(config, fileName, type, modelPath, hasFirstperson);

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
        
        // Generate custom model data JSONs after saving all cosmetics
        modelDataGenerator.generateModelJsons();
        
        // Generate atlas files after all textures are added
        atlasGenerator.generateAtlasFiles(new File(plugin.getDataFolder(), "output/" + plugin.getConfigManager().getResourcePackId()));
        
        return type;
    }
    
    public void saveAllCosmetics() {
        // Save to temp directory first (for two-stage output system)
        saveCosmetics(tempDir, false); // Don't merge in temp directory
        
        // Save to final HMCCosmetics directory (with merge)
        if (hmcPlugin == null) {
            plugin.getLogger().warning("HMCCosmetics plugin not found! Could not save cosmetic files to final location.");
            return;
        }
        
        File cosmeticsDir = new File(hmcPlugin.getDataFolder(), "cosmetics/" + namespace);
        saveCosmetics(cosmeticsDir, true); // Merge existing cosmetics
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
                e.printStackTrace();
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
            plugin.getLogger().warning("Model boyanabilirlik kontrolü sırasında hata: " + e.getMessage());
            if (plugin.getConfig().getBoolean("debug", false)) {
                e.printStackTrace();
            }
        }
        return false;
    }
    
    // Material types are now configured in config.yml under settings.default-materials
}
