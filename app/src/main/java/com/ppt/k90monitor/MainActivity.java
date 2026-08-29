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
import java.util.Locale;

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
        title.setText("K90 性能悬浮监控 5.1 Cooler");
        title.setTextSize(24);
        title.setTextColor(Color.rgb(20, 20, 20));
        title.setPadding(0, dp(8), 0, dp(8));
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView desc = new TextView(this);
        desc.setText("系统：CPU 占用 / CPU频率 / CPU温度 / GPU温度 / 电池温度 / RAM。\n\n红魔散热器 8 Pro：已按实机原厂日志接入背夹温度、风扇转速、功率、智能温控、破坏神模式和散热开启指令。");
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

        Button connect = makeButton("3. 搜索并连接 RM Magcooler 8pro");
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

        TextView controlTitle = new TextView(this);
        controlTitle.setText("红魔散热器控制");
        controlTitle.setTextSize(18);
        controlTitle.setTextColor(Color.BLACK);
        controlTitle.setPadding(0, dp(22), 0, dp(4));
        root.addView(controlTitle, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout row1 = horizontal();
        Button autoOn = makeButton("智能温控 ON");
        autoOn.setOnClickListener(v -> { CoolerBleManager.get(this).setAutoTemp(true); toast("智能温控 ON"); });
        row1.addView(autoOn, weightedButton());
        Button autoOff = makeButton("智能温控 OFF");
        autoOff.setOnClickListener(v -> { CoolerBleManager.get(this).setAutoTemp(false); toast("智能温控 OFF"); });
        row1.addView(autoOff, weightedButton());
        root.addView(row1, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout row2 = horizontal();
        Button devOn = makeButton("破坏神 ON");
        devOn.setOnClickListener(v -> { CoolerBleManager.get(this).setDestroyer(true); toast("破坏神 ON"); });
        row2.addView(devOn, weightedButton());
        Button devOff = makeButton("破坏神 OFF");
        devOff.setOnClickListener(v -> { CoolerBleManager.get(this).setDestroyer(false); toast("破坏神 OFF"); });
        row2.addView(devOff, weightedButton());
        root.addView(row2, new LinearLayout.LayoutParams(-1, -2));

        Button coolerOn = makeButton("散热开启（原厂已验证指令 02）");
        coolerOn.setOnClickListener(v -> { CoolerBleManager.get(this).turnCoolerOn(); toast("已发送散热开启指令"); });
        root.addView(coolerOn, buttonLp());

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
        hint.setText("智能温控和破坏神模式按互斥逻辑处理：开启其中一个时会先关闭另一个。\n\n当前日志只明确抓到散热开启：1011 = 02；尚未确认原厂散热关闭指令，因此 5.1 不提供猜测的 OFF 按钮。\n\n如果原厂 App 同时连接散热器，可能与本 App 抢占 GATT；测试时建议先彻底退出原厂 App。");
        hint.setTextSize(13);
        hint.setTextColor(Color.GRAY);
        hint.setPadding(0, dp(16), 0, dp(16));
        root.addView(hint, new LinearLayout.LayoutParams(-1, -2));
        return scroll;
    }

    private LinearLayout horizontal() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        return row;
    }

    private Button makeButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(15);
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
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) need.add(Manifest.permission.BLUETOOTH_SCAN);
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) need.add(Manifest.permission.BLUETOOTH_CONNECT);
        } else if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            need.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (need.isEmpty()) {
            Toast.makeText(this, "蓝牙权限已开启", Toast.LENGTH_SHORT).show();
            updateStatus();
        } else requestPermissions(need.toArray(new String[0]), REQ_BLE);
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
        if (CoolerBleManager.get(this).hasPermissions()) CoolerBleManager.get(this).startAutoConnect();
        Intent i = new Intent(this, OverlayMonitorService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
        Toast.makeText(this, "性能悬浮监控已启动", Toast.LENGTH_SHORT).show();
    }

    private void updateStatus() {
        if (status == null) return;
        boolean overlay = Settings.canDrawOverlays(this);
        CoolerBleManager.State c = CoolerBleManager.get(this).getState();
        String telemetry = c.connected
                ? String.format(Locale.US, "\n背夹温度：%s\n转速：%s\n功率：%s\n智能温控：%s\n破坏神：%s",
                temp(c.clampTempC), rpm(c.fanRpm), power(c.powerW), onOff(c.autoTemp), onOff(c.destroyer))
                : "";
        status.setText("悬浮窗权限：" + (overlay ? "已开启 ✓" : "未开启 ✗") +
                "\n蓝牙权限：" + (CoolerBleManager.get(this).hasPermissions() ? "已开启 ✓" : "未开启 ✗") +
                "\n红魔散热器：" + c.message +
                (c.connected ? "\n设备：" + c.deviceName + "  " + c.address : "") + telemetry);
    }

    private String temp(float c) { return Float.isNaN(c) ? "--" : String.format(Locale.US, "%.1f°C", c); }
    private String rpm(int r) { return r < 0 ? "--" : r + " RPM"; }
    private String power(float w) { return Float.isNaN(w) ? "--" : String.format(Locale.US, "%.0f W", w); }
    private String onOff(Boolean v) { return v == null ? "--" : (v ? "ON" : "OFF"); }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1001);
        }
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
