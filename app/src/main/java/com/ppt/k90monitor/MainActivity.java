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
    private TextView diagnosticsView;
    private String lastDiagnostics = "";

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
        title.setText("K90 性能悬浮监控 V3");
        title.setTextSize(24);
        title.setTextColor(Color.rgb(20, 20, 20));
        title.setPadding(0, dp(8), 0, dp(8));
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView desc = new TextView(this);
        desc.setText("悬浮显示 CPU / GPU / 内存 / 温度\n默认每 1 秒刷新，可拖动悬浮框。\n\nV3 新增 GPU 节点诊断，用于定位 HyperOS 下 GPU 占用/频率 N/A 的具体原因。");
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

        Button start = makeButton("2. 开始悬浮监控");
        start.setOnClickListener(v -> startMonitor());
        root.addView(start, buttonLp());

        Button stop = makeButton("停止悬浮监控");
        stop.setOnClickListener(v -> stopService(new Intent(this, OverlayMonitorService.class)));
        root.addView(stop, buttonLp());

        Button diagnose = makeButton("3. GPU 节点诊断");
        diagnose.setOnClickListener(v -> runDiagnostics(diagnose));
        root.addView(diagnose, buttonLp());

        Button copy = makeButton("复制诊断结果");
        copy.setOnClickListener(v -> copyDiagnostics());
        root.addView(copy, buttonLp());

        diagnosticsView = new TextView(this);
        diagnosticsView.setText("诊断结果会显示在这里。\n如果 GPU 仍为 N/A，请点“GPU 节点诊断”，完成后点“复制诊断结果”发给我。");
        diagnosticsView.setTextSize(11);
        diagnosticsView.setTextColor(Color.rgb(40, 40, 40));
        diagnosticsView.setTextIsSelectable(true);
        diagnosticsView.setPadding(dp(8), dp(12), dp(8), dp(12));
        root.addView(diagnosticsView, new LinearLayout.LayoutParams(-1, -2));

        TextView hint = new TextView(this);
        hint.setText("红米 / 小米 HyperOS：如悬浮窗被系统回收，可在系统应用管理里允许后台运行或关闭本应用的省电限制。GPU 诊断只读取系统公开/可访问节点，不需要 Root。\n");
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

    private void startMonitor() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "请先授权悬浮窗权限", Toast.LENGTH_LONG).show();
            requestOverlayPermission();
            return;
        }
        Intent i = new Intent(this, OverlayMonitorService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i);
        else startService(i);
        Toast.makeText(this, "性能悬浮监控已启动", Toast.LENGTH_SHORT).show();
    }

    private void runDiagnostics(Button button) {
        button.setEnabled(false);
        button.setText("正在诊断，请稍候…");
        diagnosticsView.setText("正在扫描 KGSL / devfreq / GPU 系统节点…");
        new Thread(() -> {
            String report;
            try {
                report = GpuDiagnostics.run();
            } catch (Throwable e) {
                report = "GPU diagnostics failed: " + e.getClass().getSimpleName() + ": " + e.getMessage();
            }
            final String finalReport = report;
            runOnUiThread(() -> {
                lastDiagnostics = finalReport;
                diagnosticsView.setText(finalReport);
                button.setEnabled(true);
                button.setText("3. GPU 节点诊断");
                Toast.makeText(this, "GPU 诊断完成，可复制结果", Toast.LENGTH_SHORT).show();
            });
        }, "gpu-diagnostics").start();
    }

    private void copyDiagnostics() {
        if (lastDiagnostics == null || lastDiagnostics.trim().isEmpty()) {
            Toast.makeText(this, "请先运行 GPU 节点诊断", Toast.LENGTH_SHORT).show();
            return;
        }
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("K90 GPU Diagnostics", lastDiagnostics));
        Toast.makeText(this, "诊断结果已复制", Toast.LENGTH_SHORT).show();
    }

    private void updateStatus() {
        if (status == null) return;
        boolean overlay = Settings.canDrawOverlays(this);
        status.setText("悬浮窗权限：" + (overlay ? "已开启 ✓" : "未开启 ✗"));
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
