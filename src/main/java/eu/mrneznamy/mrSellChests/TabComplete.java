package eu.mrneznamy.mrSellChests;

import eu.mrneznamy.mrSellChests.MrSellChests;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

public class TabComplete
implements TabCompleter {
    private final MrSellChests plugin;

    public TabComplete(MrSellChests plugin) {
        this.plugin = plugin;
    }

    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        ArrayList<String> completions = new ArrayList<String>();
        if (args.length == 1) {
            if (sender.hasPermission("mrsellchests.give")) {
                completions.add("give");
            }
            if (sender.hasPermission("mrsellchests.reload")) {
                completions.add("reload");
            }
            if (sender.hasPermission("mrsellchests.boost")) {
                completions.add("boost");
            }
            if (sender.hasPermission("mrsellchests.banned")) {
                completions.add("banned");
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            if (sender.hasPermission("mrsellchests.give")) {
                return Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList());
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            if (sender.hasPermission("mrsellchests.give")) {
                FileConfiguration config = this.plugin.getConfig();
                ConfigurationSection sellChests = config.getConfigurationSection("MrSellChests.SellChests");
                if (sellChests == null) {
                    sellChests = config.getConfigurationSection("SellChests");
                }
                if (sellChests != null) {
                    completions.addAll(sellChests.getKeys(false));
                }
            }
        } else if (args.length == 4 && args[0].equalsIgnoreCase("give")) {
            if (sender.hasPermission("mrsellchests.give")) {
                completions.add("1");
                completions.add("5");
                completions.add("10");
                completions.add("32");
                completions.add("64");
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("boost")) {
            if (sender.hasPermission("mrsellchests.boost")) {
                completions.add("give");
                completions.addAll(Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()));
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("boost") && args[1].equalsIgnoreCase("give")) {
            if (sender.hasPermission("mrsellchests.boost")) {
                return Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList());
            }
        } else if (args.length == 4 && args[0].equalsIgnoreCase("boost") && args[1].equalsIgnoreCase("give")) {
            YamlConfiguration boosters;
            File boostersFile;
            if (sender.hasPermission("mrsellchests.boost") && (boostersFile = new File(this.plugin.getDataFolder(), "boosters.yml")).exists() && (boosters = YamlConfiguration.loadConfiguration((File)boostersFile)).isConfigurationSection("Boosters")) {
                completions.addAll(boosters.getConfigurationSection("Boosters").getKeys(false));
            }
        } else if (args.length == 5 && args[0].equalsIgnoreCase("boost") && args[1].equalsIgnoreCase("give")) {
            if (sender.hasPermission("mrsellchests.boost")) {
                completions.add("1");
                completions.add("5");
                completions.add("10");
                completions.add("32");
                completions.add("64");
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("boost")) {
            if (sender.hasPermission("mrsellchests.boost")) {
                completions.add("1.5");
                completions.add("2.0");
                completions.add("3.0");
            }
        } else if (args.length == 4 && args[0].equalsIgnoreCase("boost") && sender.hasPermission("mrsellchests.boost")) {
            completions.add("60");
            completions.add("300");
            completions.add("600");
            completions.add("3600");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("banned")) {
            if (sender.hasPermission("mrsellchests.banned")) {
                completions.add("SELL");
                completions.add("COLLECT");
                completions.add("INV");
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("banned") && sender.hasPermission("mrsellchests.banned")) {
            completions.add("add");
            completions.add("remove");
        }
        return completions.stream().filter(completion -> completion.toLowerCase().startsWith(args[args.length - 1].toLowerCase())).collect(Collectors.toList());
    }
}

