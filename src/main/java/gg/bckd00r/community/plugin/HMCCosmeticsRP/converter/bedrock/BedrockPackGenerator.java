package gg.bckd00r.community.plugin.HMCCosmeticsRP.converter.bedrock;

import com.google.gson.*;
import org.bukkit.plugin.Plugin;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;

public class BedrockPackGenerator {
    private static final double EPS = 1e-6;                  // Zero-area UV tolerance

    // === UV yardımcıları ===
    private static int rnd(double x) { return (int)Math.round(x); }

    private static class TexInfo {
        final int texW, texH, uvW, uvH;
        TexInfo(int texW, int texH, int uvW, int uvH) {
            this.texW = texW; this.texH = texH; this.uvW = uvW; this.uvH = uvH;
        }
    }

    private TexInfo readTexInfo(JsonObject bbModel) {
        int texW = 16, texH = 16, uvW = 16, uvH = 16;

        if (bbModel.has("textures") && bbModel.get("textures").isJsonArray()) {
            JsonArray textures = bbModel.getAsJsonArray("textures");
            if (textures.size() > 0 && textures.get(0).isJsonObject()) {
                JsonObject t0 = textures.get(0).getAsJsonObject();
                if (t0.has("width"))  texW = t0.get("width").getAsInt();
                if (t0.has("height")) texH = t0.get("height").getAsInt();
                if (t0.has("uv_width"))  uvW = t0.get("uv_width").getAsInt();
                if (t0.has("uv_height")) uvH = t0.get("uv_height").getAsInt();
            }
        } else if (bbModel.has("resolution")) {
            JsonObject res = bbModel.getAsJsonObject("resolution");
            if (res.has("width"))  uvW = res.get("width").getAsInt();
            if (res.has("height")) uvH = res.get("height").getAsInt();
            // texture gerçek piksel boyutunu bilmiyorsak UV grid'e eşitle
            texW = uvW; texH = uvH;
        }

        // Güvenlik: sıfır olmasın
        if (uvW <= 0) uvW = 16;
        if (uvH <= 0) uvH = 16;

        return new TexInfo(texW, texH, uvW, uvH);
    }

    /** [u1,v1,u2,v2] → (uv, uv_size) + ölçek + normalize (hep pozitif size) + integer snap */
    private void rectToUv(JsonObject faceUVOut, double u1, double v1, double u2, double v2,
                          double scaleU, double scaleV) {
        double du = u2 - u1;
        double dv = v2 - v1;

        // ölçekle
        double U = u1 * scaleU;
        double V = v1 * scaleV;
        double DU = du * scaleU;
        double DV = dv * scaleV;

        // negatif size varsa normalize: başlangıcı kaydır, mutlak al
        if (DU < 0) { U += DU; DU = -DU; }
        if (DV < 0) { V += DV; DV = -DV; }

        JsonArray uv = new JsonArray();
        uv.add(rnd(U)); uv.add(rnd(V));

        JsonArray uvSize = new JsonArray();
        uvSize.add(rnd(DU)); uvSize.add(rnd(DV));

        faceUVOut.add("uv", uv);
        faceUVOut.add("uv_size", uvSize);
    }

    private final Plugin plugin;
    private final Gson gson;

