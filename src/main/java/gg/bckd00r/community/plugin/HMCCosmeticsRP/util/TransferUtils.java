package gg.bckd00r.community.plugin.HMCCosmeticsRP.util;

import gg.bckd00r.community.plugin.HMCCosmeticsRP.HMCCosmeticsPackPlugin;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class TransferUtils {

    /**
     * Transfers menu files from temp directory to HMCCosmetics plugin
     */
    public static void transferMenuFilesToHMCCosmetics(HMCCosmeticsPackPlugin plugin, String namespace, Plugin hmcPlugin) {
        if (hmcPlugin == null) {
            plugin.getLogger().warning("HMCCosmetics plugin not found, cannot transfer menu files");
            return;
        }
        
        try {
            File tempMenuDir = new File(plugin.getDataFolder(), "temp/" + namespace + "_menus");
            File targetMenuDir = new File(hmcPlugin.getDataFolder(), "menus/" + namespace + "_menus");
            
            if (!tempMenuDir.exists()) {
                plugin.getLogger().warning("Temp menu directory not found: " + tempMenuDir.getAbsolutePath());
                return;
            }
            
            // Delete existing target directory to replace it completely
            if (targetMenuDir.exists()) {
                FileUtils.deleteDirectory(targetMenuDir);
            }
            
            if (!targetMenuDir.mkdirs()) {
                plugin.getLogger().warning("Failed to create target menu directory: " + targetMenuDir.getAbsolutePath());
                return;
            }
            
            // Copy all menu files from temp to target
            File[] menuFiles = tempMenuDir.listFiles((dir, name) -> name.endsWith(".yml"));
            if (menuFiles != null && menuFiles.length > 0) {
                for (File sourceFile : menuFiles) {
                    File targetFile = new File(targetMenuDir, sourceFile.getName());
                    Files.copy(sourceFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    Utils.debugMessage(Bukkit.getConsoleSender(),"✓ Transferred menu file: " + sourceFile.getName());
                }
                Utils.debugMessage(Bukkit.getConsoleSender(),"✓ All menu files transferred to: " + targetMenuDir.getAbsolutePath());
            } else {
                plugin.getLogger().warning("No menu files found in temp directory");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to transfer menu files: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Transfers cosmetic files from temp directory to HMCCosmetics plugin
     */
    public static void transferCosmeticFilesToHMCCosmetics(HMCCosmeticsPackPlugin plugin, String namespace, Plugin hmcPlugin) {
        if (hmcPlugin == null) {
            return;
        }
        
        try {
            File tempItemDir = new File(plugin.getDataFolder(), "temp/" + namespace + "_items");
            File targetCosmeticsDir = new File(hmcPlugin.getDataFolder(), "cosmetics/" + namespace + "_items");
            
            if (!tempItemDir.exists()) {
                plugin.getLogger().warning("Temp items directory not found: " + tempItemDir.getAbsolutePath());
                return;
            }
            
            // Delete existing target directory to replace it completely
            if (targetCosmeticsDir.exists()) {
                FileUtils.deleteDirectory(targetCosmeticsDir);
            }
            
            if (!targetCosmeticsDir.mkdirs()) {
                plugin.getLogger().warning("Failed to create target cosmetics directory: " + targetCosmeticsDir.getAbsolutePath());
                return;
            }
            
            // Copy all cosmetic files from temp to target
            File[] cosmeticFiles = tempItemDir.listFiles((dir, name) -> name.endsWith(".yml"));
            if (cosmeticFiles != null && cosmeticFiles.length > 0) {
                for (File sourceFile : cosmeticFiles) {
                    File targetFile = new File(targetCosmeticsDir, sourceFile.getName());
                    Files.copy(sourceFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    Utils.debugMessage(Bukkit.getConsoleSender(),"✓ Transferred cosmetic file: " + sourceFile.getName());
                }
                Utils.debugMessage(Bukkit.getConsoleSender(),"✓ All cosmetic files transferred to: " + targetCosmeticsDir.getAbsolutePath());
            } else {
                plugin.getLogger().warning("No cosmetic files found in temp directory");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to transfer cosmetic files: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
