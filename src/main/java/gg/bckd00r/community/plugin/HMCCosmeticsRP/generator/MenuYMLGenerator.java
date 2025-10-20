package gg.bckd00r.community.plugin.HMCCosmeticsRP.generator;

import gg.bckd00r.community.plugin.HMCCosmeticsRP.HMCCosmeticsPackPlugin;
import gg.bckd00r.community.plugin.HMCCosmeticsRP.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class MenuYMLGenerator {
    private final HMCCosmeticsPackPlugin plugin;
    private final String namespace;
    private final Set<String> validCosmeticTypes;
    private final Plugin hmcPlugin;
    private final File tempDir;

    // Type to menu name mapping
    private final Map<String, String> typeToMenuName = Map.of(
        "helmet", "helmets",
        "backpack", "backpacks",
        "offhand", "offhands",
        "balloon", "balloons"
    );

    // Menu button configuration: menu name -> (slot, model-data)
    private final Map<String, int[]> menuButtonConfig = Map.of(
        "helmets", new int[]{36, 1013},
        "backpacks", new int[]{0, 1014},
        "offhands", new int[]{18, 1015},
        "balloons", new int[]{27, 1016}
    );

    public MenuYMLGenerator(HMCCosmeticsPackPlugin plugin, String namespace, Set<String> validCosmeticTypes) {
        this.plugin = plugin;
        this.namespace = namespace.toLowerCase();
        this.validCosmeticTypes = validCosmeticTypes;
        this.tempDir = new File(plugin.getDataFolder(), "temp/" + namespace + "_menus");
        this.hmcPlugin = plugin.getServer().getPluginManager().getPlugin("HMCCosmetics");

        if (!tempDir.exists() && !tempDir.mkdirs()) {
            plugin.getLogger().warning("Failed to create temp directory: " + tempDir.getAbsolutePath());
        }
    }

    /**
     * Generates menu YML files for cosmetics
     * @param cosmeticsByType Map of cosmetic types to their item names
     * @return true if successful, false otherwise
     */
    public boolean generateMenuYMLFiles(Map<String, List<String>> cosmeticsByType) {
        try {
            // Clean temp directory
            if (tempDir.exists()) {
                deleteDirectory(tempDir);
            }
            if (!tempDir.mkdirs()) {
                plugin.getLogger().warning("Failed to create temp directory: " + tempDir.getAbsolutePath());
                return false;
            }

            // Generate menu files for each cosmetic type
            for (Map.Entry<String, String> typeEntry : typeToMenuName.entrySet()) {
                String type = typeEntry.getKey();
                String menuName = typeEntry.getValue();

                if (validCosmeticTypes.contains(type)) {
                    List<String> cosmetics = cosmeticsByType.getOrDefault(type, new ArrayList<>());
                    generateMenuFile(menuName, cosmetics);
                }
            }

            // Check if any files were created
            File[] menuFiles = tempDir.listFiles((dir, name) -> name.endsWith(".yml"));
            if (menuFiles != null && menuFiles.length > 0) {
                Utils.debugMessage(Bukkit.getConsoleSender(),"✓ Generated " + menuFiles.length + " menu files in temp directory");
                return true;
            } else {
                plugin.getLogger().warning("No menu files were generated");
                return false;
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to generate menu files: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Generates a single menu file for a cosmetic type
     */
    private void generateMenuFile(String menuName, List<String> cosmetics) {
        String menuFileName = menuName + ".yml";
        File menuFile = new File(tempDir, menuFileName);
        FileConfiguration config = new YamlConfiguration();

        // Part 1: Menu title and size
        config.set("title", "<white><shift:-8><glyph:cosmetics_gui>");
        config.set("rows", 6);

        // Part 2 & 3: All items go under "items:" section
        // Add navigation buttons
        addNavigationButtons(config, menuName);

        // Add cosmetic items
        addCosmeticItems(config, cosmetics);

        // Save the file
        try {
            config.save(menuFile);
            Utils.debugMessage(Bukkit.getConsoleSender(),"Generated menu file: " + menuFileName);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save menu file " + menuFileName + ": " + e.getMessage());
        }
    }

    /**
     * Adds navigation buttons to the menu (part 2)
     */
    private void addNavigationButtons(FileConfiguration config, String currentMenu) {
        for (Map.Entry<String, int[]> entry : menuButtonConfig.entrySet()) {
            String menuName = entry.getKey();
            int[] config_data = entry.getValue();
            int slot = config_data[0];
            int modelData = config_data[1];

            String buttonKey = "items.button_" + menuName + "_menu";

            config.set(buttonKey + ".slots", Collections.singletonList(slot));
            config.set(buttonKey + ".item.material", "PAPER");
            config.set(buttonKey + ".item.model-data", modelData);
            config.set(buttonKey + ".item.name", "<blue>" + capitalizeWords(menuName) + " Cosmetics");
            config.set(buttonKey + ".item.amount", 1);
            config.set(buttonKey + ".type", "empty");

            List<String> actions = new ArrayList<>();
            actions.add("[MENU] " + menuName);
            actions.add("[SOUND] minecraft:ui.button.click 1 1");
            config.set(buttonKey + ".actions.any", actions);
        }
    }

    /**
     * Adds cosmetic items to the menu (part 3)
     */
    private void addCosmeticItems(FileConfiguration config, List<String> cosmetics) {
        List<Integer> availableSlots = getAvailableSlots();
        
        Utils.debugMessage(Bukkit.getConsoleSender(),"Adding " + cosmetics.size() + " cosmetics to menu (available slots: " + availableSlots.size() + ")");
        
        for (int i = 0; i < cosmetics.size() && i < availableSlots.size(); i++) {
            String cosmeticId = cosmetics.get(i);
            int slot = availableSlots.get(i);

            String itemKey = "items." + cosmeticId;
            
            config.set(itemKey + ".slots", Collections.singletonList(slot));
            config.set(itemKey + ".item.material", "hmccosmetics" + ":" + cosmeticId);
            
            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add("<gray>Enabled: <#6D9DC5>%HMCCosmetics_equipped_" + cosmeticId + "%");
            lore.add("<gray>Allowed: <#6D9DC5>%HMCCosmetics_unlocked_" + cosmeticId + "%");
            
            config.set(itemKey + ".item.lore", lore);
            config.set(itemKey + ".type", "cosmetic");
            config.set(itemKey + ".cosmetic", cosmeticId);
            
            // Add actions to close menu and play sound
            List<String> actions = new ArrayList<>();
            actions.add("[CLOSE]");
            actions.add("[SOUND] minecraft:ui.button.click 1 1");
            config.set(itemKey + ".actions.any", actions);
            
            Utils.debugMessage(Bukkit.getConsoleSender(),"  ✓ Added cosmetic item: " + cosmeticId + " at slot " + slot);
        }
        
        if (cosmetics.size() > availableSlots.size()) {
            plugin.getLogger().warning("⚠ Only " + availableSlots.size() + " slots available, but " + cosmetics.size() + " cosmetics to add");
        }
    }

    /**
     * Gets available slots for cosmetic items
     * Valid ranges: 1-8, 10-17, 19-26, 28-35, 37-44
     */
    private List<Integer> getAvailableSlots() {
        List<Integer> slots = new ArrayList<>();
        
        // Define valid slot ranges (excluding navigation button slots: 0, 9, 18, 27, 36)
        int[][] validRanges = {
            {1, 8},      // Row 0: slots 1-8
            {10, 17},    // Row 1: slots 10-17
            {19, 26},    // Row 2: slots 19-26
            {28, 35},    // Row 3: slots 28-35
            {37, 44}     // Row 4: slots 37-44
        };
        
        // Add all slots in valid ranges
        for (int[] range : validRanges) {
            for (int slot = range[0]; slot <= range[1]; slot++) {
                slots.add(slot);
            }
        }
        
        return slots;
    }

    /**
     * Capitalize each word in a string
     */
    private String capitalizeWords(String str) {
        String[] words = str.split("_");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                result.append(word.substring(0, 1).toUpperCase())
                      .append(word.substring(1).toLowerCase())
                      .append(" ");
            }
        }
        return result.toString().trim();
    }

    /**
     * Deletes a directory recursively
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
}
