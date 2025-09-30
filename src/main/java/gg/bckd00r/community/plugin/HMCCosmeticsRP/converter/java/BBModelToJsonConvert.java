package gg.bckd00r.community.plugin.HMCCosmeticsRP.converter.java;

import com.google.gson.*;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public class BBModelToJsonConvert {

    // Convert UV coordinates based on texture size
    // BBModel uses pixel coordinates, Minecraft uses normalized coordinates (0-16)
    private static JsonArray normalizeUV(JsonArray uv, int textureWidth, int textureHeight) {
        JsonArray out = new JsonArray();

        // Calculate scale factors based on texture dimensions
        // Minecraft expects coordinates in 0-16 range for standard 16x16 textures
        double scaleX = 16.0 / textureWidth;
        double scaleY = 16.0 / textureHeight;

        for (int i = 0; i < uv.size(); i++) {
            double val = uv.get(i).getAsDouble();
            // Apply appropriate scale based on coordinate index (x or y)
            double scaledVal = (i % 2 == 0) ? val * scaleX : val * scaleY;
            // Round to 4 decimal places to prevent floating point precision issues
            out.add(Math.round(scaledVal * 10000.0) / 10000.0);
        }
        return out;
    }

    /**
     * @param bbmodelPath   .bbmodel dosyası
     * @param mcjsonPath    çıkacak minecraft model json (assets/<ns>/models/... değil; bu sadece dosya yolu)
     * @param texturesRoot  assets/<namespace>/textures KÖK klasörü (örn: /pack/assets/hmc/textures)
     * @param namespace     örn: "hmc"
     */
    public static void convert(String bbmodelPath,
                               String mcjsonPath,
                               String texturesRoot,
                               String namespace) throws IOException {

        // BBModel conversion starting

        // Extract model name from JSON path for consistent texture naming
        String modelName = new File(mcjsonPath).getName().replace(".json", "");
        // Using model name for texture consistency

        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        // Add null check for file reading
        String bbmodelContent = Files.readString(Path.of(bbmodelPath));
        if (bbmodelContent == null || bbmodelContent.trim().isEmpty()) {
            throw new IOException("BBModel file is empty or null: " + bbmodelPath);
        }

        // BBModel file loaded

        JsonObject bb;
        try {
            bb = gson.fromJson(bbmodelContent, JsonObject.class);
            if (bb == null) {
                // Failed to parse BBModel JSON
                throw new IOException("Failed to parse BBModel JSON: " + bbmodelPath);
            }
            // BBModel JSON parsed successfully
        } catch (JsonSyntaxException e) {
            // Invalid JSON syntax in BBModel
            throw new IOException("Invalid JSON syntax in BBModel file: " + bbmodelPath, e);
        }

        JsonObject mc = new JsonObject();

        // Get texture dimensions for UV scaling
        int textureWidth = 16;  // Default Minecraft texture size
        int textureHeight = 16;

        // Bilgi alanları - null safe
        if (bb.has("credit") && !bb.get("credit").isJsonNull()) {
            mc.add("credit", bb.get("credit"));
        }

        // texture_size yoksa Blockbench "resolution"dan türet - null safe
        if (bb.has("texture_size") && !bb.get("texture_size").isJsonNull()) {
            JsonArray ts = bb.getAsJsonArray("texture_size");
            if (ts.size() >= 2) {
                textureWidth = ts.get(0).getAsInt();
                textureHeight = ts.get(1).getAsInt();
            }
            mc.add("texture_size", bb.get("texture_size"));
        } else if (bb.has("resolution") && bb.get("resolution").isJsonObject()) {
            JsonObject res = bb.getAsJsonObject("resolution");
            if (res != null && res.has("width") && res.has("height") &&
                    !res.get("width").isJsonNull() && !res.get("height").isJsonNull()) {
                textureWidth = res.get("width").getAsInt();
                textureHeight = res.get("height").getAsInt();
                JsonArray ts = new JsonArray();
                ts.add(textureWidth);
                ts.add(textureHeight);
                mc.add("texture_size", ts);
            }
        }

        // ---- 1) Textures + PNG çıkarma ----
        JsonObject texturesJson = new JsonObject();
        Map<Integer, String> textureKeyMap = new HashMap<>();

        // Ensure the textures directory exists
        Path texturesDir = Path.of(texturesRoot);
        Files.createDirectories(texturesDir);

        JsonElement texEl = bb.get("textures");
        if (texEl != null && !texEl.isJsonNull() && texEl.isJsonArray()) {
            JsonArray bbTextures = texEl.getAsJsonArray();
            for (int i = 0; i < bbTextures.size(); i++) {
                JsonElement textureElement = bbTextures.get(i);
                if (textureElement == null || textureElement.isJsonNull() || !textureElement.isJsonObject()) {
                    continue; // Skip null or invalid texture entries
                }

                JsonObject t = textureElement.getAsJsonObject();

                // Try to use original BBModel texture name if available, otherwise fall back to model name
                String pngName;
                String baseName;

                if (t.has("name") && !t.get("name").isJsonNull()) {
                    // Use original BBModel texture name
                    String originalName = t.get("name").getAsString();
                    // DÜZELTME: _png suffix'ini kaldır (gereksiz suffix)
                    if (originalName.endsWith("_png")) {
                        originalName = originalName.substring(0, originalName.length() - 4);
                    }
                    // Clean the name for file system compatibility
                    originalName = originalName.replaceAll("[^a-zA-Z0-9_-]", "_");
                    pngName = originalName + ".png";
                    baseName = originalName;
                } else {
                    // Fallback to model name system
                    if (i == 0) {
                        pngName = modelName + ".png";
                        baseName = modelName;
                    } else {
                        pngName = modelName + "_" + i + ".png";
                        baseName = modelName + "_" + i;
                    }
                }

                // Kaynak paket kuralları: küçük harf, boşluk yok
                pngName = pngName.toLowerCase().replace(' ', '_');
                baseName = baseName.toLowerCase().replace(' ', '_');

                // Eğer base64 PNG varsa yaz
                if (t.has("source") && !t.get("source").isJsonNull()) {
                    String src = t.get("source").getAsString();
                    if (src != null && src.startsWith("data:image/png;base64,")) {
                        try {
                            byte[] image = Base64.getDecoder().decode(src.substring("data:image/png;base64,".length()));
                            Files.write(texturesDir.resolve(pngName), image);

                            // Check if this texture should be animated and create .mcmeta file
                            createAnimationMcmeta(t, texturesDir, pngName);
                        } catch (IllegalArgumentException e) {
                            // Skip invalid base64 data
                        }
                    }
                }

                String mcPath = namespace + ":item/" + baseName;
                texturesJson.addProperty(String.valueOf(i), mcPath);
                textureKeyMap.put(i, String.valueOf(i));
                if (i == 0) {
                    // GUI/particle için ilk dokuyu kullan
                    texturesJson.addProperty("particle", mcPath);
                }
            }
        }
        mc.add("textures", texturesJson);

        // ---- 2) Elements ----
        JsonArray outElems = new JsonArray();
        JsonElement elementsElement = bb.get("elements");
        if (elementsElement != null && !elementsElement.isJsonNull() && elementsElement.isJsonArray()) {
            JsonArray bbElems = elementsElement.getAsJsonArray();
            for (JsonElement e : bbElems) {
                if (e == null || e.isJsonNull() || !e.isJsonObject()) {
                    continue; // Skip null or invalid elements
                }

                JsonObject bbElem = e.getAsJsonObject();
                JsonObject mcElem = new JsonObject();

                // Geometri
                if (bbElem.has("from") && !bbElem.get("from").isJsonNull()) mcElem.add("from", bbElem.get("from"));
                if (bbElem.has("to") && !bbElem.get("to").isJsonNull())   mcElem.add("to", bbElem.get("to"));

                // Rotation: [x,y,z] + origin -> MC {angle, axis, origin}
                if (bbElem.has("rotation") && !bbElem.get("rotation").isJsonNull() &&
                        bbElem.get("rotation").isJsonArray() && bbElem.has("origin") &&
                        !bbElem.get("origin").isJsonNull()) {
                    JsonArray r = bbElem.getAsJsonArray("rotation");
                    int axisIdx = -1;
                    double angle = 0;
                    for (int i = 0; i < r.size(); i++) {
                        JsonElement rotElement = r.get(i);
                        if (rotElement != null && !rotElement.isJsonNull()) {
                            double v = rotElement.getAsDouble();
                            if (v != 0) { axisIdx = i; angle = v; break; }
                        }
                    }
                    if (axisIdx != -1) {
                        JsonObject rot = new JsonObject();
                        rot.addProperty("angle", angle);
                        rot.addProperty("axis", axisIdx == 0 ? "x" : axisIdx == 1 ? "y" : "z");
                        rot.add("origin", bbElem.get("origin"));
                        mcElem.add("rotation", rot);
                    }
                }

                // Faces
                if (bbElem.has("faces") && !bbElem.get("faces").isJsonNull() && bbElem.get("faces").isJsonObject()) {
                    JsonObject facesIn = bbElem.getAsJsonObject("faces");
                    JsonObject facesOut = new JsonObject();

                    for (Map.Entry<String, JsonElement> fe : facesIn.entrySet()) {
                        String faceKey = fe.getKey();
                        JsonElement faceElement = fe.getValue();
                        if (faceElement == null || faceElement.isJsonNull() || !faceElement.isJsonObject()) {
                            continue; // Skip null or invalid face entries
                        }

                        JsonObject fIn = faceElement.getAsJsonObject();
                        JsonObject fOut = new JsonObject();

                        if (fIn.has("uv") && !fIn.get("uv").isJsonNull() && fIn.get("uv").isJsonArray()) {
                            fOut.add("uv", normalizeUV(fIn.getAsJsonArray("uv"), textureWidth, textureHeight));
                        }

                        if (fIn.has("texture") && !fIn.get("texture").isJsonNull()) {
                            String ref;
                            JsonElement t = fIn.get("texture");
                            if (t.isJsonPrimitive() && t.getAsJsonPrimitive().isString()) {
                                String s = t.getAsString();          // "#1" ya da "1"
                                ref = s.startsWith("#") ? s : "#" + s;
                            } else if (t.isJsonPrimitive() && t.getAsJsonPrimitive().isNumber()) {
                                int idx = t.getAsInt();
                                ref = "#" + textureKeyMap.getOrDefault(idx, "0");
                            } else {
                                ref = "#0"; // Default fallback
                            }
                            fOut.addProperty("texture", ref);
                        } else {
                            // ✅ CRITICAL FIX: Always add texture reference, never leave null
                            // Minecraft JSON parser cannot handle null texture values
                            fOut.addProperty("texture", "#0"); // Default texture for faces without explicit texture
                        }

                        // Blockbench bazen "tint" ya da "tintindex" kullanabiliyor
                        if (fIn.has("tintindex") && !fIn.get("tintindex").isJsonNull()) {
                            fOut.add("tintindex", fIn.get("tintindex"));
                        } else if (fIn.has("tint") && !fIn.get("tint").isJsonNull()) {
                            fOut.add("tintindex", fIn.get("tint"));
                        }

                        facesOut.add(faceKey, fOut);
                    }
                    mcElem.add("faces", facesOut);
                }

                outElems.add(mcElem);
            }
        }
        mc.add("elements", outElems);

        if (bb.has("display") && !bb.get("display").isJsonNull()) mc.add("display", bb.get("display"));



        try (FileWriter w = new FileWriter(mcjsonPath)) {
            gson.toJson(mc, w);
        } catch (IOException e) {
            throw e;
        }

        // BBModel conversion complete
    }

    /**
     * Creates .mcmeta file for animated textures based on BBModel texture properties
     */
    private static void createAnimationMcmeta(JsonObject textureObj, Path texturesDir, String pngName) {
        try {
            // Check if texture has animation properties
            boolean shouldAnimate = false;
            JsonObject animationConfig = new JsonObject();

            // Check for common animation indicators in texture name or properties
            String textureName = pngName.toLowerCase();
            if (textureName.contains("animated") || textureName.contains("anim") ||
                    textureName.contains("_1") || textureName.contains("_2")) {
                shouldAnimate = true;
            }

            // Check BBModel texture properties for animation data
            if (textureObj.has("animated") && textureObj.get("animated").getAsBoolean()) {
                shouldAnimate = true;
            }

            if (textureObj.has("animation") && textureObj.get("animation").isJsonObject()) {
                shouldAnimate = true;
                JsonObject bbAnimation = textureObj.getAsJsonObject("animation");

                // Copy animation properties from BBModel to Minecraft format
                if (bbAnimation.has("frametime")) {
                    animationConfig.addProperty("frametime", bbAnimation.get("frametime").getAsInt());
                }
                if (bbAnimation.has("interpolate")) {
                    animationConfig.addProperty("interpolate", bbAnimation.get("interpolate").getAsBoolean());
                }
                if (bbAnimation.has("frames") && bbAnimation.get("frames").isJsonArray()) {
                    animationConfig.add("frames", bbAnimation.get("frames"));
                }
            }

            // If no specific animation config found, use default settings for animated textures
            if (shouldAnimate && animationConfig.size() == 0) {
                // Default animation settings for textures that appear to be animated
                animationConfig.addProperty("frametime", 2); // 2 ticks per frame (0.1 seconds)
                animationConfig.addProperty("interpolate", false);
            }

            // Create .mcmeta file if animation is needed
            if (shouldAnimate) {
                JsonObject mcmeta = new JsonObject();
                mcmeta.add("animation", animationConfig);

                String mcmetaName = pngName.replace(".png", ".png.mcmeta");
                Path mcmetaPath = texturesDir.resolve(mcmetaName);

                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                try (FileWriter writer = new FileWriter(mcmetaPath.toFile())) {
                    gson.toJson(mcmeta, writer);
                }
            }
        } catch (Exception e) {
            // Silently ignore animation mcmeta creation errors
        }
    }
}