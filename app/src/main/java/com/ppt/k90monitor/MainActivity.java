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
        title.setText("K90 性能悬浮监控 5.3");
        title.setTextSize(24);
        title.setTextColor(Color.rgb(20, 20, 20));
        title.setPadding(0, dp(8), 0, dp(8));
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView desc = new TextView(this);
        desc.setText("红魔散热器使用 AYA 文件桥接：红魔官方 App 负责连接/鉴权；AYA shell 读取 neoDevice 日志并持续写入本 App 目录；本 App 只读取文件显示背夹温度、风扇转速和功耗。无需 READ_LOGS，也不会抢占 BLE。\n\n首次进入 5.3 会自动生成 redmagic_bridge.sh。");
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

        Button copyStart = makeButton("2. 复制 AYA 桥接启动命令");
        copyStart.setOnClickListener(v -> copy(bridge.shellStartCommand(), "桥接启动命令已复制"));
        root.addView(copyStart, buttonLp());

        Button copyStop = makeButton("复制 AYA 桥接停止命令");
        copyStop.setOnClickListener(v -> copy(bridge.shellStopCommand(), "桥接停止命令已复制"));
        root.addView(copyStop, buttonLp());

        Button start = makeButton("3. 开始悬浮监控");
        start.setOnClickListener(v -> startMonitor());
        root.addView(start, buttonLp());

        Button refresh = makeButton("刷新桥接状态");
        refresh.setOnClickListener(v -> updateStatus());
        root.addView(refresh, buttonLp());

        Button stop = makeButton("停止悬浮监控");
        stop.setOnClickListener(v -> stopService(new Intent(this, OverlayMonitorService.class)));
        root.addView(stop, buttonLp());

        TextView steps = new TextView(this);
        steps.setText("使用顺序：\n\n① 打开红魔官方 App，确认散热器正常显示温度/转速/功耗。\n② 在 AYA shell 粘贴“桥接启动命令”。\n③ 回到本 App 点“开始悬浮监控”。\n\n桥接文件：\n" + bridge.metricsPathForShell() + "\n\nAYA 启动命令：\n" + bridge.shellStartCommand());
        steps.setTextSize(13);
        steps.setTextColor(Color.GRAY);
        steps.setPadding(0, dp(16), 0, dp(16));
        root.addView(steps, new LinearLayout.LayoutParams(-1, -2));
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
        Toast.makeText(this, "悬浮监控已启动", Toast.LENGTH_SHORT).show();
    }

    private void updateStatus() {
        if (status == null || bridge == null) return;
        RedMagicBridgeReader.State s = bridge.read();
        status.setText("悬浮窗权限：" + (Settings.canDrawOverlays(this) ? "已开启 ✓" : "未开启 ✗") +
                "\n桥接状态：" + s.message +
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
