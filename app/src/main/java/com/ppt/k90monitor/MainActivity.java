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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildUi());
        requestNotificationPermissionIfNeeded();
        updateStatus();
    }

    @Override
    protected void onResume() {
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
        title.setText("K90 性能悬浮监控 5.2");
        title.setTextSize(24);
        title.setTextColor(Color.rgb(20, 20, 20));
        title.setPadding(0, dp(8), 0, dp(8));
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView desc = new TextView(this);
        desc.setText("红魔散热器改为只读模式：\n\n1. 红魔官方 App 负责连接、鉴权和控制散热器。\n2. 本 App 不再连接蓝牙，也不发送任何 BLE 指令。\n3. 只读取红魔 App 已解析并写入系统日志的背夹温度、风扇转速和功耗，然后显示到悬浮窗。\n\n首次安装后需要电脑 ADB 执行一次 READ_LOGS 授权。");
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

        Button grantOverlay = makeButton("1. 授权悬浮窗");
        grantOverlay.setOnClickListener(v -> requestOverlayPermission());
        root.addView(grantOverlay, buttonLp());

        Button copyAdb = makeButton("2. 复制 READ_LOGS ADB 命令");
        copyAdb.setOnClickListener(v -> {
            String cmd = RedMagicLogReader.adbGrantCommand();
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("adb", cmd));
            Toast.makeText(this, "已复制：" + cmd, Toast.LENGTH_LONG).show();
        });
        root.addView(copyAdb, buttonLp());

        Button start = makeButton("3. 开始只读悬浮监控");
        start.setOnClickListener(v -> startMonitor());
        root.addView(start, buttonLp());

        Button stop = makeButton("停止悬浮监控");
        stop.setOnClickListener(v -> stopService(new Intent(this, OverlayMonitorService.class)));
        root.addView(stop, buttonLp());

        TextView adb = new TextView(this);
        adb.setText("电脑执行：\n\nadb shell pm grant com.ppt.k90monitor android.permission.READ_LOGS\n\n执行后保持红魔官方 App 正常连接散热器。悬浮窗会读取官方 App 输出的：\n\nonTemperature values=[19]\nonFanSpeed value=3330\nonFanPower value=15\n\n本 App 不会抢占散热器连接。");
        adb.setTextSize(13);
        adb.setTextColor(Color.GRAY);
        adb.setPadding(0, dp(16), 0, dp(16));
        root.addView(adb, new LinearLayout.LayoutParams(-1, -2));
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

    private void requestOverlayPermission() {
        if (Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "悬浮窗权限已开启", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()));
        startActivity(intent);
    }

    private void startMonitor() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "请先授权悬浮窗权限", Toast.LENGTH_LONG).show();
            requestOverlayPermission();
            return;
        }
        if (!RedMagicLogReader.get(this).hasReadLogsPermission()) {
            Toast.makeText(this, "缺少 READ_LOGS，请先执行 ADB 授权命令", Toast.LENGTH_LONG).show();
            updateStatus();
            return;
        }
        Intent i = new Intent(this, OverlayMonitorService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i);
        else startService(i);
        Toast.makeText(this, "只读红魔遥测悬浮窗已启动", Toast.LENGTH_SHORT).show();
    }

    private void updateStatus() {
        if (status == null) return;
        boolean overlay = Settings.canDrawOverlays(this);
        RedMagicLogReader r = RedMagicLogReader.get(this);
        RedMagicLogReader.State s = r.getState();
        status.setText("悬浮窗权限：" + (overlay ? "已开启 ✓" : "未开启 ✗") +
                "\nREAD_LOGS：" + (r.hasReadLogsPermission() ? "已授权 ✓" : "未授权 ✗") +
                "\n读取状态：" + s.message +
                "\n模式：只读红魔官方 App 日志，不连接散热器");
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1001);
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
