package com.opendex.droiduphost.shell;

import android.content.Context;
import android.system.Os;

import com.opendex.droiduphost.IHostShellService;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/** Shizuku UserService running as shell/root. */
public final class HostShellService extends IHostShellService.Stub {
    private static final File PAYLOAD = new File("/data/local/tmp/DroidUP-Dex-Launcher.apk");
    private FileOutputStream payloadOut;

    public HostShellService() {}
    public HostShellService(Context context) {}

    @Override
    public synchronized String exec(String command) {
        if (command == null || command.trim().isEmpty()) return "EXIT=2\nempty command";
        Process process = null;
        try {
            process = new ProcessBuilder("/system/bin/sh", "-c", command)
                    .redirectErrorStream(true).start();
            if (!process.waitFor(30, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return "EXIT=124\ntimeout";
            }
            String output = readAll(process.getInputStream());
            return "EXIT=" + process.exitValue() + "\n" + output;
        } catch (Throwable t) {
            return "EXIT=127\n" + t.getClass().getSimpleName() + ": " + t.getMessage();
        } finally {
            if (process != null) process.destroy();
        }
    }

    @Override public int remoteUid() { return Os.getuid(); }

    @Override
    public synchronized boolean beginPayload() {
        closePayload();
        try {
            if (PAYLOAD.exists() && !PAYLOAD.delete()) return false;
            payloadOut = new FileOutputStream(PAYLOAD, false);
            return true;
        } catch (Throwable t) {
            closePayload();
            return false;
        }
    }

    @Override
    public synchronized boolean appendPayload(byte[] data) {
        if (payloadOut == null || data == null) return false;
        try {
            payloadOut.write(data);
            return true;
        } catch (Throwable t) {
            closePayload();
            return false;
        }
    }

    @Override
    public synchronized String installPayload() {
        closePayload();
        if (!PAYLOAD.isFile() || PAYLOAD.length() < 1024) return "EXIT=2\npayload missing";
        String result = exec("chmod 0644 " + PAYLOAD.getAbsolutePath()
                + "; pm install -r -d " + PAYLOAD.getAbsolutePath()
                + "; rc=$?; rm -f " + PAYLOAD.getAbsolutePath() + "; exit $rc");
        return result;
    }

    private synchronized void closePayload() {
        if (payloadOut != null) {
            try { payloadOut.flush(); } catch (Throwable ignored) {}
            try { payloadOut.close(); } catch (Throwable ignored) {}
            payloadOut = null;
        }
    }

    @Override public void destroy() {
        closePayload();
        System.exit(0);
    }

    private static String readAll(InputStream in) throws Exception {
        try (InputStream input = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = input.read(buffer)) != -1) out.write(buffer, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
