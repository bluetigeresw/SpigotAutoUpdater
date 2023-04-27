package com.bluetigeresw.SpigotAutoUpdater;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.plugin.InvalidDescriptionException;
import org.bukkit.plugin.InvalidPluginException;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.UnknownDependencyException;
import org.bukkit.plugin.java.JavaPlugin;

import org.json.JSONObject;

    public class AutoUpdater extends JavaPlugin {

        private Logger logger;
        //private Path configFile;
        private Path logFile;
        private List<String> excludedPlugins;
        private String apiBaseUrl = "https://api.spiget.org/v2";

        @Override
        public void onEnable() {
            logger = getLogger();
            //configFile = getDataFolder().toPath().resolve("config.yml");
            logFile = getDataFolder().toPath().resolve("update.log");

            saveDefaultConfig();
            excludedPlugins = getConfig().getStringList("excludedPlugins");

            try {
                checkForUpdates();
            } catch (IOException e) {
                logger.warning("Failed to check for plugin updates: " + e.getMessage());
            } catch (URISyntaxException e) {
                e.printStackTrace();
            } catch (UnknownDependencyException e) {
                e.printStackTrace();
            } catch (InvalidPluginException e) {
                e.printStackTrace();
            } catch (InvalidDescriptionException e) {
                e.printStackTrace();
            }
        }

        @SuppressWarnings("unused")
        private void checkForUpdates() throws IOException, URISyntaxException, UnknownDependencyException, InvalidPluginException, InvalidDescriptionException {
            logger.info("Checking for plugin updates...");
            JSONObject apiResponse = new JSONObject(readUrl(apiBaseUrl + "/resources?size=10000&fields=name,version,externalUrl"));

            for (Plugin plugin : Bukkit.getPluginManager().getPlugins()) {
                PluginDescriptionFile description = plugin.getDescription();
                String pluginName = description.getName();
                if (excludedPlugins.contains(pluginName)) {
                    logger.info(pluginName + " is excluded from update checks.");
                    continue;
                }
                String currentVersion = description.getVersion();
                JSONObject pluginInfo = apiResponse.getJSONArray("data")
                        .toList().stream()
                        .map(obj -> (JSONObject) obj)
                        .filter(obj -> obj.getString("name").equals(pluginName))
                        .findFirst().orElse(null);
                if (pluginInfo == null) {
                    logger.warning(pluginName + " not found on BukkitDev.");
                    continue;
                }
                String latestVersion = pluginInfo.getString("version");
                if (currentVersion.equals(latestVersion)) {
                    logger.info(pluginName + " is up to date.");
                    continue;
                }
                String externalUrl = pluginInfo.getString("externalUrl");
                logger.info("Updating " + pluginName + " from version " + currentVersion + " to " + latestVersion + "...");
                PluginFilesInfo filesInfo = getPluginFilesInfo(externalUrl);
                if (filesInfo == null) {
                    logger.warning("Failed to retrieve update info for " + pluginName + ".");
                    continue;
                }
                String downloadUrl = filesInfo.getDownloadUrl();
                URL url = plugin.getClass().getProtectionDomain().getCodeSource().getLocation();
                URI uri = url.toURI();
                Path pluginFile = Paths.get(uri);
                Path updatedPluginFile = Paths.get(pluginFile.toUri()).resolveSibling(pluginFile.getFileName().toString().replace(".jar", "-new.jar"));
                try (BufferedInputStream in = new BufferedInputStream(new URL(downloadUrl).openStream());
                        BufferedOutputStream out = new BufferedOutputStream(Files.newOutputStream(updatedPluginFile))) {
                    byte[] buffer = new byte[4096];
                    int bytesRead = -1;
                    while ((bytesRead = in.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                    }
                } catch (IOException e) {
                    logger.warning("Failed to download update for " + pluginName + ": " + e.getMessage());
                    continue;
                }
                // Load the updated plugin file into the classpath
                URLClassLoader classLoader = new URLClassLoader(new URL[] { updatedPluginFile.toUri().toURL() },
                        getClass().getClassLoader());
                Class<?> pluginClass;
                try {
                    pluginClass = classLoader.loadClass(plugin.getClass().getName());
                } catch (ClassNotFoundException e) {
                    logger.warning("Failed to load updated plugin class for " + pluginName + ": " + e.getMessage());
                    continue;
                }

                // Disable the plugin and unload its classloader
                Bukkit.getPluginManager().disablePlugin(plugin);
                classLoader.close();

                // Replace the old plugin file with the updated one
                Files.move(updatedPluginFile, pluginFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

                // Reload the plugin
                Plugin updatedPlugin = Bukkit.getPluginManager().loadPlugin(pluginFile.toFile());
                Bukkit.getPluginManager().enablePlugin(updatedPlugin);

                // Log the successful update
                String logEntry = pluginName + " updated from version " + currentVersion + " to " + latestVersion;
                logger.info(logEntry);
                Files.write(logFile, (logEntry + "\n").getBytes(), java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
            }
        }

        private PluginFilesInfo getPluginFilesInfo(String externalUrl) {
            try {
                JSONObject apiResponse = new JSONObject(readUrl(apiBaseUrl + "/resources/" + getSpigetResourceId(externalUrl)));
                return new PluginFilesInfo(apiResponse.getJSONArray("files").getJSONObject(0));
            } catch (Exception e) {
                return null;
            }
        }

        private int getSpigetResourceId(String externalUrl) {
            String[] parts = externalUrl.split("/");
            String idStr = parts[parts.length - 1];
            return Integer.parseInt(idStr);
        }

        private String readUrl(String urlString) throws IOException {
            URL url = new URL(urlString);
            URLConnection conn = url.openConnection();
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            try (BufferedInputStream in = new BufferedInputStream(conn.getInputStream())) {
                byte[] buffer = new byte[4096];
                StringBuilder sb = new StringBuilder();
                int bytesRead = -1;
                while ((bytesRead = in.read(buffer)) != -1) {
                    sb.append(new String(buffer, 0, bytesRead));
                }
                return sb.toString();
            }
        }

        private static class PluginFilesInfo {
            private String downloadUrl;

            public PluginFilesInfo(JSONObject jsonObject) {
                downloadUrl = jsonObject.getString("url");
            }

            public String getDownloadUrl() {
                return downloadUrl;
            }
        }

    }

            
