package gg.bckd00r.community.plugin.HMCCosmeticsRP.pack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import gg.bckd00r.community.plugin.HMCCosmeticsRP.HMCCosmeticsPackPlugin;

import java.io.File;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class PackUtils {

    public static void generatePackMcmeta() {
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

            File outputDir = new File(HMCCosmeticsPackPlugin.getInstance().getDataFolder(), "output/" + HMCCosmeticsPackPlugin.getInstance().getConfigManager().getResourcePackId());
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

    private static void copyPackIcon(File outputDir) {
        try {
            java.io.InputStream packIconStream = HMCCosmeticsPackPlugin.getInstance().getResource("pack.png");
            if (packIconStream == null) {
                return;
            }

            File packIconFile = new File(outputDir, "pack.png");
            Files.copy(packIconStream, packIconFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            packIconStream.close();

        } catch (Exception e) {
        }
    }

    /**
     * Copies the generated resource pack to a custom external path if specified in the config
     */
    public static void copyResourcePackToExternalPath() {
        HMCCosmeticsPackPlugin plugin = HMCCosmeticsPackPlugin.getInstance();
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
                gg.bckd00r.community.plugin.HMCCosmeticsRP.util.FileUtils.deleteDirectory(targetDir);
            }
            
            gg.bckd00r.community.plugin.HMCCosmeticsRP.util.FileUtils.copyDirectory(sourceDir, targetDir);
            
        } catch (Exception e) {
        }
    }

}
