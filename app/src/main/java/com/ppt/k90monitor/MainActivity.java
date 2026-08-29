package com.ppt.k90monitor;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private TextView status;
    private RedMagicBridgeReader bridge;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        bridge = new RedMagicBridgeReader(this);
        setContentView(buildUi());
        requestNotificationPermissionIfNeeded();
        updateStatus();
    }

    @Override protected void onResume() {
        super.onResume();
        updateStatus();
    }

    private View buildUi() {
        int pad = dp(20);
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(Color.WHITE);
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));

        TextView title = new TextView(this);
        title.setText("K90 性能悬浮监控 5.4");
        title.setTextSize(24);
        title.setTextColor(Color.rgb(20, 20, 20));
        title.setPadding(0, dp(8), 0, dp(8));
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView desc = new TextView(this);
        desc.setText("独立模式：无需红魔散热器、无需 AYA、无需蓝牙，也可以直接使用 CPU / GPU温度 / 电池温度 / RAM 悬浮监控。\n\n红魔散热器数据是可选扩展：有数据时额外显示背夹温度、风扇转速和功耗；没有数据时只显示 --，不会影响系统监控。");
        desc.setTextSize(15);
        desc.setTextColor(Color.DKGRAY);
        desc.setLineSpacing(0, 1.2f);
        desc.setPadding(0, 0, 0, dp(18));
        root.addView(desc, new LinearLayout.LayoutParams(-1, -2));

        status = new TextView(this);
        status.setTextSize(15);
        status.setTextColor(Color.rgb(20, 20, 20));
        status.setPadding(dp(12), dp(12), dp(12), dp(12));
        root.addView(status, new LinearLayout.LayoutParams(-1, -2));

        Button overlay = makeButton("1. 授权悬浮窗");
        overlay.setOnClickListener(v -> requestOverlayPermission());
        root.addView(overlay, buttonLp());

        Button start = makeButton("2. 开始 K90 系统悬浮监控");
        start.setOnClickListener(v -> startMonitor());
        root.addView(start, buttonLp());

        Button stop = makeButton("停止悬浮监控");
        stop.setOnClickListener(v -> stopService(new Intent(this, OverlayMonitorService.class)));
        root.addView(stop, buttonLp());

        TextView optional = new TextView(this);
        optional.setText("红魔散热器（可选）");
        optional.setTextSize(18);
        optional.setTextColor(Color.BLACK);
        optional.setPadding(0, dp(24), 0, dp(6));
        root.addView(optional, new LinearLayout.LayoutParams(-1, -2));

        Button refresh = makeButton("刷新红魔数据状态");
        refresh.setOnClickListener(v -> updateStatus());
        root.addView(refresh, buttonLp());

        Button copyStart = makeButton("可选：复制 AYA 桥接启动命令");
        copyStart.setOnClickListener(v -> copy(bridge.shellStartCommand(), "桥接启动命令已复制"));
        root.addView(copyStart, buttonLp());

        TextView note = new TextView(this);
        note.setText("普通 Android APK 无权直接读取另一个 App 的 logcat。当前红魔官方 App 日志读取仍需要具有 shell/root 权限的桥接环境；如果不使用它，K90 系统监视器仍然完全可用。\n\n后续可加入 Shizuku 模式，让 APK 内点击按钮启动具有 shell 身份的红魔日志读取，不必手动进入 AYA。");
        note.setTextSize(13);
        note.setTextColor(Color.GRAY);
        note.setPadding(0, dp(16), 0, dp(16));
        root.addView(note, new LinearLayout.LayoutParams(-1, -2));
        return scroll;
    }

    private Button makeButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(16);
        b.setAllCaps(false);
        return b;
    }

    private LinearLayout.LayoutParams buttonLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(54));
        lp.topMargin = dp(10);
        return lp;
    }

    private void copy(String value, String toast) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("command", value));
        Toast.makeText(this, toast, Toast.LENGTH_SHORT).show();
    }

    private void requestOverlayPermission() {
        if (Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "悬浮窗权限已开启", Toast.LENGTH_SHORT).show();
            return;
        }
        startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
    }

    private void startMonitor() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "请先授权悬浮窗权限", Toast.LENGTH_LONG).show();
            requestOverlayPermission();
            return;
        }
        Intent i = new Intent(this, OverlayMonitorService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
        Toast.makeText(this, "K90 系统悬浮监控已启动", Toast.LENGTH_SHORT).show();
    }

    private void updateStatus() {
        if (status == null || bridge == null) return;
        RedMagicBridgeReader.State s = bridge.read();
        String rmState = (!s.fileExists || s.stale) ? "未连接 / 无实时数据（不影响系统监控）" : "实时数据 ✓";
        status.setText("悬浮窗权限：" + (Settings.canDrawOverlays(this) ? "已开启 ✓" : "未开启 ✗") +
                "\n系统监控：可独立使用 ✓" +
                "\n红魔扩展：" + rmState +
                "\n背夹温度：" + temp(s.clampTempC) +
                "\n风扇转速：" + rpm(s.fanRpm) +
                "\n功耗：" + RedMagicBridgeReader.formatPower(s.powerW));
    }

    private String temp(float v) { return Float.isNaN(v) ? "--" : String.format(java.util.Locale.US, "%.1f°C", v); }
    private String rpm(int v) { return v < 0 ? "--" : v + " RPM"; }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1001);
        }
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
