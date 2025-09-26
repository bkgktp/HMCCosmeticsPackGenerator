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
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class CosmeticYMLGenerator {
    private final Map<String, FileConfiguration> cosmeticsByType = new HashMap<>();
    private final HMCCosmeticsPackPlugin plugin;
    private final String namespace;
    private final File tempDir;
    private final Plugin hmcPlugin;
    private final Set<String> validCosmeticTypes = new HashSet<>(Arrays.asList(
        "helmet", "backpack", "chestplate", "leggings", "boots", "offhand", "balloon"
    ));
    
    private void addFirstPersonItem(FileConfiguration config, String fileName, String type, String modelPath, String itemModelPath, boolean hasFirstperson, boolean isPaintable) {
        if (!type.equalsIgnoreCase("BACKPACK")) {
            return;
        }
        String material = plugin.getConfigManager().getDefaultMaterial(type);
        config.set(fileName + ".firstperson-item.material", material);
        config.set(fileName + ".firstperson-item.name", config.getString(fileName + ".item.name"));
        config.set(fileName + ".firstperson-item.lore", config.getStringList(fileName + ".item.lore"));
        
        if (hasFirstperson) {
            String firstpersonItemModelPath = namespace + ":" + fileName.toLowerCase() + "_firstperson";
            config.set(fileName + ".firstperson-item.model-id", firstpersonItemModelPath);
        } else {
            config.set(fileName + ".firstperson-item.model-id", itemModelPath);
        }
        config.set(fileName + ".firstperson-item.flags", config.getStringList(fileName + ".item.flags"));
        
        if (isPaintable) {
            config.set(fileName + ".firstperson-item.color", "#FFFFFF");
        }
    }
    
    public void loadAndProcessModel(File modelFile, String modelName) throws IOException {
        if (!modelFile.exists()) {
            return;
        }
        
        String jsonContent = new String(Files.readAllBytes(modelFile.toPath()), StandardCharsets.UTF_8);
        JsonObject model = JsonParser.parseString(jsonContent).getAsJsonObject();
        
        DataManager.ModelData modelData = plugin.getDataManager().ensureModelExists(modelName, model);
        if (modelData == null) {
            return;
        }
        
        if (!modelData.getDisplayData().isEmpty()) {
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
            }
        }
        
        try (FileWriter writer = new FileWriter(modelFile)) {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            gson.toJson(model, writer);
        } catch (IOException e) {
            throw e;
        }
        
        if (modelName.endsWith("_firstperson")) {
            String baseName = modelName.replace("_firstperson", "");
            String firstpersonModelPath = namespace + ":item/" + modelName;
            generateNamespaceItemDefinition(modelName, firstpersonModelPath);
        }
    }
    
    private void generateNamespaceItemDefinition(String fileName, String modelPath) {
        try {
            File outputDir = new File(plugin.getDataFolder(), "output/" + plugin.getConfigManager().getResourcePackId());
            File namespaceItemsDir = new File(outputDir, "assets/" + namespace + "/items");
            
            if (!namespaceItemsDir.exists()) {
                if (!namespaceItemsDir.mkdirs()) {
                    return;
                }
            }
            
            JsonObject root = new JsonObject();
            JsonObject model = new JsonObject();
            model.addProperty("type", "model");
            model.addProperty("model", modelPath);
            JsonArray tints = new JsonArray();
            JsonObject tint = new JsonObject();
            tint.addProperty("type", "dye");
            tint.addProperty("default", 16777215);
            tints.add(tint);
            model.add("tints", tints);
            
            root.add("model", model);
            
            File itemFile = new File(namespaceItemsDir, fileName.toLowerCase() + ".json");
            
            try (FileWriter writer = new FileWriter(itemFile)) {
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                gson.toJson(root, writer);
            } catch (IOException e) {
                throw e;
            }
            
        } catch (IOException e) {
        }
    }
    
    
    private void copyBaseTemplateStructure(File outputDir) {
        try {
            File templateDir = new File(plugin.getDataFolder().getParentFile().getParentFile(), "src/main/resources/assets");
            if (templateDir.exists()) {
                copyDirectory(templateDir, new File(outputDir, "assets"));
            }
        } catch (Exception e) {
        }
    }
    
    

    private boolean isValidCosmeticType(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return false;
        }
        
        for (String type : validCosmeticTypes) {
            if (fileName.endsWith("_" + type) || fileName.equals(type)) {
                return true;
            }
        }
        
        return false;
    }

    public CosmeticYMLGenerator(HMCCosmeticsPackPlugin plugin, String namespace) {
        this.plugin = plugin;
        this.namespace = namespace.toLowerCase();
        this.tempDir = new File(plugin.getDataFolder(), "temp/" + this.namespace);
        this.hmcPlugin = plugin.getServer().getPluginManager().getPlugin("HMCCosmetics");
        
        if (!tempDir.exists() && !tempDir.mkdirs()) {
        }
    }

    private JsonObject loadAndProcessModel(File modelFile) throws IOException {
        String fileName = modelFile.getName().replace(".json", "").toLowerCase();
        
        if (!isValidCosmeticType(fileName)) {
            return null;
        }
        String jsonContent = new String(Files.readAllBytes(modelFile.toPath()), StandardCharsets.UTF_8);
        JsonObject model = JsonParser.parseString(jsonContent).getAsJsonObject();
        
        DataManager.ModelData modelData = plugin.getDataManager().ensureModelExists(fileName, model);
        if (modelData == null) {
            return null;
        }
        
        if (!modelData.getDisplayData().isEmpty()) {
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
            }
        }
        
        return model;
    }
    
    public String generateYMLFiles(File modelFile) throws IOException {
        return generateYMLFiles(modelFile, false);
    }
    
    public String generateYMLFiles(File modelFile, boolean hasFirstperson) throws IOException {
        JsonObject model;
        try {
            model = loadAndProcessModel(modelFile);
            if (model == null) {
                return null;
            }
        } catch (Exception e) {
            return null;
        }
        String fileName = modelFile.getName().replace(".json", "").toLowerCase();
        String[] nameParts = fileName.split("_");
        
        String type = "";
        String displayName = String.join(" ", nameParts);
        for (String part : nameParts) {
            String upperPart = part.toUpperCase();
            if (Arrays.asList("HELMET", "BACKPACK", "CHESTPLATE", "LEGGINGS", "BOOTS", "OFFHAND", "BALLOON").contains(upperPart)) {
                type = upperPart;
                displayName = displayName.replace("_" + part, "").replace(part, "").trim();
                break;
            }
        }
        

        File outputFile = new File(modelFile.getParentFile(), modelFile.getName());
        try (FileWriter writer = new FileWriter(outputFile)) {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            gson.toJson(model, writer);
        } catch (IOException e) {
            throw e;
        }

        boolean isPaintable = isModelPaintable(model);

        if (type.isEmpty()) {
            throw new IllegalArgumentException("Invalid filename: " + modelFile.getName() + " - Missing type (HELMET, BACKPACK, etc.)");
        }
        FileConfiguration config = cosmeticsByType.computeIfAbsent(type.toLowerCase(), k -> new YamlConfiguration());
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
        config.set(fileName + ".height", 1);
        config.set(fileName + ".slot", type.toUpperCase());
        
        config.set(fileName + ".permission", "hmccosmetics.cosmetic." + fileName);
        config.set(fileName + ".show-in-menu", true);
        
        String material = plugin.getConfigManager().getDefaultMaterial(type);
        String modelPath = namespace + ":item/" + fileName.toLowerCase();
        String itemModelPath = namespace + ":" + fileName.toLowerCase();
        config.set(fileName + ".item.material", material);
        config.set(fileName + ".item.name", displayName);
        config.set(fileName + ".item.lore", java.util.Arrays.asList("&7A custom " + type.toLowerCase() + " cosmetic."));
        config.set(fileName + ".item.flags", java.util.Arrays.asList("HIDE_ATTRIBUTES"));
        
        config.set(fileName + ".item.model-id", itemModelPath);
        
        addFirstPersonItem(config, fileName, type, modelPath, itemModelPath, hasFirstperson, isPaintable);
        
        generateNamespaceItemDefinition(fileName, modelPath);

        config.set(fileName + ".item.amount", 1);
        
        switch (type) {
            case "BALLOON":
                //config.set(fileName + ".model", fileName);
                config.set(fileName + ".show-lead", true);
                config.set(fileName + ".balloon-offset.x", 0.5);
                config.set(fileName + ".balloon-offset.y", 3);
                config.set(fileName + ".balloon-offset.z", 0.5);
                break;
        }
        
        if (isPaintable) {
            config.set(fileName + ".dyeable", true);
            config.set(fileName + ".item.color", "#FFFFFF");
            if (!config.contains(fileName + ".item.lore")) {
                config.set(fileName + ".item.lore", Arrays.asList(
                    "",
                    "&7This item can be dyed!"
                ));
            }
        }
        
        saveAllCosmetics();
        
        generatePackMcmeta();
        
        copyResourcePackToExternalPath();
        
        
        return type;
    }
    
    private void generatePackMcmeta() {
        try {
            LocalDate currentDate = LocalDate.now();
            String formattedDate = currentDate.format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
            
            JsonObject packMeta = new JsonObject();
            JsonObject pack = new JsonObject();
            JsonObject description = new JsonObject();
            description.addProperty("text", "&dHMCCosmetics ResourcePack &8@bckd00r &6(" + formattedDate + ")");
            
            pack.add("description", description);
            pack.addProperty("pack_format", 32);
            
            JsonObject supportedFormats = new JsonObject();
            supportedFormats.addProperty("min_inclusive", 32);
            supportedFormats.addProperty("max_inclusive", 46);
            pack.add("supported_formats", supportedFormats);
            
            packMeta.add("pack", pack);
            
            File outputDir = new File(plugin.getDataFolder(), "output/" + plugin.getConfigManager().getResourcePackId());
            File packMetaFile = new File(outputDir, "pack.mcmeta");
            
            if (!outputDir.exists()) {
                outputDir.mkdirs();
            }
            try (FileWriter writer = new FileWriter(packMetaFile, StandardCharsets.UTF_8)) {
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                gson.toJson(packMeta, writer);
            }
            
            copyPackIcon(outputDir);
            
        } catch (Exception e) {
        }
    }
    
    public void copyResourcePackToExternalPath() {
        String copyToPath = plugin.getConfigManager().getCopyToPath();
        if (copyToPath == null || copyToPath.trim().isEmpty()) {
            return;
        }
        
        try {
            File sourceDir = new File(plugin.getDataFolder(), "output/" + plugin.getConfigManager().getResourcePackId());
            File targetDir = new File(copyToPath, plugin.getConfigManager().getResourcePackId());
            
            if (!sourceDir.exists()) {
                return;
            }
            
            if (!targetDir.getParentFile().exists()) {
                targetDir.getParentFile().mkdirs();
            }
            
            if (targetDir.exists()) {
                deleteDirectory(targetDir);
            }
            
            copyDirectory(sourceDir, targetDir);
            
        } catch (Exception e) {
        }
    }
    
    
    private void copyDirectory(File source, File target) throws IOException {
        if (source.isDirectory()) {
            if (!target.exists()) {
                if (!target.mkdirs()) {
                    throw new IOException("Failed to create directory: " + target.getAbsolutePath());
                }
            }
            
            String[] files = source.list();
            if (files != null) {
                for (String fileName : files) {
                    File sourceFile = new File(source, fileName);
                    File targetFile = new File(target, fileName);
                    copyDirectory(sourceFile, targetFile);
                }
            }
        } else {
            File parentDir = target.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                if (!parentDir.mkdirs()) {
                    throw new IOException("Failed to create parent directory: " + parentDir.getAbsolutePath());
                }
            }
            
            Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }
    
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
    
    private void copyPackIcon(File outputDir) {
        try {
            java.io.InputStream packIconStream = plugin.getResource("pack.png");
            if (packIconStream == null) {
                return;
            }
            
            File packIconFile = new File(outputDir, "pack.png");
            Files.copy(packIconStream, packIconFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            packIconStream.close();
            
        } catch (Exception e) {
        }
    }
    
    public void saveAllCosmetics() {
        saveCosmetics(tempDir, false);
        
        boolean shouldTransferFiles = plugin.getConfigManager().shouldTransferGeneratedCosmeticYmlFiles();
        
        if (shouldTransferFiles) {
            transferCosmeticFilesToHMCCosmetics();
        } else {
            executeSendDataCommand();
        }
    }
    
    private void transferCosmeticFilesToHMCCosmetics() {
        if (hmcPlugin == null) {
            return;
        }
        
        File cosmeticsDir = new File(hmcPlugin.getDataFolder(), "cosmetics/" + namespace);
        saveCosmetics(cosmeticsDir, true);
    }
    
    private void executeSendDataCommand() {
    }
    
    private void saveCosmetics(File targetDir, boolean mergeExisting) {
        if (!targetDir.exists() && !targetDir.mkdirs()) {
            return;
        }
        for (Map.Entry<String, FileConfiguration> entry : cosmeticsByType.entrySet()) {
            String type = entry.getKey();
            FileConfiguration config = entry.getValue();
            
            File typeFile = new File(targetDir, type.toLowerCase() + ".yml");
            
            try {
                if (mergeExisting && typeFile.exists()) {
                    FileConfiguration existingConfig = YamlConfiguration.loadConfiguration(typeFile);
                    for (String key : existingConfig.getKeys(true)) {
                        if (!config.contains(key)) {
                            config.set(key, existingConfig.get(key));
                        }
                    }
                }
                
                config.save(typeFile);
                
            } catch (Exception e) {
            }
        }
    }
    
    private boolean isModelPaintable(JsonObject model) {
        try {
            if (model.has("textures") && model.get("textures").isJsonObject()) {
                JsonObject textures = model.getAsJsonObject("textures");
                for (Map.Entry<String, JsonElement> entry : textures.entrySet()) {
                    String textureName = entry.getValue().getAsString().toLowerCase();
                    if (textureName.contains("layer") || textureName.contains("overlay")) {
                        return true;
                    }
                }
            }
            
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
        }
        return false;
    }
}
