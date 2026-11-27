/*
 * This file is part of MyPet
 *
 * Copyright © 2011-2019 Keyle
 * MyPet is licensed under the GNU Lesser General Public License.
 *
 * MyPet is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MyPet is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package de.Keyle.MyPet.util;

import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import de.Keyle.MyPet.MyPetApi;
import de.Keyle.MyPet.api.Configuration;
import de.Keyle.MyPet.api.MyPetVersion;
import de.Keyle.MyPet.api.Util;
import org.bukkit.ChatColor;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.Optional;

public class Updater {

    public class Update {

        String version;
        String downloadURL;
        String sha512Hash;

        public Update(String version, String downloadURL, String sha512Hash) {
            this.version = version;
            this.downloadURL = downloadURL;
            this.sha512Hash = sha512Hash;
        }

        public String getVersion() {
            return version;
        }

        public String getDownloadURL() { return downloadURL; }

        public String getSha512Hash() { return sha512Hash; }

        @Override
        public String toString() {
            return version;
        }
    }

    // 由启动检查线程写入，主线程读取（加入消息、/mypet update）
    protected static volatile Update latest = null;
    protected String plugin;
    protected volatile Thread thread;

    public Updater(String plugin) {
        this.plugin = plugin;
        latest = null;
    }

    /**
     * 检查更新并返回一条在启动时记录到日志的状态消息。
     * 该操作会阻塞网络请求，因此 MyPet 从后台线程调用它。若启用了下载，
     * 则在 "async" 为真时在独立线程中执行，否则在调用者线程中执行。
     *
     * @return 状态消息；若更新检查已禁用则返回 null
     */
    public String update() {
        if (!Configuration.Update.CHECK) {
            return null;
        }

        if (MyPetVersion.isLocalBuild()) {
            return "Skipping update check for local build.";
        }

        Optional<Update> update = check();
        if (update.isPresent()) {
            Update found = update.get();
            latest = found;

            if (Configuration.Update.DOWNLOAD) {
                download();
            }

            String msg = "A new " + (MyPetVersion.isDevBuild() ? "build" : "version") + " is available: " + found;
            return msg;
        }
        return "No update available.";
    }

    protected Optional<Update> check() {
        try {

            String currentPluginVersion = MyPetVersion.getVersion();

            // 使用 Modrinth API 检查更新
            String url = "https://api.modrinth.com/v2/project/mypet/version?loaders=%5B%22paper%22,%22spigot%22%5D";

            String content = Util.readUrlContent(url);
            JsonArray resultArr = new Gson().fromJson(content, JsonArray.class);
            Optional<Update> update = Optional.empty();

            String currentMcVersion = MyPetApi.getCompatUtil().getMinecraftVersion();
            boolean isDevBuild = MyPetVersion.isDevBuild();

            for (int i = 0; i < resultArr.size(); i++) {
                JsonObject version = (JsonObject) resultArr.get(i);

                // 检查版本类型："release" 表示稳定版，"alpha"/"beta" 表示快照版
                String versionType = version.get("version_type").getAsString();
                boolean isAlpha = "alpha".equals(versionType) || "beta".equals(versionType);

                // 正式版构建只应看到正式版
                // 开发版构建既能看到快照版也能看到正式版（以便升级到最终正式版）
                if (!isDevBuild && isAlpha) {
                    continue;
                }

                // 检查该版本是否支持当前 Minecraft 版本
                JsonArray gameVersions = version.has("game_versions") ? version.getAsJsonArray("game_versions") : null;
                if (gameVersions == null || gameVersions.size() == 0) {
                    continue;
                }
                boolean supportsCurrentMc = false;
                for (int j = 0; j < gameVersions.size(); j++) {
                    String gameVersion = gameVersions.get(j).getAsString();
                    // 同时支持精确匹配（1.21.4）与通配符（1.21.x）
                    if (gameVersion.equals(currentMcVersion) ||
                        (gameVersion.endsWith(".x") && currentMcVersion.startsWith(gameVersion.substring(0, gameVersion.length() - 2)))) {
                        supportsCurrentMc = true;
                        break;
                    }
                }

                if (!supportsCurrentMc) {
                    continue;
                }

                String versionNumber = version.get("version_number").getAsString();

                // 仅当版本确实更新时才将其视为一次更新
                // 处理 "3.14.1" 与 "3.14.1-SNAPSHOT-b42" 这类版本号
                if (!isNewerVersion(versionNumber, currentPluginVersion)) {
                    continue;
                }

                // 找到主文件（若没有文件被标记为主文件则取第一个）
                JsonArray files = version.getAsJsonArray("files");
                JsonObject primaryFile = null;
                for (int j = 0; j < files.size(); j++) {
                    JsonObject file = files.get(j).getAsJsonObject();
                    if (file.has("primary") && file.get("primary").getAsBoolean()) {
                        primaryFile = file;
                        break;
                    }
                }
                if (primaryFile == null && files.size() > 0) {
                    primaryFile = files.get(0).getAsJsonObject();
                }

                if (primaryFile != null && primaryFile.has("url")) {
                    String downloadURL = primaryFile.get("url").getAsString();
                    JsonObject hashes = primaryFile.has("hashes") ? primaryFile.getAsJsonObject("hashes") : null;
                    String sha512Hash = (hashes != null && hashes.has("sha512")) ? hashes.get("sha512").getAsString() : null;
                    update = Optional.of(new Update(versionNumber, downloadURL, sha512Hash));
                    break;
                }
            }
            return update;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Optional.empty();
    }

    public void download() {
        File pluginFile;
        if (Configuration.Update.REPLACE_OLD) {
            pluginFile = new File(MyPetApi.getPlugin().getFile().getParentFile().getAbsolutePath(), "update/" + MyPetApi.getPlugin().getFile().getName());
        } else {
            pluginFile = new File(MyPetApi.getPlugin().getFile().getParentFile().getAbsolutePath(), "update/MyPet-" + latest.getVersion() + ".jar");
        }
        if (!pluginFile.getParentFile().exists()) {
            pluginFile.getParentFile().mkdirs();
        }

        String finalUrl = latest.getDownloadURL();
        String expectedHash = latest.getSha512Hash();
        Runnable downloadRunner = () -> {
            MyPetApi.getLogger().info(ChatColor.RED + "Start update download: " + ChatColor.RESET + latest);

            if (expectedHash == null) {
                MyPetApi.getLogger().severe("Download aborted: Hash verification unavailable (API did not provide SHA512).");
                return;
            }

            String actualHash;
            try {
                URL website = new URL(finalUrl);
                HttpURLConnection httpConn = (HttpURLConnection) website.openConnection();
                int responseCode = httpConn.getResponseCode();

                if (responseCode != HttpURLConnection.HTTP_OK) {
                    httpConn.disconnect();
                    return;
                }

                Hasher hasher = Hashing.sha512().newHasher();
                try (InputStream inputStream = httpConn.getInputStream();
                     FileOutputStream outputStream = new FileOutputStream(pluginFile)) {
                    int bytesRead;
                    byte[] buffer = new byte[4096];
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                        hasher.putBytes(buffer, 0, bytesRead);
                    }
                }
                actualHash = hasher.hash().toString();
            } catch (IOException e) {
                MyPetApi.getLogger().warning("Download failed: " + e.getMessage());
                pluginFile.delete(); // Clean up partial download
                return;
            }

            // 校验 SHA512 哈希
            if (!expectedHash.equalsIgnoreCase(actualHash)) {
                MyPetApi.getLogger().severe("Download verification failed! Hash mismatch.");
                MyPetApi.getLogger().severe("Expected: " + expectedHash);
                MyPetApi.getLogger().severe("Actual:   " + actualHash);
                MyPetApi.getLogger().severe("Deleting corrupted file...");
                if (!pluginFile.delete()) {
                    MyPetApi.getLogger().warning("Failed to delete corrupted file: " + pluginFile.getAbsolutePath());
                }
                return;
            }

            String message = "Finished update download (verified).";
            if (Configuration.Update.REPLACE_OLD || MyPetApi.getPlugin().getFile().getName().equals("MyPet-" + latest.getVersion() + ".jar")) {
                message += " The update will be loaded on the next server start.";
            } else {
                message += " The file was stored in the \"update\" folder.";
            }
            MyPetApi.getLogger().info(message);
        };
        if (!Configuration.Update.ASYNC) {
            downloadRunner.run();
        } else {
            thread = new Thread(downloadRunner);
            thread.start();
        }
    }

    public void waitForDownload() {
        if (Configuration.Update.ASYNC && thread != null && thread.isAlive()) {
            MyPetApi.getLogger().info("Wait for the update download to finish...");
            try {
                thread.join();
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
        }
    }

    public static Update getLatest() {
        return latest;
    }

    public static boolean isUpdateAvailable() {
        return latest != null;
    }

    /**
     * 比较两个版本字符串，判断前者是否更新。
     * 支持 "3.14.1"、"3.14.1-SNAPSHOT" 和 "3.14.1-SNAPSHOT-b42" 等格式。
     * 正式版（3.14.1）始终被视为比同一基线的快照（3.14.1-SNAPSHOT-b10）更新。
     */
    private static boolean isNewerVersion(String newVersion, String currentVersion) {
        // 提取基础版本号（第一个连字符之前的部分）
        String newBase = newVersion.split("-")[0];
        String currentBase = currentVersion.split("-")[0];

        int baseCompare = Util.versionCompare(newBase, currentBase);
        if (baseCompare != 0) {
            return baseCompare > 0;
        }

        // 基础版本相同 - 检查正式版与快照版的区别
        boolean newIsSnapshot = newVersion.contains("-SNAPSHOT");
        boolean currentIsSnapshot = currentVersion.contains("-SNAPSHOT");

        // 正式版始终比同一基础版本的快照更新
        if (!newIsSnapshot && currentIsSnapshot) {
            return true;
        }
        // 快照版永远不会比同一基础版本的正式版更新
        if (newIsSnapshot && !currentIsSnapshot) {
            return false;
        }

        // 两者都是快照（或都是正式版），比较构建号
        int newBuild = extractBuildNumber(newVersion);
        int currentBuild = extractBuildNumber(currentVersion);

        return newBuild > currentBuild;
    }

    /**
     * 从 "3.14.1-SNAPSHOT-b42" 这类版本字符串中提取构建号。
     * 若未找到构建号则返回 0。
     */
    private static int extractBuildNumber(String version) {
        int bIndex = version.lastIndexOf("-b");
        if (bIndex == -1) {
            return 0;
        }
        try {
            return Integer.parseInt(version.substring(bIndex + 2));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}