    public BedrockPackGenerator(Plugin plugin) {
        this.plugin = plugin;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    /** Ana giriş: BBModel'den tam bir Bedrock Resource Pack üretir */
    public void generateBedrockPack(String bbmodelPath, String outputPath, String packName) throws IOException {
        File bbmodelFile = new File(bbmodelPath);
        if (!bbmodelFile.exists()) {
            throw new FileNotFoundException("BBModel file not found: " + bbmodelPath);
        }

        JsonObject bbModel = parseBBModel(bbmodelFile);

        // Model adı sanitize
        String rawName = bbmodelFile.getName().replaceAll("\\.bbmodel$", "");
        String modelName = sanitizeName(rawName);
        String packDirName = sanitizeName(packName) + "_bedrock";

        // Klasör yapısı
        File packDir = new File(outputPath, packDirName);
        createBedrockDirectoryStructure(packDir);

        // manifest.json
        generateManifest(packDir, packName);

        // geometry
        generateBedrockGeometry(bbModel, packDir, modelName);

        // textures - önce texture'ları extract et ve texture adını al
        String actualTextureName = extractTextures(bbModel, packDir, modelName);

        // attachable - gerçek texture adını kullan
        generateBedrockAttachable(bbModel, packDir, modelName, actualTextureName);

        plugin.getLogger().info("Bedrock pack generated successfully at: " + packDir.getAbsolutePath());
    }

    private String sanitizeName(String in) {
        String s = in.toLowerCase(Locale.ROOT).replace(' ', '_');
        // harf, rakam, altçizgi dışında her şeyi altçizgi yap
        s = s.replaceAll("[^a-z0-9_]", "_");
        // birden çok altçizgiyi tek altçizgiye indir
        s = s.replaceAll("_+", "_");
        // baş/son altçizgileri kırp
        s = s.replaceAll("^_+|_+$", "");
        if (s.isEmpty())
            s = "model";
        return s;
    }

    /** BBModel JSON oku */
    private JsonObject parseBBModel(File bbmodelFile) throws IOException {
        try (FileReader reader = new FileReader(bbmodelFile)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    /** Pack klasör yapısı */
    private void createBedrockDirectoryStructure(File packDir) throws IOException {
        Files.createDirectories(packDir.toPath());
        Files.createDirectories(Paths.get(packDir.getAbsolutePath(), "models", "entity"));
        Files.createDirectories(Paths.get(packDir.getAbsolutePath(), "attachables"));
        Files.createDirectories(Paths.get(packDir.getAbsolutePath(), "textures", "entity"));
    }

    /** manifest.json üretimi (min_engine_version ARRAY olmalı) */
    private void generateManifest(File packDir, String packName) throws IOException {
        JsonObject manifest = new JsonObject();
        manifest.addProperty("format_version", 2);

        JsonObject header = new JsonObject();
        header.addProperty("description", packName + " Bedrock Resource Pack");
        header.addProperty("name", packName + " Bedrock");
        header.addProperty("uuid", UUID.randomUUID().toString());

        JsonArray version = new JsonArray();
        version.add(1); version.add(0); version.add(0);
        header.add("version", version);

        // DÜZELTME: min_engine_version bir DİZİ olmalı
        JsonArray minEngine = new JsonArray();
        minEngine.add(1); minEngine.add(16); minEngine.add(0);
        header.add("min_engine_version", minEngine);

        JsonArray modules = new JsonArray();
        JsonObject module = new JsonObject();
        module.addProperty("description", "Resource Pack Module");
        module.addProperty("type", "resources");
        module.addProperty("uuid", UUID.randomUUID().toString());
        module.add("version", version.deepCopy());
        modules.add(module);

        manifest.add("header", header);
        manifest.add("modules", modules);

        try (FileWriter writer = new FileWriter(new File(packDir, "manifest.json"))) {
            gson.toJson(manifest, writer);
        }
    }

    /** Bedrock geometry üretimi */
    private void generateBedrockGeometry(JsonObject bbModel, File packDir, String modelName) throws IOException {
        JsonObject geometryRoot = new JsonObject();
        geometryRoot.addProperty("format_version", "1.12.0");

        JsonArray geometries = new JsonArray();
        JsonObject geometryDef = new JsonObject();

        JsonObject description = new JsonObject();
        description.addProperty("identifier", "geometry." + modelName);

        // Texture boyutu + UV ölçekleri
        TexInfo ti = readTexInfo(bbModel);
        description.addProperty("texture_width", ti.texW);
        description.addProperty("texture_height", ti.texH);
        
        double scaleU = (double) ti.texW / (double) ti.uvW;
        double scaleV = (double) ti.texH / (double) ti.uvH;

        description.addProperty("visible_bounds_width", 3);
        description.addProperty("visible_bounds_height", 2.5);
        JsonArray vbo = new JsonArray();
        vbo.add(0); vbo.add(0.75); vbo.add(0);
        description.add("visible_bounds_offset", vbo);

        geometryDef.add("description", description);

        // Tek kemik: model adını kullan - pivot [5,13,-1] working UFO reference'a göre
        JsonArray bones = new JsonArray();
        JsonObject mainBone = new JsonObject();
        
        // DÜZELTME: _backpack suffix'ini kaldır, sadece temiz model adı
        String cleanModelName = modelName.replaceAll("_backpack$", "");
        mainBone.addProperty("name", cleanModelName);

        // DÜZELTME: Working UFO reference pivot sistemi [5,13,-1]
        JsonArray pivot = new JsonArray();
        pivot.add(5); pivot.add(13); pivot.add(-1);  // Working UFO model pivot
        mainBone.add("pivot", pivot);

        // Elementleri küplere çevir
        JsonArray cubes = new JsonArray();
        if (bbModel.has("elements") && bbModel.get("elements").isJsonArray()) {
            for (JsonElement el : bbModel.getAsJsonArray("elements")) {
                JsonObject element = el.getAsJsonObject();
                if (!element.has("from") || !element.has("to")) continue;

                JsonArray from = element.getAsJsonArray("from");
                JsonArray to   = element.getAsJsonArray("to");

                double fx = from.get(0).getAsDouble();
                double fy = from.get(1).getAsDouble();
                double fz = from.get(2).getAsDouble();

                double tx = to.get(0).getAsDouble();
                double ty = to.get(1).getAsDouble();
                double tz = to.get(2).getAsDouble();

                // YENİ: Bedrock piksel uzayına düz yaz (merkezleme/flipleme yok)
                double minX = Math.min(fx, tx);
                double minY = Math.min(fy, ty);
                double minZ = Math.min(fz, tz);

                double sx = Math.abs(tx - fx);
                double sy = Math.abs(ty - fy);
                double sz = Math.abs(tz - fz);

                JsonObject cube = new JsonObject();
                JsonArray origin = new JsonArray(); 
                origin.add(minX); origin.add(minY); origin.add(minZ);
                cube.add("origin", origin);

                JsonArray size = new JsonArray(); 
                size.add(sx); size.add(sy); size.add(sz);
                cube.add("size", size);

                // Rotation & pivot (varsa)
                if (element.has("rotation") && element.get("rotation").isJsonArray()) {
                    JsonArray rot = element.getAsJsonArray("rotation");
                    if (rot.size() == 3) {
                        JsonArray rotOut = new JsonArray();
                        rotOut.add(rot.get(0)); rotOut.add(rot.get(1)); rotOut.add(rot.get(2));
                        cube.add("rotation", rotOut);

                        // BBModel element origin'i rotasyon pivotu - Z flip ile
                        if (element.has("origin") && element.get("origin").isJsonArray()) {
                            JsonArray ori = element.getAsJsonArray("origin");
                            if (ori.size() == 3) {
                                JsonArray piv = new JsonArray();
                                double px = ori.get(0).getAsDouble() - 8.0; // X merkezleme
                                double py = ori.get(1).getAsDouble();        // Y aynen
                                double pz = -ori.get(2).getAsDouble();       // Z flip
                                piv.add(px); piv.add(py); piv.add(pz);
                                cube.add("pivot", piv);
                            }
                        }
                    }
                }

                // Faces → UV (temizlenmiş versiyon)
                if (element.has("faces") && element.get("faces").isJsonObject()) {
                    JsonObject faces = element.getAsJsonObject("faces");
                    JsonObject uvObj = new JsonObject();

                    for (Map.Entry<String, JsonElement> faceEntry : faces.entrySet()) {
                        String faceKey = faceEntry.getKey(); // north/east/south/west/up/down
                        JsonObject face = faceEntry.getValue().getAsJsonObject();

                        // texture null ise atla
                        if (face.has("texture") && face.get("texture").isJsonNull()) continue;

                        if (!face.has("uv")) continue;
                        JsonArray uvArr = face.getAsJsonArray("uv");
                        if (uvArr.size() < 4) continue; // sadeleştirme: 4'lü bekliyoruz

                        double u1 = uvArr.get(0).getAsDouble();
                        double v1 = uvArr.get(1).getAsDouble();
                        double u2 = uvArr.get(2).getAsDouble();
                        double v2 = uvArr.get(3).getAsDouble();

                        double du = u2 - u1;
                        double dv = v2 - v1;
                        if (Math.abs(du) < EPS || Math.abs(dv) < EPS) continue; // zero-area

                        JsonObject faceUVOut = new JsonObject();
                        rectToUv(faceUVOut, u1, v1, u2, v2, scaleU, scaleV);

                        uvObj.add(faceKey, faceUVOut);
                    }
                    if (!uvObj.entrySet().isEmpty()) {
                        cube.add("uv", uvObj);
                    }
                }

                cubes.add(cube);
            }
        }

        mainBone.add("cubes", cubes);
        bones.add(mainBone);
        geometryDef.add("bones", bones);

        // Item display transforms (senin kullandığın değerlerle)
        JsonObject itemDisplayTransforms = new JsonObject();

        itemDisplayTransforms.add("thirdperson_righthand", obj("rotation", arr(75,45,0),
                "translation", arr(0,1,0), "scale", arr(0.375,0.375,0.375),
                "rotation_pivot", arr(0,0,0), "scale_pivot", arr(0,0,0)));

        itemDisplayTransforms.add("thirdperson_lefthand", obj("rotation", arr(75,45,0),
                "translation", arr(0,2.5,0), "scale", arr(0.375,0.375,0.375),
                "rotation_pivot", arr(0,0,0), "scale_pivot", arr(0,0,0)));

        itemDisplayTransforms.add("firstperson_righthand", obj("rotation", arr(0,45,0),
                "translation", arr(3.25,0,0), "scale", arr(0.4,0.4,0.4),
                "rotation_pivot", arr(0,0,0), "scale_pivot", arr(0,0,0)));

        itemDisplayTransforms.add("firstperson_lefthand", obj("rotation", arr(0,-135,0),
                "translation", arr(-1.75,1.25,0), "scale", arr(0.4,0.4,0.4),
                "rotation_pivot", arr(0,0,0), "scale_pivot", arr(0,0,0)));

        itemDisplayTransforms.add("ground", obj("rotation", arr(0,0,0),
                "translation", arr(0,3,0), "scale", arr(0.25,0.25,0.25),
                "rotation_pivot", arr(0,0,0), "scale_pivot", arr(0,0,0)));

        JsonObject gui = obj("rotation", arr(30,-135,0),
                "translation", arr(-3.25,0,0), "scale", arr(0.5,0.5,0.5),
                "rotation_pivot", arr(0,0,0), "scale_pivot", arr(0,0,0));
        gui.addProperty("fit_to_frame", false);
        itemDisplayTransforms.add("gui", gui);

        itemDisplayTransforms.add("head", obj("rotation", arr(0,0,0),
                "translation", arr(0,-11.5,25.75), "scale", arr(1.63,1.63,1.63),
                "rotation_pivot", arr(0,0,0), "scale_pivot", arr(0,0,0)));

        itemDisplayTransforms.add("fixed", obj("rotation", arr(0,0,0),
                "translation", arr(0,0,0), "scale", arr(0.5,0.5,0.5),
                "rotation_pivot", arr(0,0,0), "scale_pivot", arr(0,0,0)));

        geometryDef.add("item_display_transforms", itemDisplayTransforms);

        geometries.add(geometryDef);
        geometryRoot.add("minecraft:geometry", geometries);

        File geometryFile = new File(packDir, "models/entity/" + modelName + ".geo.json");
        try (FileWriter writer = new FileWriter(geometryFile)) {
            gson.toJson(geometryRoot, writer);
        }
    }

    private static JsonObject obj(Object... kv) {
        JsonObject o = new JsonObject();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            String k = (String) kv[i];
            JsonElement v = (JsonElement) kv[i + 1];
            o.add(k, v);
        }
        return o;
    }
    private static JsonArray arr(double a, double b, double c) {
        JsonArray ar = new JsonArray();
        ar.add(a); ar.add(b); ar.add(c);
        return ar;
    }

    /** Tek bone altında küpler — (Kullanmıyorum; yukarıda inline yaptım) */
    @Deprecated
    private JsonArray convertElementsToBones(JsonArray elements) {
        // Eski yaklaşım gerekirse burada tutuluyor.
        return new JsonArray();
    }

    /** Attachable üretimi */
    private void generateBedrockAttachable(JsonObject bbModel, File packDir, String modelName, String actualTextureName) throws IOException {
        JsonObject attachable = new JsonObject();
        attachable.addProperty("format_version", "1.10.0");

        JsonObject attachableDef = new JsonObject();
        JsonObject description = new JsonObject();

        // identifier & item: namespace vermek istersen "hmc:" vb. ekleyebilirsin
        description.addProperty("identifier", modelName);
        description.addProperty("item", modelName);
        description.addProperty("category", "wearable");

        JsonObject materials = new JsonObject();
        materials.addProperty("default", "entity_alphatest");
        description.add("materials", materials);

        JsonObject textures = new JsonObject();
        textures.addProperty("default", "textures/entity/" + actualTextureName);
        description.add("textures", textures);

        JsonObject geometry = new JsonObject();
        geometry.addProperty("default", "geometry." + modelName);
        description.add("geometry", geometry);

        JsonArray scripts = new JsonArray();
        JsonObject script = new JsonObject();
        script.addProperty("parent_setup", "variable.helmet_layer_visible = 0.0;");
        scripts.add(script);
        description.add("scripts", scripts);

        JsonObject renderControllers = new JsonObject();
        JsonArray controllers = new JsonArray();
        controllers.add("controller.render.item_default");
        renderControllers.add("default", controllers);
        description.add("render_controllers", renderControllers);

        attachableDef.add("description", description);
        attachable.add("minecraft:attachable", attachableDef);

        try (FileWriter writer = new FileWriter(new File(packDir, "attachables/" + modelName + ".json"))) {
            gson.toJson(attachable, writer);
        }
    }

    /** BBModel içindeki base64 texture’ları çıkarır */
    private String extractTextures(JsonObject bbModel, File packDir, String modelName) throws IOException {
        if (!bbModel.has("textures") || !bbModel.get("textures").isJsonArray()) return modelName;

        JsonArray textures = bbModel.getAsJsonArray("textures");
        File texturesDir = new File(packDir, "textures/entity");

        // DÜZELTME: Her texture için benzersiz isim kullan - çoklu texture desteği
        for (int i = 0; i < textures.size(); i++) {
            JsonElement textureElement = textures.get(i);
            if (!textureElement.isJsonObject()) continue;
            JsonObject texture = textureElement.getAsJsonObject();

            // Java pack gibi basit yaklaşım: model adını kullan
            String textureName;
            if (i == 0) {
                textureName = modelName;
            } else {
                textureName = modelName + "_" + i;
            }

            if (texture.has("source")) {
                String source = texture.get("source").getAsString();

                if (source.startsWith("data:image/png;base64,")) {
                    String base64Data = source.substring("data:image/png;base64,".length());
                    byte[] imageBytes = Base64.getDecoder().decode(base64Data);

                    // DÜZELTME: Benzersiz dosya adları
                    File textureFile = new File(texturesDir, textureName + ".png");
                    try (FileOutputStream fos = new FileOutputStream(textureFile)) {
                        fos.write(imageBytes);
                    }
                    
                    plugin.getLogger().info("Bedrock texture extracted: " + textureName + ".png");
                }
                // İleride: base64 değil de dosya yolu gelirse, projedeki yoldan kopyalama eklenebilir.
            }
        }
        
        // Java pack gibi basit yaklaşım: model adını return et
        return modelName;
    }

    /** Pack’ı dışarı kopyala */
    public void sendBedrockPack(String packPath, String transferPath) throws IOException {
        File packDir = new File(packPath);
        File transferDir = new File(transferPath);

        if (!packDir.exists()) {
            throw new FileNotFoundException("Bedrock pack not found: " + packPath);
        }
        if (!transferDir.exists()) {
            Files.createDirectories(transferDir.toPath());
        }

        copyDirectory(packDir.toPath(), transferDir.toPath().resolve(packDir.getName()));
        plugin.getLogger().info("Bedrock pack sent to: " + transferDir.getAbsolutePath());
    }

    /** Klasör kopyalama */
    private void copyDirectory(Path source, Path target) throws IOException {
        Files.walk(source).forEach(sourcePath -> {
            try {
                Path targetPath = target.resolve(source.relativize(sourcePath));
                if (Files.isDirectory(sourcePath)) {
                    Files.createDirectories(targetPath);
                } else {
                    Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }
}
