package gg.bckd00r.community.plugin.HMCCosmeticsRP.util;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

public class FileUtils {
    
    /**
     * Copies a directory and all its contents to a target directory
     * @param source The source directory to copy
     * @param target The target directory to copy to
     * @throws IOException If an I/O error occurs
     */
    public static void copyDirectory(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path targetDir = target.resolve(source.relativize(dir));
                Files.createDirectories(targetDir);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.copy(file, target.resolve(source.relativize(file)), 
                         StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }
    
    /**
     * Copies the generated pack to a custom output path if specified in the config
     * @param plugin The plugin instance
     * @param packId The ID of the resource pack
     * @return true if the copy was successful, false otherwise
     */
    public static boolean copyPackToCustomPath(JavaPlugin plugin, String packId) {
        try {
            File outputDir = new File(plugin.getDataFolder(), "output/" + packId);
            if (!outputDir.exists()) {
                plugin.getLogger().warning("Output directory does not exist: " + outputDir.getAbsolutePath());
                return false;
            }
            
            String customPath = plugin.getConfig().getString("output.custom-path");
            if (customPath == null || customPath.isEmpty()) {
                return false; // No custom path specified
            }
            
            Path sourcePath = outputDir.toPath();
            Path targetPath = Paths.get(customPath, packId);
            
            // Create parent directories if they don't exist
            Files.createDirectories(targetPath.getParent());
            
            // Copy the directory
            copyDirectory(sourcePath, targetPath);
            
            plugin.getLogger().info("Successfully copied pack to: " + targetPath);
            return true;
            
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to copy pack to custom path: " + e.getMessage());
            return false;
        }
    }
}
