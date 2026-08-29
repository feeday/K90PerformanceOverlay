package com.ppt.k90monitor;

import android.content.pm.PackageManager;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

import rikka.shizuku.Shizuku;

public final class ShizukuBridge {
    public static final int REQUEST_CODE = 4100;

    private ShizukuBridge() { }

    public static boolean isBinderReady() {
        try {
            return Shizuku.pingBinder();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean hasPermission() {
        try {
            return isBinderReady() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static void requestPermission() {
        try {
            Shizuku.requestPermission(REQUEST_CODE);
        } catch (Throwable ignored) {
        }
    }

    public static String getBackendInfo() {
        try {
            if (!isBinderReady()) return "Shizuku 未运行";
            int uid = Shizuku.getUid();
            String ctx = Shizuku.getSELinuxContext();
            if (!hasPermission()) return "Shizuku 已运行，未授权";
            return "Shizuku 已授权 UID=" + uid + (ctx == null ? "" : " " + ctx);
        } catch (Throwable e) {
            return "Shizuku 状态未知";
        }
    }

    /**
     * Executes a small read-only shell command through Shizuku.
     * Shizuku API 13 keeps newProcess as a private transition API; reflection is used here
     * only for simple shell reads so the app can stay lightweight without a persistent UserService.
     */
    public static String exec(String command) {
        if (!hasPermission()) return null;
        Object remote = null;
        try {
            Method newProcess = Shizuku.class.getDeclaredMethod("newProcess", String[].class, String[].class, String.class);
            newProcess.setAccessible(true);
            String[] argv = new String[]{"sh", "-c", command};
            remote = newProcess.invoke(null, argv, null, null);
            if (remote == null) return null;

            Method waitForTimeout = null;
            try {
                waitForTimeout = remote.getClass().getMethod("waitFor", long.class, TimeUnit.class);
            } catch (Throwable ignored) { }

            if (waitForTimeout != null) {
                Object ok = waitForTimeout.invoke(remote, 1200L, TimeUnit.MILLISECONDS);
                if (ok instanceof Boolean && !((Boolean) ok)) {
                    destroy(remote);
                    return null;
                }
            } else {
                Method waitFor = remote.getClass().getMethod("waitFor");
                waitFor.invoke(remote);
            }

            Method getInputStream = remote.getClass().getMethod("getInputStream");
            InputStream in = (InputStream) getInputStream.invoke(remote);
            if (in == null) return null;
            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(in))) {
                String line;
                int lines = 0;
                while ((line = br.readLine()) != null && lines++ < 32) {
                    if (sb.length() > 0) sb.append('\n');
                    sb.append(line);
                }
            }
            String result = sb.toString().trim();
            return result.isEmpty() ? null : result;
        } catch (Throwable ignored) {
            return null;
        } finally {
            if (remote != null) destroy(remote);
        }
    }

    public static String readFirstLine(String path) {
        if (path == null || path.isEmpty()) return null;
        String escaped = path.replace("'", "'\\''");
        String out = exec("cat '" + escaped + "' 2>/dev/null | head -n 1");
        if (out == null) return null;
        int n = out.indexOf('\n');
        return (n >= 0 ? out.substring(0, n) : out).trim();
    }

    private static void destroy(Object remote) {
        try {
            Method destroy = remote.getClass().getMethod("destroy");
            destroy.invoke(remote);
        } catch (Throwable ignored) { }
    }
}
