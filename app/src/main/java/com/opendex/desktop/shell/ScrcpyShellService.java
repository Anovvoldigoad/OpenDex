package com.opendex.desktop.shell;

import android.content.Context;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.os.ParcelFileDescriptor;
import android.system.Os;

import androidx.annotation.Keep;

import com.opendex.desktop.IScrcpyShellService;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Runs as Shizuku UserService (shell UID). Its job is intentionally small:
 * install/start the official scrcpy-server and bridge its abstract Unix sockets
 * to the app by transferring duplicated file descriptors over Binder.
 */
public final class ScrcpyShellService extends IScrcpyShellService.Stub {
    private static final String SERVER_DIR = "/data/local/tmp/opendex";
    private static final String SERVER_PATH = SERVER_DIR + "/scrcpy-server-v4.1";
    private static final Pattern DISPLAY_ID = Pattern.compile("New display: .*\\(id=(\\d+)\\)");

    private final Context context;
    private final Map<Integer, SessionProc> sessions = new HashMap<>();

    public ScrcpyShellService() { this.context = null; }

    @Keep
    public ScrcpyShellService(Context context) { this.context = context; }

    @Override public int remoteUid() { return Os.getuid(); }

    @Override
    public synchronized boolean installScrcpyServer(byte[] data, String expectedSha256) {
        if (data == null || data.length < 1024 || expectedSha256 == null) return false;
        try {
            String actual = sha256(data);
            if (!actual.equalsIgnoreCase(expectedSha256.trim())) return false;
            File dir = new File(SERVER_DIR);
            if (!dir.exists() && !dir.mkdirs()) return false;
            File out = new File(SERVER_PATH);
            if (out.isFile() && expectedSha256.equalsIgnoreCase(sha256(out))) return true;
            try (FileOutputStream fos = new FileOutputStream(out, false)) {
                fos.write(data);
                fos.flush();
                fos.getFD().sync();
            }
            out.setReadable(true, false);
            out.setWritable(true, true);
            return expectedSha256.equalsIgnoreCase(sha256(out));
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public synchronized String prepareEnvironment() {
        // Undo global flags set by older OpenDex/DroidUP experiments. The new engine does not
        // depend on native Android freeform at all, and leaving these flags enabled can cause
        // Android 16 to inject desktop captions inside scrcpy virtual displays.
        StringBuilder sb = new StringBuilder();
        sb.append(runQuick("settings delete global enable_freeform_support"));
        sb.append(" | ").append(runQuick("settings delete global force_resizable_activities"));
        return "uid=" + Os.getuid() + " | legacyFreeformReset | " + compact(sb.toString());
    }

    @Override
    public synchronized String startScrcpyServer(int scid, int width, int height, int dpi, int bitRate, int maxFps) {
        stopScrcpyServer(scid);
        File server = new File(SERVER_PATH);
        if (!server.isFile()) return "ERROR|scrcpy-server missing";

        width = clamp(width, 320, 2800);
        height = clamp(height, 240, 2800);
        dpi = clamp(dpi, 120, 640);
        bitRate = clamp(bitRate, 1_000_000, 30_000_000);
        maxFps = clamp(maxFps, 15, 120);
        String hex = String.format(Locale.US, "%08x", scid);

        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "/system/bin/app_process", "/", "com.genymobile.scrcpy.Server", "4.1",
                    "scid=" + hex,
                    "log_level=info",
                    "video=true",
                    "audio=false",
                    "control=true",
                    "video_codec=h264",
                    "video_bit_rate=" + bitRate,
                    "max_fps=" + maxFps,
                    "new_display=" + width + "x" + height + "/" + dpi,
                    "vd_destroy_content=true",
                    "vd_system_decorations=false",
                    "flex_display=true",
                    "display_ime_policy=local",
                    "tunnel_forward=true",
                    "cleanup=false",
                    "power_on=false",
                    "keep_active=true",
                    "clipboard_autosync=false",
                    "send_device_meta=false",
                    "send_dummy_byte=false",
                    "send_frame_meta=true",
                    "send_stream_meta=true"
            );
            pb.environment().put("CLASSPATH", SERVER_PATH);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            SessionProc session = new SessionProc(scid, process);
            sessions.put(scid, session);
            startLogReader(session);
            return "OK|scid=" + hex + "|" + width + "x" + height + "@" + dpi
                    + "|uid=" + Os.getuid() + "|cleanup=false";
        } catch (Throwable t) {
            return "ERROR|start failed|" + shortError(t);
        }
    }

