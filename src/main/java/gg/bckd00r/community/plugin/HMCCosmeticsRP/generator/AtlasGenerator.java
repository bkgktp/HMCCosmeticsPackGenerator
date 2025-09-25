package gg.bckd00r.community.plugin.HMCCosmeticsRP.generator;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import gg.bckd00r.community.plugin.HMCCosmeticsRP.HMCCosmeticsPackPlugin;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates Minecraft atlas files (items.json and blocks.json) for texture optimization
 */
public class AtlasGenerator {
    private final HMCCosmeticsPackPlugin plugin;
    private final String namespace;
    private final List<String> textureResources;
    private final Gson gson;

    public AtlasGenerator(HMCCosmeticsPackPlugin plugin, String namespace) {
        this.plugin = plugin;
        this.namespace = namespace;
        this.textureResources = new ArrayList<>();
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    /**
     * Adds a texture resource to the atlas
     * @param textureName The name of the texture (without .png extension)
     */
    public void addTexture(String textureName) {
        String resourcePath = namespace + ":item/" + textureName;
        if (!textureResources.contains(resourcePath)) {
            textureResources.add(resourcePath);
            plugin.getLogger().info("Added texture to atlas: " + resourcePath);
        }
    }

    /**
     * Generates both items.json and blocks.json atlas files
     * @param outputDir The output directory for the atlas files
     */
    public void generateAtlasFiles(File outputDir) {
        if (textureResources.isEmpty()) {
            plugin.getLogger().info("No textures to add to atlas files.");
            return;
        }

        // Create atlases directory
        File atlasesDir = new File(outputDir, "assets/minecraft/atlases");
        if (!atlasesDir.exists() && !atlasesDir.mkdirs()) {
            plugin.getLogger().warning("Failed to create atlases directory: " + atlasesDir.getAbsolutePath());
            return;
        }

        // Generate items.json
        generateAtlasFile(new File(atlasesDir, "items.json"), "items");
        
        // blocks.json generation is disabled for now
        // generateAtlasFile(new File(atlasesDir, "blocks.json"), "blocks");
    }

    /**
     * Generates a single atlas file
     * @param atlasFile The atlas file to create
     * @param atlasType The type of atlas (items or blocks)
     */
    private void generateAtlasFile(File atlasFile, String atlasType) {
        try {
            JsonObject atlasRoot = new JsonObject();
            JsonArray sources = new JsonArray();

            // Add all texture resources to the atlas
            for (String resource : textureResources) {
                JsonObject source = new JsonObject();
                source.addProperty("type", "single");
                source.addProperty("resource", resource);
                sources.add(source);
            }

            atlasRoot.add("sources", sources);

            // Write to file
            try (FileWriter writer = new FileWriter(atlasFile)) {
                gson.toJson(atlasRoot, writer);
            }

            plugin.getLogger().info("Generated " + atlasType + " atlas with " + textureResources.size() + 
                                  " textures: " + atlasFile.getAbsolutePath());

        } catch (IOException e) {
            plugin.getLogger().severe("Error generating " + atlasType + " atlas file: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Clears all texture resources from the atlas
     */
    public void clear() {
        textureResources.clear();
    }

    /**
     * Gets the number of textures in the atlas
     * @return The number of texture resources
     */
    public int getTextureCount() {
        return textureResources.size();
    }
}
