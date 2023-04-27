package com.bluetigeresw.SpigotAutoUpdater;

import java.io.BufferedInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.InvalidDescriptionException;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;

public class AutoUpdater extends JavaPlugin {
    
    private List<String> excludedPlugins;
    private FileConfiguration config;
    
    @Override
    public void onEnable() {
        // Load the configuration file
        saveDefaultConfig();
        config = YamlConfiguration.loadConfiguration(getDataFolder().toPath().resolve("config.yml").toFile());
        excludedPlugins = config.getStringList("excludedPlugins");
        
        // Check for updates on startup
        try {
            checkForUpdates();
        } catch (IOException | InvalidDescriptionException e) {
            e.printStackTrace();
        }
    }
    
    public void checkForUpdates() throws IOException, InvalidDescriptionException {
        Path pluginsFolder = getDataFolder().getParentFile().toPath().resolve("plugins");
        for (Path pluginFile : Files.list(pluginsFolder).filter(path -> path.toString().endsWith(".jar")).toList()) {
            String pluginName = pluginFile.getFileName().toString().substring(0, pluginFile.getFileName().toString().length() - 4);
            if (!excludedPlugins.contains(pluginName)) {
                PluginDescriptionFile description = getPluginDescription(pluginFile);
                String currentVersion = description.getVersion();
                String updateUrl = config.getString("pluginUpdateUrls." + pluginName);
                if (updateUrl != null) {
                    try {
                        URL url = new URL(updateUrl);
                        URLConnection connection = url.openConnection();
                        connection.setRequestProperty("User-Agent", "Mozilla/5.0");
                        connection.setUseCaches(false);
                        connection.connect();
                        int length = connection.getContentLength();
                        BufferedInputStream in = new BufferedInputStream(connection.getInputStream());
                        Path tempFile = Files.createTempFile("temp_", ".jar");
                        FileOutputStream fos = new FileOutputStream(tempFile.toFile());
                        byte[] buffer = new byte[4096];
                        int bytesRead = -1;
                        int totalBytesRead = 0;
                        while ((bytesRead = in.read(buffer)) != -1) {
                            fos.write(buffer, 0, bytesRead);
                            totalBytesRead += bytesRead;
                        }
                        fos.close();
                        in.close();
                        if (totalBytesRead == length) {
                            getLogger().info("Updating plugin " + pluginName);
                            Files.delete(pluginFile);
                            Files.move(tempFile, pluginFile);
                        } else {
                            Files.delete(tempFile);
                        }
                    } catch (IOException e) {
                        getLogger().warning("Failed to download update for plugin " + pluginName);
                    }
                }
            }
        }
    }
    
    private PluginDescriptionFile getPluginDescription(Path pluginFile) throws InvalidDescriptionException {
        try {
            URLClassLoader loader = URLClassLoader.newInstance(new URL[] { pluginFile.toUri().toURL() });
            InputStream is = loader.getResourceAsStream("plugin.yml");
            if (is != null) {
                return new PluginDescriptionFile(is);
            }
        } catch (IOException e) {
            getLogger().warning("Failed to read plugin.yml for " + pluginFile.getFileName().toString());
        }
        return null;
    }
}
