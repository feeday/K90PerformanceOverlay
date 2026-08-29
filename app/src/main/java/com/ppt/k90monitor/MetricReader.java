package com.ppt.k90monitor;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class MetricReader {
    private final Context context;
    private long lastCpuTotal = -1;
    private long lastCpuIdle = -1;

    public MetricReader(Context context) {
        this.context = context.getApplicationContext();
    }

    public Snapshot read() {
        Snapshot s = new Snapshot();
        s.cpuUsage = readCpuUsage();
        s.cpuFreqMHz = readCpuMaxCurrentFreqMHz();
        s.cpuTempC = readThermalTemp(new String[]{"cpu", "cpuss", "cpu-", "cpu_"});
        s.gpuUsage = readGpuUsage();
        s.gpuFreqMHz = readGpuFreqMHz();
        s.gpuTempC = readThermalTemp(new String[]{"gpu", "gpuss", "gpu-", "gpu_"});
        readMemory(s);
        s.batteryTempC = readBatteryTemp();
        return s;
    }

    private float readCpuUsage() {
        String line = readFirstLine("/proc/stat");
        if (line == null || !line.startsWith("cpu ")) return Float.NaN;
        try {
            String[] p = line.trim().split("\\s+");
            long user = parseLong(p, 1), nice = parseLong(p, 2), system = parseLong(p, 3);
            long idle = parseLong(p, 4), iowait = parseLong(p, 5), irq = parseLong(p, 6);
            long softirq = parseLong(p, 7), steal = parseLong(p, 8);
            long idleAll = idle + iowait;
            long total = user + nice + system + idle + iowait + irq + softirq + steal;
            float result = Float.NaN;
            if (lastCpuTotal >= 0 && total > lastCpuTotal) {
                long dt = total - lastCpuTotal;
                long di = idleAll - lastCpuIdle;
                result = clamp(100f * (dt - di) / (float) dt);
            }
            lastCpuTotal = total;
            lastCpuIdle = idleAll;
            return result;
        } catch (Throwable ignored) {
            return Float.NaN;
        }
    }

    private float readCpuMaxCurrentFreqMHz() {
        File root = new File("/sys/devices/system/cpu");
        File[] dirs = root.listFiles((dir, name) -> name.matches("cpu\\d+"));
        if (dirs == null) return Float.NaN;
        long maxKhz = -1;
        for (File cpu : dirs) {
            long v = readLong(cpu.getAbsolutePath() + "/cpufreq/scaling_cur_freq");
            if (v <= 0) v = readLong(cpu.getAbsolutePath() + "/cpufreq/cpuinfo_cur_freq");
            if (v > maxKhz) maxKhz = v;
        }
        return maxKhz > 0 ? maxKhz / 1000f : Float.NaN;
    }

    private float readGpuUsage() {
        String[] percentPaths = {
                "/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage",
                "/sys/devices/platform/soc/3d00000.qcom,kgsl-3d0/kgsl/kgsl-3d0/gpu_busy_percentage"
        };
        for (String path : percentPaths) {
            String v = readFirstLine(path);
            if (v != null) {
                try {
                    v = v.replace("%", "").trim();
                    return clamp(Float.parseFloat(v));
                } catch (Throwable ignored) { }
            }
        }

        String busy = readFirstLine("/sys/class/kgsl/kgsl-3d0/gpubusy");
        if (busy != null) {
            try {
                String[] p = busy.trim().split("\\s+");
                if (p.length >= 2) {
                    long a = Long.parseLong(p[0]), b = Long.parseLong(p[1]);
                    if (b > 0) return clamp(100f * a / (float) b);
                }
            } catch (Throwable ignored) { }
        }
        return Float.NaN;
    }

    private float readGpuFreqMHz() {
        String[] paths = {
                "/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq",
                "/sys/class/kgsl/kgsl-3d0/gpuclk",
                "/sys/class/devfreq/3d00000.qcom,kgsl-3d0/cur_freq",
                "/sys/class/devfreq/soc:qcom,kgsl-busmon/cur_freq"
        };
        for (String path : paths) {
            long hz = readLong(path);
            if (hz > 0) {
                if (hz > 10_000_000L) return hz / 1_000_000f;
                if (hz > 10_000L) return hz / 1000f;
                return hz;
            }
        }

        File devfreq = new File("/sys/class/devfreq");
        File[] entries = devfreq.listFiles();
        if (entries != null) {
            for (File e : entries) {
                String name = readFirstLine(e.getAbsolutePath() + "/name");
                String low = name == null ? e.getName().toLowerCase(Locale.US) : name.toLowerCase(Locale.US);
                if (low.contains("kgsl") || low.contains("gpu")) {
                    long hz = readLong(e.getAbsolutePath() + "/cur_freq");
                    if (hz > 10_000_000L) return hz / 1_000_000f;
                    if (hz > 10_000L) return hz / 1000f;
                }
            }
        }
        return Float.NaN;
    }

    private float readThermalTemp(String[] keywords) {
        File root = new File("/sys/class/thermal");
        File[] zones = root.listFiles((dir, name) -> name.startsWith("thermal_zone"));
        if (zones == null) return Float.NaN;

        List<TempCandidate> matches = new ArrayList<>();
        for (File zone : zones) {
            String type = readFirstLine(zone.getAbsolutePath() + "/type");
            if (type == null) continue;
            String t = type.toLowerCase(Locale.US);
            boolean hit = false;
            for (String k : keywords) {
                if (t.contains(k)) { hit = true; break; }
            }
            if (!hit) continue;
            long raw = readLong(zone.getAbsolutePath() + "/temp");
            float c = normalizeTemp(raw);
            if (!Float.isNaN(c) && c > -20 && c < 150) matches.add(new TempCandidate(type, c));
        }
        if (matches.isEmpty()) return Float.NaN;
        TempCandidate hottest = Collections.max(matches, Comparator.comparingDouble(a -> a.temp));
        return hottest.temp;
    }

    private void readMemory(Snapshot s) {
        try {
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            s.memTotalBytes = mi.totalMem;
            s.memAvailBytes = mi.availMem;
            s.memUsedBytes = Math.max(0, mi.totalMem - mi.availMem);
            s.memUsage = mi.totalMem > 0 ? 100f * s.memUsedBytes / (float) mi.totalMem : Float.NaN;
        } catch (Throwable ignored) {
            s.memUsage = Float.NaN;
        }
    }

    private float readBatteryTemp() {
        try {
            Intent i = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (i == null) return Float.NaN;
            int tenth = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Integer.MIN_VALUE);
            return tenth == Integer.MIN_VALUE ? Float.NaN : tenth / 10f;
        } catch (Throwable ignored) {
            return Float.NaN;
        }
    }

    private static float normalizeTemp(long raw) {
        if (raw == Long.MIN_VALUE) return Float.NaN;
        long a = Math.abs(raw);
        if (a > 10000) return raw / 1000f;
        if (a > 1000) return raw / 100f;
        if (a > 150) return raw / 10f;
        return raw;
    }

    private static String readFirstLine(String path) {
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            return br.readLine();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static long readLong(String path) {
        String s = readFirstLine(path);
        if (s == null) return Long.MIN_VALUE;
        try { return Long.parseLong(s.trim().split("\\s+")[0]); }
        catch (Throwable ignored) { return Long.MIN_VALUE; }
    }

    private static long parseLong(String[] p, int i) {
        if (i >= p.length) return 0;
        try { return Long.parseLong(p[i]); } catch (Throwable ignored) { return 0; }
    }

    private static float clamp(float v) {
        return Math.max(0f, Math.min(100f, v));
    }

    private static class TempCandidate {
        final String name;
        final float temp;
        TempCandidate(String name, float temp) { this.name = name; this.temp = temp; }
    }

    public static class Snapshot {
        public float cpuUsage = Float.NaN;
        public float cpuFreqMHz = Float.NaN;
        public float cpuTempC = Float.NaN;
        public float gpuUsage = Float.NaN;
        public float gpuFreqMHz = Float.NaN;
        public float gpuTempC = Float.NaN;
        public float memUsage = Float.NaN;
        public long memTotalBytes;
        public long memAvailBytes;
        public long memUsedBytes;
        public float batteryTempC = Float.NaN;
    }
}
