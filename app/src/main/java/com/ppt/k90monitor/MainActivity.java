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

import rikka.shizuku.Shizuku;

public class MainActivity extends Activity {
    private TextView status;
    private TextView diagnosticsView;
    private String lastDiagnostics = "";

    private final Shizuku.OnRequestPermissionResultListener shizukuPermissionListener =
            (requestCode, grantResult) -> {
                if (requestCode != ShizukuBridge.REQUEST_CODE) return;
                runOnUiThread(() -> {
                    updateStatus();
                    Toast.makeText(this,
                            grantResult == PackageManager.PERMISSION_GRANTED ? "Shizuku 授权成功" : "Shizuku 授权被拒绝",
                            Toast.LENGTH_SHORT).show();
                });
            };

    private final Shizuku.OnBinderReceivedListener binderListener = () -> runOnUiThread(this::updateStatus);
    private final Shizuku.OnBinderDeadListener binderDeadListener = () -> runOnUiThread(this::updateStatus);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildUi());
        requestNotificationPermissionIfNeeded();
        try {
            Shizuku.addRequestPermissionResultListener(shizukuPermissionListener);
            Shizuku.addBinderReceivedListenerSticky(binderListener);
            Shizuku.addBinderDeadListener(binderDeadListener);
        } catch (Throwable ignored) { }
        updateStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
    }

    @Override
    protected void onDestroy() {
        try {
            Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener);
            Shizuku.removeBinderReceivedListener(binderListener);
            Shizuku.removeBinderDeadListener(binderDeadListener);
        } catch (Throwable ignored) { }
        super.onDestroy();
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
        title.setText("K90 性能悬浮监控 V4");
        title.setTextSize(24);
        title.setTextColor(Color.rgb(20, 20, 20));
        title.setPadding(0, dp(8), 0, dp(8));
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView desc = new TextView(this);
        desc.setText("悬浮显示 CPU / GPU / 内存 / 温度\n默认每 1 秒刷新，可拖动悬浮框。\n\nK90 / HyperOS 会阻止普通 APK 读取 KGSL GPU 节点。V4 增加 Shizuku shell 后端，用于读取 GPU 占用率和频率，无需 Root。");
        desc.setTextSize(15);
        desc.setTextColor(Color.DKGRAY);
        desc.setLineSpacing(0, 1.2f);
        desc.setPadding(0, 0, 0, dp(18));
        root.addView(desc, new LinearLayout.LayoutParams(-1, -2));

        status = new TextView(this);
        status.setTextSize(14);
        status.setTextColor(Color.rgb(20, 20, 20));
        status.setPadding(dp(12), dp(12), dp(12), dp(12));
        root.addView(status, new LinearLayout.LayoutParams(-1, -2));

        Button grant = makeButton("1. 授权悬浮窗");
        grant.setOnClickListener(v -> requestOverlayPermission());
        root.addView(grant, buttonLp());

        Button shizuku = makeButton("2. 授权 Shizuku（GPU）");
        shizuku.setOnClickListener(v -> requestShizukuPermission());
        root.addView(shizuku, buttonLp());

        Button start = makeButton("3. 开始悬浮监控");
        start.setOnClickListener(v -> startMonitor());
        root.addView(start, buttonLp());

        Button stop = makeButton("停止悬浮监控");
        stop.setOnClickListener(v -> stopService(new Intent(this, OverlayMonitorService.class)));
        root.addView(stop, buttonLp());

        Button diagnose = makeButton("GPU 节点诊断");
        diagnose.setOnClickListener(v -> runDiagnostics(diagnose));
        root.addView(diagnose, buttonLp());

        Button copy = makeButton("复制诊断结果");
        copy.setOnClickListener(v -> copyDiagnostics());
        root.addView(copy, buttonLp());

        diagnosticsView = new TextView(this);
        diagnosticsView.setText("GPU 仍为 N/A 时，可再次运行节点诊断。\nV4 授权 Shizuku 后，悬浮框底部应显示 GPU BACKEND: SHIZUKU。");
        diagnosticsView.setTextSize(11);
        diagnosticsView.setTextColor(Color.rgb(40, 40, 40));
        diagnosticsView.setTextIsSelectable(true);
        diagnosticsView.setPadding(dp(8), dp(12), dp(8), dp(12));
        root.addView(diagnosticsView, new LinearLayout.LayoutParams(-1, -2));

        TextView hint = new TextView(this);
        hint.setText("使用方法：先在手机安装并启动 Shizuku（推荐通过无线调试启动），然后回到本应用点击“授权 Shizuku（GPU）”。Shizuku 每次重启手机后通常需要重新启动。\n\n如果 Shizuku 已授权后 GPU 仍为 N/A，说明 shell SELinux 也被限制，届时再考虑 Root 后端。");
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

    private void requestShizukuPermission() {
        if (!ShizukuBridge.isBinderReady()) {
            Toast.makeText(this, "Shizuku 未运行，请先打开 Shizuku 并通过无线调试启动", Toast.LENGTH_LONG).show();
            return;
        }
        if (ShizukuBridge.hasPermission()) {
            Toast.makeText(this, "Shizuku 已授权", Toast.LENGTH_SHORT).show();
            updateStatus();
            return;
        }
        ShizukuBridge.requestPermission();
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
        diagnosticsView.setText("正在扫描 KGSL / devfreq / GPU 系统节点…\n" + ShizukuBridge.getBackendInfo());
        new Thread(() -> {
            String report;
            try {
                report = GpuDiagnostics.run();
                report += "\n[Shizuku]\n" + ShizukuBridge.getBackendInfo() + "\n";
                if (ShizukuBridge.hasPermission()) {
                    report += "gpu_busy_percentage=" + ShizukuBridge.readFirstLine("/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage") + "\n";
                    report += "gpu_load=" + ShizukuBridge.readFirstLine("/sys/class/kgsl/kgsl-3d0/devfreq/gpu_load") + "\n";
                    report += "gpubusy=" + ShizukuBridge.readFirstLine("/sys/class/kgsl/kgsl-3d0/gpubusy") + "\n";
                    report += "cur_freq=" + ShizukuBridge.readFirstLine("/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq") + "\n";
                    report += "clock_mhz=" + ShizukuBridge.readFirstLine("/sys/class/kgsl/kgsl-3d0/clock_mhz") + "\n";
                }
            } catch (Throwable e) {
                report = "GPU diagnostics failed: " + e.getClass().getSimpleName() + ": " + e.getMessage();
            }
            final String finalReport = report;
            runOnUiThread(() -> {
                lastDiagnostics = finalReport;
                diagnosticsView.setText(finalReport);
                button.setEnabled(true);
                button.setText("GPU 节点诊断");
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
        status.setText("悬浮窗权限：" + (overlay ? "已开启 ✓" : "未开启 ✗") +
                "\nGPU 提权后端：" + ShizukuBridge.getBackendInfo());
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
