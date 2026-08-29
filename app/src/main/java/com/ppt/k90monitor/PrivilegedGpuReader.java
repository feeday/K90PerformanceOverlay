package com.ppt.k90monitor;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PrivilegedGpuReader {
    private static final Pattern NUMBER = Pattern.compile("[-+]?[0-9]*\\.?[0-9]+");
    private long lastBusy = -1;
    private long lastTotal = -1;

    public float readUsage() {
        if (!ShizukuBridge.hasPermission()) return Float.NaN;
        String[] percentPaths = {
                "/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage",
                "/sys/class/kgsl/kgsl-3d0/devfreq/gpu_load",
                "/sys/class/kgsl/kgsl-3d0/gpu_load"
        };
        for (String path : percentPaths) {
            String s = ShizukuBridge.readFirstLine(path);
            float v = parsePercent(s);
            if (!Float.isNaN(v)) return v;
        }

        String busyPair = ShizukuBridge.readFirstLine("/sys/class/kgsl/kgsl-3d0/gpubusy");
        if (busyPair != null) {
            try {
                String[] p = busyPair.trim().split("\\s+");
                if (p.length >= 2) {
                    long busy = Long.parseLong(p[0]);
                    long total = Long.parseLong(p[1]);
                    return busyDelta(busy, total);
                }
            } catch (Throwable ignored) { }
        }
        return Float.NaN;
    }

    public float readFreqMHz() {
        if (!ShizukuBridge.hasPermission()) return Float.NaN;
        String[] paths = {
                "/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq",
                "/sys/class/kgsl/kgsl-3d0/clock_mhz",
                "/sys/class/kgsl/kgsl-3d0/gpuclk",
                "/sys/class/kgsl/kgsl-3d0/devfreq/target_freq"
        };
        for (String path : paths) {
            String s = ShizukuBridge.readFirstLine(path);
            long raw = parseLong(s);
            float mhz = normalizeFreq(raw, path);
            if (!Float.isNaN(mhz) && mhz > 0) return mhz;
        }
        return Float.NaN;
    }

    private float parsePercent(String s) {
        if (s == null) return Float.NaN;
        Matcher m = NUMBER.matcher(s.replace(',', '.'));
        if (!m.find()) return Float.NaN;
        try {
            float v = Float.parseFloat(m.group());
            return v >= 0f && v <= 100f ? clamp(v) : Float.NaN;
        } catch (Throwable ignored) {
            return Float.NaN;
        }
    }

    private long parseLong(String s) {
        if (s == null) return Long.MIN_VALUE;
        Matcher m = NUMBER.matcher(s);
        if (!m.find()) return Long.MIN_VALUE;
        try {
            return (long) Double.parseDouble(m.group());
        } catch (Throwable ignored) {
            return Long.MIN_VALUE;
        }
    }

    private float busyDelta(long busy, long total) {
        if (busy < 0 || total <= 0 || busy > total) return Float.NaN;
        float out;
        if (lastBusy >= 0 && lastTotal >= 0 && busy >= lastBusy && total > lastTotal) {
            long db = busy - lastBusy;
            long dt = total - lastTotal;
            out = dt > 0 ? clamp(100f * db / (float) dt) : Float.NaN;
        } else {
            out = clamp(100f * busy / (float) total);
        }
        lastBusy = busy;
        lastTotal = total;
        return out;
    }

    private float normalizeFreq(long raw, String path) {
        if (raw == Long.MIN_VALUE || raw <= 0) return Float.NaN;
        String low = path.toLowerCase(Locale.US);
        if (low.contains("clock_mhz")) return raw;
        if (raw >= 10_000_000L) return raw / 1_000_000f;
        if (raw >= 10_000L) return raw / 1000f;
        return raw;
    }

    private float clamp(float v) {
        return Math.max(0f, Math.min(100f, v));
    }
}
