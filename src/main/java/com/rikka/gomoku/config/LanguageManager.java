package com.rikka.gomoku.config;

import com.rikka.gomoku.GomokuPlugin;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class LanguageManager {
    private final GomokuPlugin plugin;
    private final Map<String, FileConfiguration> languageCache = new HashMap<>();
    private String currentLanguage = "zh_CN";

    public LanguageManager(GomokuPlugin plugin) {
        this.plugin = plugin;
        loadLanguageFiles();
    }

    private void loadLanguageFiles() {
        File langDir = new File(plugin.getDataFolder(), "Language");
        if (!langDir.exists()) {
            langDir.mkdirs();
            saveDefaultLanguageFile("zh_CN.yml");
            saveDefaultLanguageFile("en_US.yml");
        }
        // Ensure default files exist
        for (String lang : new String[]{"zh_CN.yml", "en_US.yml"}) {
            File f = new File(langDir, lang);
            if (!f.exists()) {
                saveDefaultLanguageFile(lang);
            }
        }

        // Load all language files
        File[] files = langDir.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files != null) {
            for (File f : files) {
                String lang = f.getName().replace(".yml", "");
                FileConfiguration cfg = YamlConfiguration.loadConfiguration(f);
                languageCache.put(lang, cfg);
            }
        }
    }

    private void saveDefaultLanguageFile(String name) {
        try {
            InputStream in = plugin.getResource("Language/" + name);
            if (in != null) {
                File out = new File(plugin.getDataFolder(), "Language/" + name);
                java.nio.file.Files.copy(in, out.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                in.close();
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to save language file: " + name);
        }
    }

    public void reload() {
        languageCache.clear();
        loadLanguageFiles();
    }

    public String get(String key) {
        return get(key, currentLanguage);
    }

    public String get(String key, String language) {
        FileConfiguration cfg = languageCache.get(language);
        if (cfg == null) {
            cfg = languageCache.get("en_US"); // fallback
        }
        if (cfg == null) return "Missing: " + key;

        String value = cfg.getString(key);
        if (value == null) return "Missing: " + key;

        return ChatColor.translateAlternateColorCodes('&', value);
    }

    public String format(String key, Map<String, String> replacements) {
        String msg = get(key);
        if (replacements != null) {
            for (Map.Entry<String, String> entry : replacements.entrySet()) {
                msg = msg.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }
        return msg;
    }

    public String getPrefix() {
        return get("prefix");
    }

    public void setLanguage(String lang) {
        if (languageCache.containsKey(lang)) {
            this.currentLanguage = lang;
        }
    }

    public String getCurrentLanguage() {
        return currentLanguage;
    }
}
