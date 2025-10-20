package gg.bckd00r.community.plugin.HMCCosmeticsRP.util;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

public class FileUtils {
    

    public static void copyDirectory(File source, File target) throws IOException {
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
    
    /**
     * Deletes a directory recursively
     */
    public static boolean deleteDirectory(File directory) {
        if (directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    deleteDirectory(file);
                }
            }
        }
        return directory.delete();
    }
}
