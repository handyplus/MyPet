/*
 * This file is part of MyPet
 *
 * Copyright © 2011-2026 Keyle
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

package de.Keyle.MyPet.util.translation;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonWriter;
import de.Keyle.MyPet.MyPetApi;
import org.bukkit.plugin.Plugin;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 在运行时从启动器 meta + 资源 CDN 获取 Mojang 的原版翻译文件，
 * 解析出 {@code entity.minecraft.*} 条目，并维护一个内存中的
 * 语言标签 &rarr; 键 &rarr; 翻译字符串 映射，供
 * {@link #resolveEntityName(String, String)} 读取。
 *
 * <p>各种失败情形（离线服务器、Mojang CDN 宕机、清单中缺少 MC 版本、
 * JSON 格式错误）都会被吸收处理：加载器记录一条警告，解析器则回退到
 * {@link #englishFromBukkitName(String)}，由它从 Bukkit 实体名称机械推导出英文名称。</p>
 *
 * <p>插件 JAR 中从不打包任何数据。插件代表正在运行的服务器充当
 * 资源获取代理，使用的是官方启动器所用的同一批公开接口。</p>
 */
public final class VanillaTranslationLoader {

    private VanillaTranslationLoader() {}

    private static final String VERSION_MANIFEST_URL =
            "https://launchermeta.mojang.com/mc/game/version_manifest.json";
    private static final String RESOURCES_URL =
            "https://resources.download.minecraft.net/";

    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final int READ_TIMEOUT_MS = 30_000;

    private static final String CLIENT_JAR_SHA1_KEY = "__client_jar_sha1";
    private static final String ENTITY_KEY_PREFIX = "entity.minecraft.";

    private static final Pattern MC_VERSION_RE = Pattern.compile("\\d+\\.\\d+(?:\\.\\d+)?");
    private static final Pattern SHA1_HEX_RE = Pattern.compile("[0-9a-fA-F]{40}");

    // 语言标签（例如 "de_de"）-> 翻译键 -> 翻译字符串。
    // 通过 volatile 写入整体替换，使调用方永远不会观察到构建到一半的映射。
    private static volatile Map<String, Map<String, String>> translations = new HashMap<>();

    // 跟踪正在进行的异步加载，使 MyPetPlugin#onDisable 能在插件被禁用后任何滞后的
    // volatile 发布落到静态字段之前中断它。
    private static volatile CompletableFuture<Void> currentFuture = null;

