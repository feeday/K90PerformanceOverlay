package com.ppt.k90monitor;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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
    private SharedPreferences prefs;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        bridge = new RedMagicBridgeReader(this);
        prefs = getSharedPreferences(OverlayMonitorService.PREFS, MODE_PRIVATE);
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
        title.setText("K90 性能悬浮监控 5.5");
        title.setTextSize(24);
        title.setTextColor(Color.rgb(20, 20, 20));
        title.setPadding(0, dp(8), 0, dp(8));
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView desc = new TextView(this);
        desc.setText("支持两种显示模式：\n\n温度模式：只显示 CPU 温度、GPU 温度；红魔实时数据存在时再显示背夹温度。\n\n全部模式：显示 CPU 占用/频率/温度、GPU温度、电池温度、RAM、实时上传/下载网速、当前屏幕刷新率；红魔实时数据存在时再追加背夹温度、RPM 和功耗。");
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

        TextView modeTitle = new TextView(this);
        modeTitle.setText("2. 选择显示模式");
        modeTitle.setTextSize(18);
        modeTitle.setTextColor(Color.BLACK);
        modeTitle.setPadding(0, dp(18), 0, dp(4));
        root.addView(modeTitle, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        Button tempMode = makeButton("温度模式");
        tempMode.setOnClickListener(v -> setMode(OverlayMonitorService.MODE_TEMP));
        row.addView(tempMode, weightedButton());
        Button fullMode = makeButton("全部模式");
        fullMode.setOnClickListener(v -> setMode(OverlayMonitorService.MODE_FULL));
        row.addView(fullMode, weightedButton());
        root.addView(row, new LinearLayout.LayoutParams(-1, -2));

        Button start = makeButton("3. 开始悬浮监控");
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
        note.setText("说明：全部模式里的“DISPLAY xx Hz”是当前屏幕刷新率，不冒充游戏真实渲染 FPS。普通 APK 无法可靠读取其他游戏的真实 FPS；后续可通过 Shizuku/系统级接口扩展。\n\n没有红魔实时 RPM 时，悬浮窗不会显示任何红魔相关内容。");
        note.setTextSize(13);
        note.setTextColor(Color.GRAY);
        note.setPadding(0, dp(16), 0, dp(16));
        root.addView(note, new LinearLayout.LayoutParams(-1, -2));
        return scroll;
    }

    private void setMode(String mode) {
        prefs.edit().putString(OverlayMonitorService.KEY_MODE, mode).apply();
        Toast.makeText(this, OverlayMonitorService.MODE_TEMP.equals(mode) ? "已切换：温度模式" : "已切换：全部模式", Toast.LENGTH_SHORT).show();
        updateStatus();
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

    private LinearLayout.LayoutParams weightedButton() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(54), 1f);
        lp.setMargins(dp(2), dp(5), dp(2), dp(5));
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
        Toast.makeText(this, "K90 悬浮监控已启动", Toast.LENGTH_SHORT).show();
    }

    private void updateStatus() {
        if (status == null || bridge == null) return;
        RedMagicBridgeReader.State s = bridge.read();
        String rmState = (!s.fileExists || s.stale || s.fanRpm < 0) ? "无实时数据" : "实时数据 ✓";
        String mode = prefs.getString(OverlayMonitorService.KEY_MODE, OverlayMonitorService.MODE_FULL);
        status.setText("悬浮窗权限：" + (Settings.canDrawOverlays(this) ? "已开启 ✓" : "未开启 ✗") +
                "\n显示模式：" + (OverlayMonitorService.MODE_TEMP.equals(mode) ? "温度模式" : "全部模式") +
                "\n系统监控：可独立使用 ✓" +
                "\n红魔扩展：" + rmState);
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1001);
        }
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
