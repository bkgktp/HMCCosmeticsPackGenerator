package gg.bckd00r.community.plugin.HMCCosmeticsRP.util;

import gg.bckd00r.community.plugin.HMCCosmeticsRP.HMCCosmeticsPackPlugin;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

public class Utils {

    /**
     * Sends a debug message to a command sender if debug mode is enabled
     * @param sender The command sender to send the message to
     * @param message The message to send
     */
    public static void debugMessage(CommandSender sender, String message) {
        HMCCosmeticsPackPlugin plugin = HMCCosmeticsPackPlugin.getInstance();
        if (plugin.getConfigManager().isDebugMode()) {
            sender.sendMessage(message);
        }
    }

    /**
     * Sends a debug message to a command sender if debug mode is enabled
     * @param sender The command sender to send the message to
     * @param prefix The prefix for the message
     * @param message The message to send
     */
    public static void debugMessage(CommandSender sender, String prefix, String message) {
        HMCCosmeticsPackPlugin plugin = HMCCosmeticsPackPlugin.getInstance();
        if (plugin.getConfigManager().isDebugMode()) {
            sender.sendMessage(prefix + message);
        }
    }
}