    public static CompletableFuture<Void> loadAsync(Plugin plugin) {
        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            try {
                loadInternal(plugin);
            } catch (Throwable t) {
                MyPetApi.getLogger().warning(
                        "Vanilla translation loader failed; pet default names will use English derived from entity type. Cause: "
                                + t.getMessage());
            }
        });
        currentFuture = future;
        return future;
    }

    /**
     * 取消任何正在进行的 {@link #loadAsync(Plugin)} 任务。在没有加载进行时调用是安全的。
     * 设计用于从 {@code MyPetPlugin#onDisable} 调用，以免运行在公共 ForkJoinPool 上的
     * 长时间 CDN 拉取越过插件关闭而继续存在，并通过共享的静态字段覆盖后继插件实例的翻译映射。
     */
    public static void cancelLoad() {
        CompletableFuture<Void> future = currentFuture;
        if (future != null && !future.isDone()) {
            future.cancel(true);
        }
    }

    public static String resolveEntityName(String bukkitName, String localeTag) {
        String key = ENTITY_KEY_PREFIX + bukkitName.toLowerCase(Locale.ROOT);
        String normalizedLocale = localeTag == null
                ? "en_us"
                : localeTag.toLowerCase(Locale.ROOT).replace('-', '_');

        Map<String, Map<String, String>> snapshot = translations;
        Map<String, String> locale = snapshot.get(normalizedLocale);
        if (locale != null) {
            String hit = locale.get(key);
            if (hit != null) return hit;
        }
        // 先回退到仅语言（例如未发布 "de_de" 时使用 "de"），再回退到英文。
        int underscore = normalizedLocale.indexOf('_');
        if (underscore > 0) {
            Map<String, String> langOnly = snapshot.get(normalizedLocale.substring(0, underscore));
            if (langOnly != null) {
                String hit = langOnly.get(key);
                if (hit != null) return hit;
            }
        }
        Map<String, String> english = snapshot.get("en_us");
        if (english != null) {
            String hit = english.get(key);
            if (hit != null) return hit;
        }
        return englishFromBukkitName(bukkitName);
    }

    public static String englishFromBukkitName(String bukkitName) {
        StringBuilder sb = new StringBuilder(bukkitName.length());
        boolean cap = true;
        for (int i = 0; i < bukkitName.length(); i++) {
            char c = bukkitName.charAt(i);
            if (c == '_') {
                sb.append(' ');
                cap = true;
            } else if (cap) {
                sb.append(Character.toUpperCase(c));
                cap = false;
            } else {
                sb.append(Character.toLowerCase(c));
            }
        }
        return sb.toString();
    }

    private static void loadInternal(Plugin plugin) throws Exception {
        String mcVersion = MyPetApi.getCompatUtil().getMinecraftVersion();
        // 当 CompatUtil 无法解析 Bukkit 的版本横幅时跳过整套 CDN 操作——
        // 默认值 "0.0.0" 永远匹配不上 Mojang 清单条目，否则清单拉取 + 扫描会在每次启动时
        // 白白运行一遍然后失败。
        if (mcVersion == null || "0.0.0".equals(mcVersion) || !MC_VERSION_RE.matcher(mcVersion).matches()) {
            MyPetApi.getLogger().warning("Skipping vanilla translation load: unrecognized Minecraft version '"
                    + mcVersion + "'");
            return;
        }
        Path cacheDir = cacheDirFor(plugin, mcVersion);
        Map<String, String> hashes = readHashes(cacheDir);

        JsonObject manifest = fetchJson(VERSION_MANIFEST_URL);
        String versionUrl = null;
        for (JsonElement v : manifest.getAsJsonArray("versions")) {
            JsonObject entry = v.getAsJsonObject();
            if (mcVersion.equalsIgnoreCase(entry.get("id").getAsString())) {
                versionUrl = entry.get("url").getAsString();
                break;
            }
        }
        if (versionUrl == null) {
            throw new IOException("No version manifest entry for Minecraft " + mcVersion);
        }
        JsonObject versionMeta = fetchJson(versionUrl);

        String clientUrl = versionMeta.getAsJsonObject("downloads")
                .getAsJsonObject("client").get("url").getAsString();
        String clientSha1 = versionMeta.getAsJsonObject("downloads")
                .getAsJsonObject("client").get("sha1").getAsString();
        boolean enUsChanged = downloadEnUsFromClientJar(cacheDir, clientUrl, clientSha1, hashes);

        String indexUrl = versionMeta.getAsJsonObject("assetIndex").get("url").getAsString();
        boolean localesChanged = downloadLocalesFromAssetIndex(cacheDir, indexUrl, hashes);

        if (enUsChanged || localesChanged) {
            writeHashes(cacheDir, hashes);
        }
        loadAllLocalesIntoMap(cacheDir, plugin);
    }

    private static Path cacheDirFor(Plugin plugin, String mcVersion) throws IOException {
        // 进行规范化，使 downloadLocalesFromAssetIndex 中的路径穿越防护（将规范化后的目标
        // 与此基准进行比较）不会在包含 '.' 路径段的数据文件夹路径上误判为不匹配——
        // 当服务器从容器中启动，或通过宽松解析工作目录的包装脚本启动时，这种情况很常见。
        Path dir = plugin.getDataFolder().toPath()
                .resolve("cache")
                .resolve("vanilla-lang")
                .resolve(mcVersion)
                .normalize();
        Files.createDirectories(dir);
        return dir;
    }

    private static Map<String, String> readHashes(Path cacheDir) throws IOException {
        Path file = cacheDir.resolve("hashes.json");
        Map<String, String> out = new HashMap<>();
        if (!Files.exists(file)) return out;
        try (Reader rdr = new InputStreamReader(Files.newInputStream(file), StandardCharsets.UTF_8)) {
            JsonObject obj = new JsonParser().parse(rdr).getAsJsonObject();
            for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
                out.put(e.getKey(), e.getValue().getAsString());
            }
        }
        return out;
    }

    private static void writeHashes(Path cacheDir, Map<String, String> hashes) throws IOException {
        Path file = cacheDir.resolve("hashes.json");
        try (JsonWriter w = new JsonWriter(new OutputStreamWriter(
                Files.newOutputStream(file), StandardCharsets.UTF_8))) {
            w.beginObject();
            for (Map.Entry<String, String> e : hashes.entrySet()) {
                w.name(e.getKey()).value(e.getValue());
            }
            w.endObject();
        }
    }

    private static byte[] fetchBytes(String url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        try {
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestMethod("GET");
            int status = conn.getResponseCode();
            if (status != 200) {
                throw new IOException("HTTP " + status + " fetching " + url);
            }
            try (InputStream in = conn.getInputStream()) {
                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                byte[] chunk = new byte[8192];
                int n;
                while ((n = in.read(chunk)) > 0) buf.write(chunk, 0, n);
                return buf.toByteArray();
            }
        } finally {
            conn.disconnect();
        }
    }

    private static JsonObject fetchJson(String url) throws IOException {
        byte[] body = fetchBytes(url);
        try (Reader rdr = new InputStreamReader(new ByteArrayInputStream(body), StandardCharsets.UTF_8)) {
            return new JsonParser().parse(rdr).getAsJsonObject();
        }
    }

    private static String sha1Hex(byte[] data) throws Exception {
        return hexOf(MessageDigest.getInstance("SHA-1").digest(data));
    }

    private static String hexOf(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            int v = b & 0xFF;
            sb.append(Character.forDigit(v >>> 4, 16));
            sb.append(Character.forDigit(v & 0x0F, 16));
        }
        return sb.toString();
    }

    private static boolean downloadEnUsFromClientJar(Path cacheDir, String clientUrl,
                                                     String manifestSha1,
                                                     Map<String, String> hashes) throws Exception {
        if (manifestSha1.equalsIgnoreCase(hashes.get(CLIENT_JAR_SHA1_KEY))
                && Files.exists(cacheDir.resolve("en_us.json"))) {
            return false;
        }

        // 将客户端 jar（现代 MC 上约 30-60 MB）流式写入缓存目录中的临时文件，
        // 同时增量计算 SHA-1。这样可避免把整个 jar 内容放进堆中，并让我们在信任
        // 提取出的 en_us.json 之前，用 Mojang 清单中的哈希校验其完整性。
        HttpURLConnection conn = (HttpURLConnection) new URL(clientUrl).openConnection();
        conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(READ_TIMEOUT_MS);
        conn.setInstanceFollowRedirects(true);
        conn.setRequestMethod("GET");
        Path tmpJar = Files.createTempFile(cacheDir, "client-", ".jar");
        try {
            int status = conn.getResponseCode();
            if (status != 200) {
                throw new IOException("HTTP " + status + " fetching " + clientUrl);
            }
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            try (DigestInputStream dis = new DigestInputStream(conn.getInputStream(), md)) {
                Files.copy(dis, tmpJar, StandardCopyOption.REPLACE_EXISTING);
            }
            String computedSha1 = hexOf(md.digest());
            if (!computedSha1.equalsIgnoreCase(manifestSha1)) {
                throw new IOException("Client jar SHA-1 mismatch: expected " + manifestSha1
                        + " got " + computedSha1 + " at " + clientUrl);
            }
            try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(tmpJar))) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    if (!"assets/minecraft/lang/en_us.json".equalsIgnoreCase(entry.getName())) continue;
                    ByteArrayOutputStream buf = new ByteArrayOutputStream();
                    byte[] chunk = new byte[8192];
                    int n;
                    while ((n = zip.read(chunk)) > 0) buf.write(chunk, 0, n);
                    Path target = cacheDir.resolve("en_us.json");
                    Path stage = cacheDir.resolve("en_us.json.tmp");
                    Files.write(stage, buf.toByteArray());
                    // ATOMIC_MOVE 保证即使发生崩溃，缓存目标也不会被观察到写入到一半的状态；
                    // 配合 REPLACE_EXISTING 使用，以便针对不同 MC 版本字符串的重新拉取可以覆盖
                    // 同一缓存目录中旧的 en_us.json。
                    Files.move(stage, target, StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING);
                    hashes.put(CLIENT_JAR_SHA1_KEY, manifestSha1);
                    return true;
                }
            }
            throw new IOException("en_us.json not found inside client jar at " + clientUrl);
        } finally {
            try {
                Files.deleteIfExists(tmpJar);
            } catch (IOException ignored) {}
            conn.disconnect();
        }
    }

    private static boolean downloadLocalesFromAssetIndex(Path cacheDir, String indexUrl,
                                                         Map<String, String> hashes) throws Exception {
        JsonObject index = fetchJson(indexUrl);
        JsonObject objects = index.getAsJsonObject("objects");
        boolean changed = false;
        for (Map.Entry<String, JsonElement> e : objects.entrySet()) {
            String key = e.getKey().toLowerCase(Locale.ROOT);
            if (!key.startsWith("minecraft/lang/") || !key.endsWith(".json")) continue;
            String locale = key.substring("minecraft/lang/".length(), key.length() - ".json".length());
            if ("en_us".equals(locale)) continue; // 已从客户端 jar 中提取

            // 防御性检查：缺少 "hash" 字段、值不是字符串或十六进制字符串格式错误，
            // 否则会在下面的 substring/URL 构建中抛出 NPE / 异常，并通过外层 catch 中止
            // 整个加载——从而静默丢弃其它所有语言环境。
            JsonElement hashElem = e.getValue().getAsJsonObject().get("hash");
            if (hashElem == null || hashElem.isJsonNull()
                    || !hashElem.isJsonPrimitive() || !hashElem.getAsJsonPrimitive().isString()) {
                MyPetApi.getLogger().warning("Skipping locale " + locale + " from asset index: missing or non-string 'hash' field");
                continue;
            }
            String hash = hashElem.getAsString();
            if (!SHA1_HEX_RE.matcher(hash).matches()) {
                MyPetApi.getLogger().warning("Skipping locale " + locale + " from asset index: malformed hash '" + hash + "'");
                continue;
            }
            Path target = cacheDir.resolve(locale + ".json").normalize();
            // 防止构造的索引键在规范化解析后逃逸出缓存目录。
            if (!target.startsWith(cacheDir)) {
                MyPetApi.getLogger().warning("Skipping suspicious locale key from asset index: " + locale);
                continue;
            }
            if (hash.equalsIgnoreCase(hashes.get(locale)) && Files.exists(target)) continue;

            String url = RESOURCES_URL + hash.substring(0, 2) + "/" + hash;
            byte[] bytes = fetchBytes(url);
            // 在写入或记录缓存键之前，用资源索引声明的哈希校验字节完整性。
            // 否则，中间人攻击或临时损坏造成返回 200 OK 但内容错误的情况会被静默持久化，
            // 并在之后每次启动时被信任。
            String actual = sha1Hex(bytes);
            if (!actual.equalsIgnoreCase(hash)) {
                MyPetApi.getLogger().warning("Locale " + locale + " SHA-1 mismatch: expected " + hash
                        + " got " + actual + "; skipping");
                continue;
            }
            Path stage = cacheDir.resolve(locale + ".json.tmp");
            Files.write(stage, bytes);
            Files.move(stage, target, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
            hashes.put(locale, hash);
            changed = true;
        }
        return changed;
    }

    private static void loadAllLocalesIntoMap(Path cacheDir, Plugin plugin) throws IOException {
        Map<String, Map<String, String>> built = new HashMap<>();
        int localeCount = 0;
        int keyCount = 0;
        try (DirectoryStream<Path> files = Files.newDirectoryStream(cacheDir, "*.json")) {
            for (Path file : files) {
                String name = file.getFileName().toString();
                if ("hashes.json".equals(name)) continue;
                String localeTag = name.substring(0, name.length() - ".json".length());
                Map<String, String> entries = parseEntityEntries(file);
                if (!entries.isEmpty()) {
                    built.put(localeTag, entries);
                    localeCount++;
                    keyCount += entries.size();
                }
            }
        }
        // 如果此次拉取进行期间插件已被禁用（例如 /reload 或快速重启），就不要发布——
        // 在使用保留类加载器的重载器时，静态字段会在插件实例之间共享，迟到的写入
        // 会覆盖后继实例的映射。
        if (plugin != null && !plugin.isEnabled()) {
            return;
        }
        translations = built;
        MyPetApi.getLogger().info("Loaded " + keyCount + " vanilla entity translations across "
                + localeCount + " locales");
    }

    private static Map<String, String> parseEntityEntries(Path file) {
        Map<String, String> out = new LinkedHashMap<>();
        try (Reader rdr = new InputStreamReader(Files.newInputStream(file), StandardCharsets.UTF_8)) {
            JsonObject obj = new JsonParser().parse(rdr).getAsJsonObject();
            for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
                String k = e.getKey();
                if (!k.startsWith(ENTITY_KEY_PREFIX)) continue;
                JsonElement v = e.getValue();
                if (!v.isJsonPrimitive() || !v.getAsJsonPrimitive().isString()) continue;
                out.put(k, v.getAsString());
            }
        } catch (IOException | RuntimeException ex) {
            // RuntimeException 涵盖了 JsonSyntaxException（JSON 损坏）和 IllegalStateException
            // （根不是 JSON 对象）——否则两者都会从 loadAllLocalesIntoMap 中向外传播，
            // 中止整个加载，丢弃其它所有解析成功的语言环境。
            MyPetApi.getLogger().warning("Skipping malformed vanilla lang file "
                    + file.getFileName() + ": " + ex.getMessage());
        }
        return out;
    }
}
