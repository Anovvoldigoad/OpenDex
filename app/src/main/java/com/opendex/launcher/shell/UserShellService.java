package com.opendex.launcher.shell;

import android.content.Context;
import android.system.Os;

import com.opendex.launcher.IUserShellService;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/** Runs in a Shizuku UserService process using shell/root identity. */
public final class UserShellService extends IUserShellService.Stub {
    public UserShellService() {}
    public UserShellService(Context context) {}

    @Override
    public String exec(String command) {
        if (command == null || command.trim().isEmpty()) return "EXIT=2\nempty command";
        Process process = null;
        try {
            process = new ProcessBuilder("/system/bin/sh", "-c", command)
                    .redirectErrorStream(true).start();
            if (!process.waitFor(25, TimeUnit.SECONDS)) {
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

    /** Reserved destroy transaction required by Shizuku UserService. */
    @Override public void destroy() { System.exit(0); }

    private static String readAll(InputStream in) throws Exception {
        try (InputStream input = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = input.read(buffer)) != -1) out.write(buffer, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