    @Override
    public ParcelFileDescriptor connectScrcpy(int scid) {
        String socketName = "scrcpy_" + String.format(Locale.US, "%08x", scid);
        Throwable last = null;
        for (int i = 0; i < 80; i++) {
            LocalSocket socket = null;
            try {
                socket = new LocalSocket();
                socket.connect(new LocalSocketAddress(socketName, LocalSocketAddress.Namespace.ABSTRACT));
                socket.setReceiveBufferSize(1 << 20);
                socket.setSendBufferSize(256 << 10);
                ParcelFileDescriptor dup = ParcelFileDescriptor.dup(socket.getFileDescriptor());
                socket.close();
                return dup;
            } catch (Throwable t) {
                last = t;
                if (socket != null) try { socket.close(); } catch (Throwable ignored) {}
                try { Thread.sleep(75); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
            }
        }
        SessionProc s;
        synchronized (this) { s = sessions.get(scid); }
        if (s != null) s.appendLog("connectScrcpy failed: " + shortError(last));
        return null;
    }

    @Override
    public synchronized String sessionInfo(int scid) {
        SessionProc s = sessions.get(scid);
        if (s == null) return "missing session " + scid;
        return "scid=" + String.format(Locale.US, "%08x", scid)
                + "|alive=" + s.process.isAlive()
                + "|display=" + s.displayId
                + "|log=" + compact(s.log.toString());
    }

    @Override
    public synchronized void stopScrcpyServer(int scid) {
        SessionProc s = sessions.remove(scid);
        if (s == null) return;
        try { s.process.destroy(); } catch (Throwable ignored) {}
        try {
            if (!s.process.waitFor(500, TimeUnit.MILLISECONDS)) s.process.destroyForcibly();
        } catch (Throwable ignored) {}
    }

    @Override
    public String prepareSessionDisplay(int scid) {
        SessionProc session;
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(1500);
        do {
            synchronized (this) { session = sessions.get(scid); }
            if (session == null) return "ERROR|session missing";
            if (session.displayId >= 0) break;
            try { Thread.sleep(25); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return "ERROR|interrupted";
            }
        } while (System.nanoTime() < deadline);

        int displayId = session.displayId;
        if (displayId < 0) return "WARN|display id not observed yet|" + compact(session.log.toString());

        String mode = runQuick("wm set-display-windowing-mode -d " + displayId + " 1");
        String orientation = runQuick("wm set-ignore-orientation-request -d " + displayId + " true");
        return "OK|display=" + displayId + "|fullscreen=" + compact(mode) + "|orientation=" + compact(orientation);
    }

    @Override
    public synchronized String exec(String command) {
        if (command == null || command.trim().isEmpty()) return "EXIT=2\\nempty";
        Process p = null;
        try {
            p = new ProcessBuilder("/system/bin/sh", "-c", command).redirectErrorStream(true).start();
            if (!p.waitFor(15, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return "EXIT=124\\ntimeout";
            }
            return "EXIT=" + p.exitValue() + "\\n" + readAll(p.getInputStream());
        } catch (Throwable t) {
            return "EXIT=127\\n" + shortError(t);
        } finally {
            if (p != null) try { p.destroy(); } catch (Throwable ignored) {}
        }
    }

    @Override
    public synchronized void destroy() {
        for (Integer scid : sessions.keySet().toArray(new Integer[0])) stopScrcpyServer(scid);
        System.exit(0);
    }

    private void startLogReader(SessionProc session) {
        Thread t = new Thread(() -> {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(session.process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    session.appendLog(line);
                    Matcher m = DISPLAY_ID.matcher(line);
                    if (m.find()) {
                        try {
                            int id = Integer.parseInt(m.group(1));
                            session.displayId = id;
                        } catch (Throwable ignored) {}
                    }
                }
            } catch (Throwable t1) {
                session.appendLog("logReader: " + shortError(t1));
            }
        }, "scrcpy-log-" + Integer.toHexString(session.scid));
        t.setDaemon(true);
        t.start();
    }

    private String runQuick(String command) {
        Process p = null;
        try {
            p = new ProcessBuilder("/system/bin/sh", "-c", command).redirectErrorStream(true).start();
            if (!p.waitFor(3, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return "timeout";
            }
            return "exit=" + p.exitValue() + ":" + readAll(p.getInputStream());
        } catch (Throwable t) {
            return shortError(t);
        } finally {
            if (p != null) try { p.destroy(); } catch (Throwable ignored) {}
        }
    }

    private static final class SessionProc {
        final int scid;
        final Process process;
        final StringBuilder log = new StringBuilder();
        volatile int displayId = -1;
        SessionProc(int scid, Process process) { this.scid = scid; this.process = process; }
        synchronized void appendLog(String line) {
            if (log.length() > 4096) log.delete(0, Math.min(2048, log.length()));
            if (log.length() > 0) log.append(" / ");
            log.append(line == null ? "" : line.replace('\n', ' ').replace('\r', ' '));
        }
    }

    private static String sha256(byte[] data) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        md.update(data);
        return hex(md.digest());
    }

    private static String sha256(File file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] buf = new byte[8192];
        try (FileInputStream in = new FileInputStream(file)) {
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
        }
        return hex(md.digest());
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format(Locale.US, "%02x", b & 0xff));
        return sb.toString();
    }

    private static String readAll(InputStream input) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = input.read(buf)) >= 0) out.write(buf, 0, n);
        return out.toString(StandardCharsets.UTF_8.name()).trim();
    }

    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
    private static String compact(String value) { return value == null ? "" : value.replace('\n', ' ').replace('\r', ' ').trim(); }
    private static String shortError(Throwable t) {
        if (t == null) return "unknown";
        String m = t.getMessage();
        return t.getClass().getSimpleName() + (m == null ? "" : ":" + m);
    }
}
