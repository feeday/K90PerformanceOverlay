package com.ppt.k90monitor;

import android.app.Activity;
import android.graphics.Color;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class StressTestActivity extends Activity {
    private static final String MODE_CPU = "CPU";
    private static final String MODE_GPU = "GPU";
    private static final String MODE_BOTH = "CPU+GPU";

    private static final float CPU_TEMP_LIMIT_C = 105f;
    private static final float BATTERY_TEMP_LIMIT_C = 55f;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable sampleTask = this::sampleAndRefresh;

    private MetricReader metricReader;
    private GpuStressView gpuView;
    private TextView durationLabel;
    private TextView statusText;
    private TextView resultText;

    private int selectedMinutes = 10;
    private volatile boolean running;
    private String runningMode = MODE_BOTH;
    private long startedAtMs;
    private long targetEndMs;
    private ExecutorService cpuPool;

    private final FrequencyStats cpuFreqStats = new FrequencyStats();
    private final FrequencyStats gpuFreqStats = new FrequencyStats();
    private final ValueStats cpuUsageStats = new ValueStats();
    private final ValueStats gpuUsageStats = new ValueStats();
    private final ValueStats cpuTempStats = new ValueStats();
    private final ValueStats gpuTempStats = new ValueStats();
    private final ValueStats batteryTempStats = new ValueStats();

    private static volatile long cpuBlackHole;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        metricReader = new MetricReader(this);
        setContentView(buildUi());
        updateDurationLabel();
        updateIdleStatus();
    }

    @Override protected void onResume() {
        super.onResume();
        if (gpuView != null) gpuView.onResume();
    }

    @Override protected void onPause() {
        if (running) finishTest("页面离开，测试已停止");
        if (gpuView != null) gpuView.onPause();
        super.onPause();
    }

    @Override protected void onDestroy() {
        stopCpuWorkers();
        handler.removeCallbacks(sampleTask);
        super.onDestroy();
    }

    private View buildUi() {
        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(Color.rgb(18, 18, 18));

        gpuView = new GpuStressView(this);
        gpuView.setVisibility(View.GONE);
        frame.addView(gpuView, new FrameLayout.LayoutParams(-1, -1));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.argb(238, 255, 255, 255));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(28));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));

        TextView title = new TextView(this);
        title.setText("CPU / GPU 压力测试");
        title.setTextSize(24);
        title.setTextColor(Color.rgb(20, 20, 20));
        title.setPadding(0, dp(4), 0, dp(8));
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView desc = new TextView(this);
        desc.setText("CPU：多线程计算压满可用核心。\nGPU：OpenGL ES 2.0 持续高负载片元计算。\n测试期间每秒采样频率，结束后统计 CPU 平均 / 最高 / 最低频率。GPU 频率可读取时也会同步统计。\n\n温度保护：CPU ≥ 105°C 或电池 ≥ 55°C 时自动停止。");
        desc.setTextSize(14);
        desc.setTextColor(Color.DKGRAY);
        desc.setLineSpacing(0, 1.15f);
        desc.setPadding(0, 0, 0, dp(12));
        root.addView(desc, new LinearLayout.LayoutParams(-1, -2));

        root.addView(sectionTitle("1. 选择测试时长"), new LinearLayout.LayoutParams(-1, -2));

        LinearLayout durationRow = new LinearLayout(this);
        durationRow.setOrientation(LinearLayout.HORIZONTAL);
        int[] minutes = {5, 10, 15, 30, 60};
        String[] labels = {"5分", "10分", "15分", "30分", "1小时"};
        for (int i = 0; i < minutes.length; i++) {
            final int value = minutes[i];
            Button b = makeButton(labels[i], 13);
            b.setOnClickListener(v -> {
                if (running) {
                    Toast.makeText(this, "请先停止当前测试", Toast.LENGTH_SHORT).show();
                    return;
                }
                selectedMinutes = value;
                updateDurationLabel();
            });
            durationRow.addView(b, weightedButtonLp());
        }
        root.addView(durationRow, new LinearLayout.LayoutParams(-1, -2));

        durationLabel = new TextView(this);
        durationLabel.setTextSize(15);
        durationLabel.setTextColor(Color.BLACK);
        durationLabel.setPadding(dp(4), dp(7), dp(4), dp(8));
        root.addView(durationLabel, new LinearLayout.LayoutParams(-1, -2));

        root.addView(sectionTitle("2. 开始压力测试"), new LinearLayout.LayoutParams(-1, -2));

        Button cpu = makeButton("开始 CPU 压力测试", 16);
        cpu.setOnClickListener(v -> startTest(MODE_CPU));
        root.addView(cpu, buttonLp());

        Button gpu = makeButton("开始 GPU 压力测试", 16);
        gpu.setOnClickListener(v -> startTest(MODE_GPU));
        root.addView(gpu, buttonLp());

        Button both = makeButton("开始 CPU + GPU 双烤", 16);
        both.setOnClickListener(v -> startTest(MODE_BOTH));
        root.addView(both, buttonLp());

        Button stop = makeButton("停止测试", 16);
        stop.setOnClickListener(v -> {
            if (running) finishTest("手动停止");
            else Toast.makeText(this, "当前没有正在运行的测试", Toast.LENGTH_SHORT).show();
        });
        root.addView(stop, buttonLp());

        root.addView(sectionTitle("3. 实时状态"), new LinearLayout.LayoutParams(-1, -2));

        statusText = new TextView(this);
        statusText.setTextSize(15);
        statusText.setTextColor(Color.rgb(25, 25, 25));
        statusText.setPadding(dp(12), dp(12), dp(12), dp(12));
        statusText.setBackgroundColor(Color.rgb(242, 242, 242));
        root.addView(statusText, new LinearLayout.LayoutParams(-1, -2));

        root.addView(sectionTitle("4. 测试结果"), new LinearLayout.LayoutParams(-1, -2));

        resultText = new TextView(this);
        resultText.setText("完成一次测试后，这里会显示统计结果。\n重点：CPU 平均频率 / 最高频率 / 最低频率。");
        resultText.setTextSize(15);
        resultText.setTextColor(Color.rgb(20, 20, 20));
        resultText.setPadding(dp(12), dp(12), dp(12), dp(12));
        resultText.setBackgroundColor(Color.rgb(236, 244, 255));
        root.addView(resultText, new LinearLayout.LayoutParams(-1, -2));

        TextView note = new TextView(this);
        note.setText("说明：压力测试会明显增加功耗和发热。建议测试时保持应用在前台，并根据需要连接散热背夹。CPU 频率统计优先读取每个在线核心的实时频率；系统限制读取时会回退到现有监控可读值。");
        note.setTextSize(13);
        note.setTextColor(Color.GRAY);
        note.setPadding(0, dp(18), 0, dp(10));
        root.addView(note, new LinearLayout.LayoutParams(-1, -2));

        frame.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        return frame;
    }

    private TextView sectionTitle(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(18);
        t.setTextColor(Color.BLACK);
        t.setPadding(0, dp(14), 0, dp(5));
        return t;
    }

    private Button makeButton(String text, int size) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(size);
        b.setAllCaps(false);
        return b;
    }

    private LinearLayout.LayoutParams weightedButtonLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(50), 1f);
        lp.setMargins(dp(1), dp(4), dp(1), dp(4));
        return lp;
    }

    private LinearLayout.LayoutParams buttonLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(54));
        lp.topMargin = dp(8);
        return lp;
    }

    private void updateDurationLabel() {
        if (durationLabel != null) {
            durationLabel.setText("当前时长：" + (selectedMinutes == 60 ? "1 小时" : selectedMinutes + " 分钟"));
        }
    }

    private void updateIdleStatus() {
        if (statusText != null) {
            statusText.setText("状态：未运行\n请选择 5 / 10 / 15 / 30 / 60 分钟后开始测试。");
        }
    }

    private void startTest(String mode) {
        if (running) {
            Toast.makeText(this, "已有压力测试正在运行", Toast.LENGTH_SHORT).show();
            return;
        }

        resetStats();
        runningMode = mode;
        running = true;
        startedAtMs = SystemClock.elapsedRealtime();
        targetEndMs = startedAtMs + selectedMinutes * 60_000L;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        if (needsCpu(mode)) startCpuWorkers();
        if (needsGpu(mode)) {
            gpuView.setVisibility(View.VISIBLE);
            gpuView.setStressEnabled(true);
        } else {
            gpuView.setStressEnabled(false);
            gpuView.setVisibility(View.GONE);
        }

        resultText.setText("测试进行中……结束后自动生成统计结果。");
        Toast.makeText(this, mode + " 压力测试已开始", Toast.LENGTH_SHORT).show();
        handler.removeCallbacks(sampleTask);
        handler.postDelayed(sampleTask, 700);
    }

    private boolean needsCpu(String mode) {
        return MODE_CPU.equals(mode) || MODE_BOTH.equals(mode);
    }

    private boolean needsGpu(String mode) {
        return MODE_GPU.equals(mode) || MODE_BOTH.equals(mode);
    }

    private void startCpuWorkers() {
        int threads = Math.max(1, Runtime.getRuntime().availableProcessors());
        cpuPool = Executors.newFixedThreadPool(threads);
        for (int i = 0; i < threads; i++) {
            final int worker = i;
            cpuPool.submit(() -> runCpuStress(worker));
        }
    }

    private void runCpuStress(int worker) {
        try { Thread.currentThread().setPriority(Thread.MAX_PRIORITY); } catch (Throwable ignored) { }
        long x = 0x9E3779B97F4A7C15L ^ (worker * 0xBF58476D1CE4E5B9L);
        double d = 1.000001 + worker * 0.00001;
        while (running && needsCpu(runningMode) && !Thread.currentThread().isInterrupted()) {
            for (int i = 0; i < 60_000; i++) {
                x ^= x << 13;
                x ^= x >>> 7;
                x ^= x << 17;
                d = Math.sqrt(Math.abs(d * 1.000000119 + (x & 0xFFFF) + 1.0));
                d += Math.sin(d + (x & 255) * 0.001);
                if (d > 1_000_000.0) d *= 0.000001;
            }
            cpuBlackHole = x ^ Double.doubleToRawLongBits(d);
        }
    }

    private void stopCpuWorkers() {
        ExecutorService pool = cpuPool;
        cpuPool = null;
        if (pool != null) pool.shutdownNow();
    }

    private void sampleAndRefresh() {
        if (!running) return;

        MetricReader.Snapshot snapshot = metricReader.read();
        FrequencySample cpuSample = readCpuFrequencySample();
        if (!cpuSample.valid() && valid(snapshot.cpuFreqMHz)) {
            cpuSample = new FrequencySample(snapshot.cpuFreqMHz, snapshot.cpuFreqMHz, snapshot.cpuFreqMHz, 1);
        }
        if (cpuSample.valid()) cpuFreqStats.add(cpuSample);
        if (valid(snapshot.gpuFreqMHz)) gpuFreqStats.add(snapshot.gpuFreqMHz);
        if (valid(snapshot.cpuUsage)) cpuUsageStats.add(snapshot.cpuUsage);
        if (valid(snapshot.gpuUsage)) gpuUsageStats.add(snapshot.gpuUsage);
        if (valid(snapshot.cpuTempC)) cpuTempStats.add(snapshot.cpuTempC);
        if (valid(snapshot.gpuTempC)) gpuTempStats.add(snapshot.gpuTempC);
        if (valid(snapshot.batteryTempC)) batteryTempStats.add(snapshot.batteryTempC);

        long now = SystemClock.elapsedRealtime();
        long remainingMs = Math.max(0, targetEndMs - now);
        StringBuilder sb = new StringBuilder();
        sb.append("状态：运行中  [").append(runningMode).append("]\n");
        sb.append("剩余：").append(formatDuration(remainingMs)).append(" / 设定 ")
                .append(selectedMinutes == 60 ? "1小时" : selectedMinutes + "分钟").append("\n");
        sb.append("CPU：").append(formatPercent(snapshot.cpuUsage))
                .append("  当前平均 ").append(cpuSample.valid() ? formatMHz(cpuSample.averageMHz) : "不可读")
                .append("  温度 ").append(formatTemp(snapshot.cpuTempC)).append("\n");
        sb.append("GPU：").append(formatPercent(snapshot.gpuUsage))
                .append("  频率 ").append(valid(snapshot.gpuFreqMHz) ? formatMHz(snapshot.gpuFreqMHz) : "不可读")
                .append("  温度 ").append(formatTemp(snapshot.gpuTempC)).append("\n");
        sb.append("BAT：").append(formatTemp(snapshot.batteryTempC))
                .append("  CPU频率样本：").append(cpuFreqStats.count);
        statusText.setText(sb.toString());

        boolean cpuTooHot = valid(snapshot.cpuTempC) && snapshot.cpuTempC >= CPU_TEMP_LIMIT_C;
        boolean batteryTooHot = valid(snapshot.batteryTempC) && snapshot.batteryTempC >= BATTERY_TEMP_LIMIT_C;
        if (cpuTooHot || batteryTooHot) {
            finishTest("温度保护自动停止");
            return;
        }

        if (now >= targetEndMs) {
            finishTest("测试完成");
            return;
        }
        handler.postDelayed(sampleTask, 1000);
    }

    private void finishTest(String reason) {
        if (!running) return;
        running = false;
        handler.removeCallbacks(sampleTask);
        stopCpuWorkers();
        if (gpuView != null) {
            gpuView.setStressEnabled(false);
            gpuView.setVisibility(View.GONE);
        }
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        long elapsed = Math.max(0, SystemClock.elapsedRealtime() - startedAtMs);
        StringBuilder result = new StringBuilder();
        result.append(reason).append("\n");
        result.append("模式：").append(runningMode).append("\n");
        result.append("实际时长：").append(formatDuration(elapsed)).append("\n\n");
        result.append("CPU 频率\n");
        if (cpuFreqStats.count > 0) {
            result.append("平均：").append(formatMHz((float) (cpuFreqStats.sumAverageMHz / cpuFreqStats.count))).append("\n");
            result.append("最高：").append(formatMHz(cpuFreqStats.maxMHz)).append("\n");
            result.append("最低：").append(formatMHz(cpuFreqStats.minMHz)).append("\n");
            result.append("样本：").append(cpuFreqStats.count).append(" 次\n");
        } else {
            result.append("系统未允许读取 CPU 实时频率。\n");
        }

        result.append("\nCPU 平均占用：").append(cpuUsageStats.count > 0 ? formatPercent((float) cpuUsageStats.average()) : "不可读");
        result.append("\nCPU 最高温度：").append(cpuTempStats.count > 0 ? formatTemp(cpuTempStats.max) : "不可读");

        result.append("\n\nGPU 频率\n");
        if (gpuFreqStats.count > 0) {
            result.append("平均：").append(formatMHz((float) (gpuFreqStats.sumAverageMHz / gpuFreqStats.count))).append("\n");
            result.append("最高：").append(formatMHz(gpuFreqStats.maxMHz)).append("\n");
            result.append("最低：").append(formatMHz(gpuFreqStats.minMHz)).append("\n");
        } else {
            result.append("当前系统限制普通 APK 读取 GPU 频率，压力负载仍可正常运行。\n");
        }
        result.append("GPU 平均占用：").append(gpuUsageStats.count > 0 ? formatPercent((float) gpuUsageStats.average()) : "不可读");
        result.append("\nGPU 最高温度：").append(gpuTempStats.count > 0 ? formatTemp(gpuTempStats.max) : "不可读");
        result.append("\nBAT 最高温度：").append(batteryTempStats.count > 0 ? formatTemp(batteryTempStats.max) : "不可读");

        resultText.setText(result.toString());
        statusText.setText("状态：" + reason + "\n已停止 CPU/GPU 压力负载。可重新选择时长继续测试。");
        Toast.makeText(this, reason, Toast.LENGTH_SHORT).show();
    }

    private void resetStats() {
        cpuFreqStats.reset();
        gpuFreqStats.reset();
        cpuUsageStats.reset();
        gpuUsageStats.reset();
        cpuTempStats.reset();
        gpuTempStats.reset();
        batteryTempStats.reset();
    }

    private FrequencySample readCpuFrequencySample() {
        File root = new File("/sys/devices/system/cpu");
        File[] cpus = root.listFiles((dir, name) -> name.matches("cpu\\d+"));
        if (cpus == null || cpus.length == 0) return FrequencySample.invalid();

        double sum = 0;
        float min = Float.POSITIVE_INFINITY;
        float max = Float.NEGATIVE_INFINITY;
        int count = 0;
        for (File cpu : cpus) {
            File onlineFile = new File(cpu, "online");
            if (onlineFile.exists() && readLong(onlineFile) == 0) continue;

            long raw = readLong(new File(cpu, "cpufreq/scaling_cur_freq"));
            if (raw <= 0) raw = readLong(new File(cpu, "cpufreq/cpuinfo_cur_freq"));
            float mhz = normalizeCpuFrequencyMHz(raw);
            if (!valid(mhz) || mhz <= 0) continue;

            sum += mhz;
            min = Math.min(min, mhz);
            max = Math.max(max, mhz);
            count++;
        }
        if (count == 0) return FrequencySample.invalid();
        return new FrequencySample((float) (sum / count), min, max, count);
    }

    private static float normalizeCpuFrequencyMHz(long raw) {
        if (raw <= 0) return Float.NaN;
        if (raw >= 10_000_000L) return raw / 1_000_000f;
        if (raw >= 10_000L) return raw / 1000f;
        return raw;
    }

    private static long readLong(File file) {
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String s = br.readLine();
            if (s == null) return Long.MIN_VALUE;
            return Long.parseLong(s.trim().split("\\s+")[0]);
        } catch (Throwable ignored) {
            return Long.MIN_VALUE;
        }
    }

    private static boolean valid(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }

    private static String formatMHz(float mhz) {
        if (!valid(mhz)) return "不可读";
        if (mhz >= 1000f) return String.format(Locale.CHINA, "%.0f MHz (%.2f GHz)", mhz, mhz / 1000f);
        return String.format(Locale.CHINA, "%.0f MHz", mhz);
    }

    private static String formatPercent(float value) {
        return valid(value) ? String.format(Locale.CHINA, "%.1f%%", value) : "不可读";
    }

    private static String formatTemp(float value) {
        return valid(value) ? String.format(Locale.CHINA, "%.1f°C", value) : "不可读";
    }

    private static String formatDuration(long ms) {
        long totalSeconds = Math.max(0, ms / 1000L);
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        if (hours > 0) return String.format(Locale.CHINA, "%d:%02d:%02d", hours, minutes, seconds);
        return String.format(Locale.CHINA, "%02d:%02d", minutes, seconds);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static class FrequencySample {
        final float averageMHz;
        final float minMHz;
        final float maxMHz;
        final int cores;

        FrequencySample(float averageMHz, float minMHz, float maxMHz, int cores) {
            this.averageMHz = averageMHz;
            this.minMHz = minMHz;
            this.maxMHz = maxMHz;
            this.cores = cores;
        }

        static FrequencySample invalid() {
            return new FrequencySample(Float.NaN, Float.NaN, Float.NaN, 0);
        }

        boolean valid() {
            return cores > 0 && StressTestActivity.valid(averageMHz);
        }
    }

    private static class FrequencyStats {
        double sumAverageMHz;
        float minMHz = Float.POSITIVE_INFINITY;
        float maxMHz = Float.NEGATIVE_INFINITY;
        int count;

        void add(FrequencySample sample) {
            if (!sample.valid()) return;
            sumAverageMHz += sample.averageMHz;
            minMHz = Math.min(minMHz, sample.minMHz);
            maxMHz = Math.max(maxMHz, sample.maxMHz);
            count++;
        }

        void add(float mhz) {
            if (!StressTestActivity.valid(mhz)) return;
            sumAverageMHz += mhz;
            minMHz = Math.min(minMHz, mhz);
            maxMHz = Math.max(maxMHz, mhz);
            count++;
        }

        void reset() {
            sumAverageMHz = 0;
            minMHz = Float.POSITIVE_INFINITY;
            maxMHz = Float.NEGATIVE_INFINITY;
            count = 0;
        }
    }

    private static class ValueStats {
        double sum;
        float max = Float.NEGATIVE_INFINITY;
        int count;

        void add(float value) {
            if (!StressTestActivity.valid(value)) return;
            sum += value;
            max = Math.max(max, value);
            count++;
        }

        double average() {
            return count > 0 ? sum / count : Double.NaN;
        }

        void reset() {
            sum = 0;
            max = Float.NEGATIVE_INFINITY;
            count = 0;
        }
    }

    private static class GpuStressView extends GLSurfaceView {
        private final StressRenderer stressRenderer;

        GpuStressView(Activity context) {
            super(context);
            setEGLContextClientVersion(2);
            stressRenderer = new StressRenderer();
            setRenderer(stressRenderer);
            setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
            setPreserveEGLContextOnPause(true);
        }

        void setStressEnabled(boolean enabled) {
            stressRenderer.enabled = enabled;
            try {
                setRenderMode(enabled ? GLSurfaceView.RENDERMODE_CONTINUOUSLY : GLSurfaceView.RENDERMODE_WHEN_DIRTY);
                requestRender();
            } catch (Throwable ignored) { }
        }
    }

    private static class StressRenderer implements GLSurfaceView.Renderer {
        private static final float[] TRIANGLE = {
                -1f, -1f,
                 3f, -1f,
                -1f,  3f
        };

        private final FloatBuffer vertexBuffer = ByteBuffer.allocateDirect(TRIANGLE.length * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        volatile boolean enabled;
        private int program;
        private int positionHandle;
        private int timeHandle;
        private long startNs;

        StressRenderer() {
            vertexBuffer.put(TRIANGLE).position(0);
        }

        @Override public void onSurfaceCreated(GL10 gl, EGLConfig config) {
            String vertex = "attribute vec2 aPos; void main(){ gl_Position = vec4(aPos, 0.0, 1.0); }";
            String fragment =
                    "precision mediump float; uniform float uTime;" +
                    "void main(){" +
                    "vec2 p=gl_FragCoord.xy*0.0025; float v=0.0;" +
                    "for(int i=0;i<96;i++){" +
                    "float f=float(i);" +
                    "p=vec2(sin(p.y*1.71+uTime+f*0.013),cos(p.x*1.37-uTime+f*0.017));" +
                    "v+=sin(dot(p,p)*7.0+f*0.11+uTime);" +
                    "}" +
                    "gl_FragColor=vec4(fract(v*0.17),fract(v*0.31),fract(v*0.47),1.0);" +
                    "}";
            int vs = compileShader(GLES20.GL_VERTEX_SHADER, vertex);
            int fs = compileShader(GLES20.GL_FRAGMENT_SHADER, fragment);
            program = GLES20.glCreateProgram();
            GLES20.glAttachShader(program, vs);
            GLES20.glAttachShader(program, fs);
            GLES20.glLinkProgram(program);
            positionHandle = GLES20.glGetAttribLocation(program, "aPos");
            timeHandle = GLES20.glGetUniformLocation(program, "uTime");
            startNs = System.nanoTime();
            GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        }

        @Override public void onSurfaceChanged(GL10 gl, int width, int height) {
            GLES20.glViewport(0, 0, width, height);
        }

        @Override public void onDrawFrame(GL10 gl) {
            if (!enabled) {
                GLES20.glClearColor(0.04f, 0.04f, 0.04f, 1f);
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
                return;
            }
            GLES20.glUseProgram(program);
            float seconds = (System.nanoTime() - startNs) / 1_000_000_000f;
            GLES20.glUniform1f(timeHandle, seconds);
            vertexBuffer.position(0);
            GLES20.glEnableVertexAttribArray(positionHandle);
            GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 3);
            GLES20.glDisableVertexAttribArray(positionHandle);
        }

        private static int compileShader(int type, String source) {
            int shader = GLES20.glCreateShader(type);
            GLES20.glShaderSource(shader, source);
            GLES20.glCompileShader(shader);
            return shader;
        }
    }
}
