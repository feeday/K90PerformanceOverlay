package com.ppt.k90monitor;

import android.Manifest;
import android.app.Activity;
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

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private static final int REQ_BLE = 2001;
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
        title.setText("K90 性能悬浮监控 5.0 Cooler");
        title.setTextSize(24);
        title.setTextColor(Color.rgb(20, 20, 20));
        title.setPadding(0, dp(8), 0, dp(8));
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView desc = new TextView(this);
        desc.setText("系统监控：CPU 占用 / CPU 频率 / CPU温度 / GPU温度 / 电池温度 / RAM。\n\n红魔散热器 8 Pro：自动扫描并连接 BLE，悬浮窗预留散热开关、风扇转速、背夹温度、功率。协议字段未确认前显示 --，不会伪造数值。");
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

        Button grant = makeButton("1. 授权悬浮窗");
        grant.setOnClickListener(v -> requestOverlayPermission());
        root.addView(grant, buttonLp());

        Button ble = makeButton("2. 授权蓝牙 / 附近设备");
        ble.setOnClickListener(v -> requestBlePermissions());
        root.addView(ble, buttonLp());

        Button connect = makeButton("3. 搜索并连接红魔散热器");
        connect.setOnClickListener(v -> {
            if (!CoolerBleManager.get(this).hasPermissions()) {
                requestBlePermissions();
                Toast.makeText(this, "请先允许蓝牙/附近设备权限", Toast.LENGTH_LONG).show();
                return;
            }
            CoolerBleManager.get(this).startAutoConnect();
            Toast.makeText(this, "正在扫描红魔散热器", Toast.LENGTH_SHORT).show();
            updateStatus();
        });
        root.addView(connect, buttonLp());

        Button start = makeButton("4. 开始悬浮监控");
        start.setOnClickListener(v -> startMonitor());
        root.addView(start, buttonLp());

        Button disconnect = makeButton("断开红魔散热器");
        disconnect.setOnClickListener(v -> {
            CoolerBleManager.get(this).disconnect();
            updateStatus();
        });
        root.addView(disconnect, buttonLp());

        Button stop = makeButton("停止悬浮监控");
        stop.setOnClickListener(v -> stopService(new Intent(this, OverlayMonitorService.class)));
        root.addView(stop, buttonLp());

        TextView hint = new TextView(this);
        hint.setText("当前 5.0 先完成系统监控 + BLE 自动连接骨架。红魔散热器的 COOL/RPM/背夹温度/功率需要用实机 Notify/Read 数据确认字节映射后才能显示真实值。\n\nHyperOS 如回收悬浮服务，可在系统应用管理中允许后台运行并关闭省电限制。");
        hint.setTextSize(13);
        hint.setTextColor(Color.GRAY);
        hint.setPadding(0, dp(16), 0, dp(16));
        root.addView(hint, new LinearLayout.LayoutParams(-1, -2));
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

    private void requestBlePermissions() {
        List<String> need = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 31) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                need.add(Manifest.permission.BLUETOOTH_SCAN);
            }
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                need.add(Manifest.permission.BLUETOOTH_CONNECT);
            }
        } else if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            need.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (need.isEmpty()) {
            Toast.makeText(this, "蓝牙权限已开启", Toast.LENGTH_SHORT).show();
            updateStatus();
        } else {
            requestPermissions(need.toArray(new String[0]), REQ_BLE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_BLE) updateStatus();
    }

    private void startMonitor() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "请先授权悬浮窗权限", Toast.LENGTH_LONG).show();
            requestOverlayPermission();
            return;
        }
        if (CoolerBleManager.get(this).hasPermissions()) {
            CoolerBleManager.get(this).startAutoConnect();
        }
        Intent i = new Intent(this, OverlayMonitorService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i);
        else startService(i);
        Toast.makeText(this, "性能悬浮监控已启动", Toast.LENGTH_SHORT).show();
    }

    private void updateStatus() {
        if (status == null) return;
        boolean overlay = Settings.canDrawOverlays(this);
        CoolerBleManager.State c = CoolerBleManager.get(this).getState();
        status.setText("悬浮窗权限：" + (overlay ? "已开启 ✓" : "未开启 ✗") +
                "\n蓝牙权限：" + (CoolerBleManager.get(this).hasPermissions() ? "已开启 ✓" : "未开启 ✗") +
                "\n红魔散热器：" + c.message +
                (c.connected ? "\n设备：" + c.deviceName + "  " + c.address : ""));
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
