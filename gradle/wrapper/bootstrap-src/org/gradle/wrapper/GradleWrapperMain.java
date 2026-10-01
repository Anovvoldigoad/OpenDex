package org.gradle.wrapper;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;

/**
 * Tiny source-included bootstrap used only because the official wrapper binary
 * could not be vendored by the generation environment. It reads the standard
 * gradle-wrapper.properties file, downloads the configured Gradle distribution,
 * extracts it under ~/.gradle/opendex-wrapper and forwards all arguments.
 */
public final class GradleWrapperMain {
    public static void main(String[] args) throws Exception {
        Path appHome = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        Path propsPath = appHome.resolve("gradle/wrapper/gradle-wrapper.properties");
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(propsPath)) { props.load(in); }
        String distributionUrl = props.getProperty("distributionUrl");
        if (distributionUrl == null) throw new IllegalStateException("distributionUrl missing");
        distributionUrl = distributionUrl.replace("\\:", ":");

        String zipName = distributionUrl.substring(distributionUrl.lastIndexOf('/') + 1);
        String folderName = zipName.replace("-bin.zip", "").replace("-all.zip", "");
        Path cacheRoot = Paths.get(System.getProperty("user.home"), ".gradle", "opendex-wrapper");
        Path distDir = cacheRoot.resolve(folderName);
        Path marker = distDir.resolve(".ready");

        if (!Files.exists(marker)) {
            Files.createDirectories(cacheRoot);
            Path zip = cacheRoot.resolve(zipName);
            if (!Files.exists(zip)) {
                System.out.println("Downloading " + distributionUrl);
                download(new URL(distributionUrl), zip);
            }
            String expectedSha = props.getProperty("distributionSha256Sum");
            if (expectedSha != null && !expectedSha.trim().isEmpty()) {
                String actualSha = sha256(zip);
                if (!expectedSha.trim().equalsIgnoreCase(actualSha)) {
                    Files.deleteIfExists(zip);
                    throw new IOException("Gradle distribution SHA-256 mismatch. Expected " + expectedSha + " but got " + actualSha);
                }
            }
            if (Files.exists(distDir)) deleteRecursively(distDir);
            Files.createDirectories(distDir);
            unzip(zip, distDir);
            Files.write(marker, Collections.singletonList("ok"));
        }

        Path gradleHome = findGradleHome(distDir);
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        Path gradle = gradleHome.resolve(windows ? "bin/gradle.bat" : "bin/gradle");
        if (!windows) gradle.toFile().setExecutable(true);

        List<String> command = new ArrayList<>();
        command.add(gradle.toString());
        command.addAll(Arrays.asList(args));
        ProcessBuilder pb = new ProcessBuilder(command).directory(appHome.toFile()).inheritIO();
        Process process = pb.start();
        System.exit(process.waitFor());
    }

    private static void download(URL url, Path target) throws Exception {
        URL current = url;
        for (int redirects = 0; redirects < 10; redirects++) {
            HttpURLConnection c = (HttpURLConnection) current.openConnection();
            c.setInstanceFollowRedirects(false);
            c.setConnectTimeout(15000);
            c.setReadTimeout(30000);
            c.setRequestProperty("User-Agent", "OpenDex-Gradle-Bootstrap");
            int code = c.getResponseCode();
            if (code >= 300 && code < 400) {
                String location = c.getHeaderField("Location");
                c.disconnect();
                if (location == null) throw new IOException("redirect without Location");
                current = new URL(current, location);
                continue;
            }
            if (code < 200 || code >= 300) throw new IOException("HTTP " + code + " for " + current);
            try (InputStream in = c.getInputStream(); OutputStream out = Files.newOutputStream(target)) {
                byte[] buffer = new byte[64 * 1024];
                int n;
                while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
            } finally {
                c.disconnect();
            }
            return;
        }
        throw new IOException("too many redirects");
    }

    private static void unzip(Path zip, Path dest) throws Exception {
        try (ZipInputStream zin = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry e;
            while ((e = zin.getNextEntry()) != null) {
                Path out = dest.resolve(e.getName()).normalize();
                if (!out.startsWith(dest)) throw new IOException("invalid zip path: " + e.getName());
                if (e.isDirectory()) {
                    Files.createDirectories(out);
                } else {
                    Files.createDirectories(out.getParent());
                    Files.copy(zin, out, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static Path findGradleHome(Path distDir) throws Exception {
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(distDir)) {
            for (Path p : ds) if (Files.isDirectory(p) && Files.exists(p.resolve("bin"))) return p;
        }
        throw new IOException("Gradle home not found in " + distDir);
    }

    private static String sha256(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            int n;
            while ((n = in.read(buffer)) != -1) digest.update(buffer, 0, n);
        }
        StringBuilder out = new StringBuilder();
        for (byte b : digest.digest()) out.append(String.format("%02x", b));
        return out.toString();
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) return;
        Files.walk(path).sorted(Comparator.reverseOrder()).forEach(p -> {
            try { Files.deleteIfExists(p); } catch (IOException e) { throw new UncheckedIOException(e); }
        });
    }
}
