package com.ppt.k90monitor;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.net.TrafficStats;
import android.os.SystemClock;
import android.view.Display;

/** Device-wide network throughput plus current display refresh rate. */
public final class NetworkDisplayReader {
    public static final class State {
        public double downBytesPerSec = Double.NaN;
        public double upBytesPerSec = Double.NaN;
        public float refreshRateHz = Float.NaN;
    }

    private final Context context;
    private long lastRx = -1L;
    private long lastTx = -1L;
    private long lastAtMs = -1L;

    public NetworkDisplayReader(Context context) {
        this.context = context.getApplicationContext();
    }

    public State read() {
        State s = new State();
        long now = SystemClock.elapsedRealtime();
        long rx = TrafficStats.getTotalRxBytes();
        long tx = TrafficStats.getTotalTxBytes();

        if (rx != TrafficStats.UNSUPPORTED && tx != TrafficStats.UNSUPPORTED &&
                lastRx >= 0L && lastTx >= 0L && lastAtMs > 0L && now > lastAtMs) {
            long dt = now - lastAtMs;
            long drx = Math.max(0L, rx - lastRx);
            long dtx = Math.max(0L, tx - lastTx);
            s.downBytesPerSec = drx * 1000.0 / dt;
            s.upBytesPerSec = dtx * 1000.0 / dt;
        }

        if (rx != TrafficStats.UNSUPPORTED) lastRx = rx;
        if (tx != TrafficStats.UNSUPPORTED) lastTx = tx;
        lastAtMs = now;
        s.refreshRateHz = readRefreshRate();
        return s;
    }

    private float readRefreshRate() {
        try {
            DisplayManager dm = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
            if (dm == null) return Float.NaN;
            Display display = dm.getDisplay(Display.DEFAULT_DISPLAY);
            if (display == null) return Float.NaN;
            float hz = display.getRefreshRate();
            return hz > 1f && hz < 1000f ? hz : Float.NaN;
        } catch (Throwable ignored) {
            return Float.NaN;
        }
    }
}
