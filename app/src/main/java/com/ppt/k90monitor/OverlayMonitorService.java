package com.ppt.k90monitor;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

public class OverlayMonitorService extends Service {
    public static final String PREFS = "k90_monitor_prefs";
    public static final String KEY_MODE = "display_mode";
    public static final String MODE_TEMP = "temperature";
    public static final String MODE_FULL = "full";

    private static final int NOTIFICATION_ID = 9001;
    private static final String CHANNEL_ID = "k90_monitor";

    private WindowManager windowManager;
    private View overlay;
    private WindowManager.LayoutParams lp;
    private TextView text;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private MetricReader reader;
    private RedMagicBridgeReader redMagic;
    private NetworkDisplayReader netDisplay;
    private SharedPreferences prefs;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            updateMetrics();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        reader = new MetricReader(this);
        redMagic = new RedMagicBridgeReader(this);
        netDisplay = new NetworkDisplayReader(this);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification());
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "未获得悬浮窗权限", Toast.LENGTH_LONG).show();
            stopSelf();
            return;
        }
        showOverlay();
        handler.post(tick);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "STOP".equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        return START_STICKY;
    }

    private void showOverlay() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(10), dp(7), dp(10), dp(7));
        box.setGravity(Gravity.START);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xD91B1B1B);
        bg.setCornerRadius(dp(10));
        bg.setStroke(dp(1), 0x55FFFFFF);
        box.setBackground(bg);

        TextView title = new TextView(this);
        title.setText("K90 MONITOR 5.5");
        title.setTextColor(0xFFB8E1FF);
        title.setTextSize(10);
        title.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        box.addView(title, new LinearLayout.LayoutParams(-2, -2));

        text = new TextView(this);
        text.setText("正在读取…");
        text.setTextColor(Color.WHITE);
        text.setTextSize(12);
        text.setTypeface(Typeface.MONOSPACE, Typeface.NORMAL);
        text.setIncludeFontPadding(false);
        text.setLineSpacing(0, 1.05f);
        box.addView(text, new LinearLayout.LayoutParams(-2, -2));

        TextView close = new TextView(this);
        close.setText("长按关闭");
        close.setTextColor(0xFFAAAAAA);
        close.setTextSize(9);
        close.setPadding(0, dp(3), 0, 0);
        box.addView(close, new LinearLayout.LayoutParams(-2, -2));

        lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : 2002,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = dp(10);
        lp.y = dp(90);

        box.setOnTouchListener(new DragTouchListener());
        box.setOnLongClickListener(v -> { stopSelf(); return true; });
        overlay = box;
        windowManager.addView(overlay, lp);
    }

    private void updateMetrics() {
        if (text == null) return;
        MetricReader.Snapshot s = reader.read();
        RedMagicBridgeReader.State r = redMagic.read();
        NetworkDisplayReader.State n = netDisplay.read();

        String mode = prefs.getString(KEY_MODE, MODE_FULL);
        boolean hasLiveRedMagic = r.fileExists && !r.stale && r.fanRpm >= 0;

        if (MODE_TEMP.equals(mode)) {
            String clamp = hasLiveRedMagic && !Float.isNaN(r.clampTempC)
                    ? temp(r.clampTempC)
                    : "--";
            String out = "CPU " + temp(s.cpuTempC)
                    + "   GPU " + temp(s.gpuTempC)
                    + "   BAT " + temp(s.batteryTempC)
                    + "   CLAMP " + clamp;
            text.setText(out);
            return;
        }

        String cpu = String.format(Locale.US, "CPU %s  %s  %s", pct(s.cpuUsage), freq(s.cpuFreqMHz), temp(s.cpuTempC));
        String thermal = String.format(Locale.US, "GPU %s   BAT %s", temp(s.gpuTempC), temp(s.batteryTempC));
        String mem = String.format(Locale.US, "RAM %s / %s  %s", gb(s.memUsedBytes), gb(s.memTotalBytes), pct(s.memUsage));
        String net = "NET ↓" + speed(n.downBytesPerSec) + "  ↑" + speed(n.upBytesPerSec);

        StringBuilder out = new StringBuilder();
        out.append(cpu).append('\n')
                .append(thermal).append('\n')
                .append(mem).append('\n')
                .append(net);

        if (hasLiveRedMagic) {
            out.append("\n\nREDMAGIC")
                    .append("\nFAN ").append(rpm(r.fanRpm))
                    .append("   CLAMP ").append(temp(r.clampTempC))
                    .append("\nPWR ").append(RedMagicBridgeReader.formatPower(r.powerW));
        }

        text.setText(out.toString());
    }

    private String pct(float v) { return Float.isNaN(v) ? "N/A" : String.format(Locale.US, "%5.1f%%", v); }
    private String freq(float mhz) {
        if (Float.isNaN(mhz)) return "N/A";
        return mhz >= 1000f ? String.format(Locale.US, "%4.2fGHz", mhz / 1000f) : String.format(Locale.US, "%4.0fMHz", mhz);
    }
    private String temp(float c) { return Float.isNaN(c) ? "--" : String.format(Locale.US, "%4.1f°C", c); }
    private String gb(long bytes) { return bytes <= 0 ? "N/A" : String.format(Locale.US, "%.1fG", bytes / 1073741824.0); }
    private String rpm(int v) { return v < 0 ? "---- RPM" : String.format(Locale.US, "%4d RPM", v); }

    private String speed(double bytesPerSec) {
        if (Double.isNaN(bytesPerSec)) return "--";
        if (bytesPerSec >= 1024 * 1024) return String.format(Locale.US, "%.1fMB/s", bytesPerSec / (1024.0 * 1024.0));
        if (bytesPerSec >= 1024) return String.format(Locale.US, "%.0fKB/s", bytesPerSec / 1024.0);
        return String.format(Locale.US, "%.0fB/s", bytesPerSec);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "性能悬浮监控", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("K90 系统性能监控；红魔散热器遥测可选显示");
            nm.createNotificationChannel(ch);
        }
    }

    private Notification buildNotification() {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent content = PendingIntent.getActivity(this, 1, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent stop = new Intent(this, OverlayMonitorService.class);
        stop.setAction("STOP");
        PendingIntent stopPi = PendingIntent.getService(this, 2, stop, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
        return b.setContentTitle("K90 性能悬浮监控 5.5")
                .setContentText("支持温度模式 / 全部模式")
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentIntent(content)
                .setOngoing(true)
                .addAction(new Notification.Action.Builder(null, "停止", stopPi).build())
                .build();
    }

    @Override public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (windowManager != null && overlay != null) {
            try { windowManager.removeView(overlay); } catch (Throwable ignored) { }
        }
        overlay = null;
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private class DragTouchListener implements View.OnTouchListener {
        float downRawX, downRawY; int downX, downY; long downAt; boolean moved;
        @Override public boolean onTouch(View v, MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downRawX = e.getRawX(); downRawY = e.getRawY(); downX = lp.x; downY = lp.y; downAt = System.currentTimeMillis(); moved = false; return true;
                case MotionEvent.ACTION_MOVE:
                    float dx = e.getRawX() - downRawX, dy = e.getRawY() - downRawY;
                    if (Math.abs(dx) > dp(3) || Math.abs(dy) > dp(3)) moved = true;
                    lp.x = downX + Math.round(dx); lp.y = downY + Math.round(dy);
                    try { windowManager.updateViewLayout(overlay, lp); } catch (Throwable ignored) { }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (!moved && System.currentTimeMillis() - downAt >= 650) stopSelf();
                    return true;
                default: return true;
            }
        }
    }
}
