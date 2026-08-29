package com.ppt.k90monitor;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.Locale;

/**
 * Reads REDMAGIC Cooler 8 Pro telemetry from a file written by an AYA/shell bridge.
 * The Android app itself never reads logcat and never connects to the cooler.
 */
public final class RedMagicBridgeReader {
    private static final String METRICS_FILE = "redmagic_metrics.txt";
    private static final String SCRIPT_FILE = "redmagic_bridge.sh";
    private static final long STALE_MS = 15_000L;

    public static final class State {
        public float clampTempC = Float.NaN;
        public int fanRpm = -1;
        public float powerW = Float.NaN;
        public long updatedAtMs;
        public boolean fileExists;
        public boolean stale = true;
        public String message = "等待桥接数据";
    }

    private final Context context;

    public RedMagicBridgeReader(Context context) {
        this.context = context.getApplicationContext();
        ensureBridgeScript();
    }

    public State read() {
        State s = new State();
        File file = metricsFile();
        s.fileExists = file.exists();
        if (!s.fileExists) {
            s.message = "等待 AYA 桥接";
            return s;
        }

        long explicitUpdatedSec = 0L;
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                int eq = line.indexOf('=');
                if (eq <= 0) continue;
                String key = line.substring(0, eq).trim();
                String value = line.substring(eq + 1).trim();
                try {
                    switch (key) {
                        case "TEMP":
                            if (!value.isEmpty()) s.clampTempC = Float.parseFloat(value);
                            break;
                        case "RPM":
                            if (!value.isEmpty()) s.fanRpm = Integer.parseInt(value);
                            break;
                        case "POWER":
                            if (!value.isEmpty()) s.powerW = Float.parseFloat(value);
                            break;
                        case "UPDATED":
                            if (!value.isEmpty()) explicitUpdatedSec = Long.parseLong(value);
                            break;
                    }
                } catch (Throwable ignored) { }
            }
        } catch (Throwable e) {
            s.message = "桥接文件读取失败";
            return s;
        }

        s.updatedAtMs = explicitUpdatedSec > 0 ? explicitUpdatedSec * 1000L : file.lastModified();
        long age = s.updatedAtMs <= 0 ? Long.MAX_VALUE : System.currentTimeMillis() - s.updatedAtMs;
        s.stale = age > STALE_MS;
        s.message = s.stale ? "红魔数据已暂停" : "红魔数据实时";
        return s;
    }

    public File metricsFile() {
        File dir = context.getExternalFilesDir(null);
        return new File(dir, METRICS_FILE);
    }

    public File bridgeScriptFile() {
        File dir = context.getExternalFilesDir(null);
        return new File(dir, SCRIPT_FILE);
    }

    public void ensureBridgeScript() {
        File dir = context.getExternalFilesDir(null);
        if (dir == null) return;
        if (!dir.exists()) dir.mkdirs();
        File script = new File(dir, SCRIPT_FILE);
        try (FileWriter fw = new FileWriter(script, false)) {
            fw.write(scriptText());
            fw.flush();
        } catch (Throwable ignored) { }
    }

    public String shellStartCommand() {
        ensureBridgeScript();
        return "nohup sh /sdcard/Android/data/com.ppt.k90monitor/files/" + SCRIPT_FILE +
                " >/sdcard/Android/data/com.ppt.k90monitor/files/redmagic_bridge.log 2>&1 &";
    }

    public String shellStopCommand() {
        return "pkill -f redmagic_bridge.sh";
    }

    public String metricsPathForShell() {
        return "/sdcard/Android/data/com.ppt.k90monitor/files/" + METRICS_FILE;
    }

    public static String formatPower(float w) {
        if (Float.isNaN(w)) return "--";
        if (Math.abs(w - Math.round(w)) < 0.05f) return String.format(Locale.US, "%d W", Math.round(w));
        return String.format(Locale.US, "%.1f W", w);
    }

    private String scriptText() {
        return "#!/system/bin/sh\n" +
                "OUT=/sdcard/Android/data/com.ppt.k90monitor/files/redmagic_metrics.txt\n" +
                "TMP=/sdcard/Android/data/com.ppt.k90monitor/files/redmagic_metrics.tmp\n" +
                "mkdir -p /sdcard/Android/data/com.ppt.k90monitor/files\n" +
                "TEMP=\nRPM=\nPOWER=\n" +
                "write_state() {\n" +
                "  NOW=$(date +%s)\n" +
                "  {\n" +
                "    echo TEMP=$TEMP\n" +
                "    echo RPM=$RPM\n" +
                "    echo POWER=$POWER\n" +
                "    echo UPDATED=$NOW\n" +
                "  } > $TMP\n" +
                "  mv $TMP $OUT\n" +
                "}\n" +
                "logcat -v brief -T 1 -s neoDevice:V '*:S' | while IFS= read -r line; do\n" +
                "  case \"$line\" in\n" +
                "    *\"onTemperature values=[\"*)\n" +
                "      V=$(echo \"$line\" | sed -n 's/.*onTemperature values=\\[\\(-\\{0,1\\}[0-9][0-9]*\\)\\].*/\\1/p')\n" +
                "      if [ -n \"$V\" ]; then TEMP=$V; write_state; fi\n" +
                "      ;;\n" +
                "    *\"onTemp changed [\"*)\n" +
                "      V=$(echo \"$line\" | sed -n 's/.*onTemp changed \\[\\(-\\{0,1\\}[0-9][0-9]*\\)\\].*/\\1/p')\n" +
                "      if [ -n \"$V\" ]; then TEMP=$V; write_state; fi\n" +
                "      ;;\n" +
                "    *\"showTemperature=\"*)\n" +
                "      V=$(echo \"$line\" | sed -n 's/.*showTemperature=\\(-\\{0,1\\}[0-9][0-9]*\\).*/\\1/p')\n" +
                "      if [ -n \"$V\" ]; then TEMP=$V; write_state; fi\n" +
                "      ;;\n" +
                "    *\"onFanSpeed value=\"*)\n" +
                "      V=$(echo \"$line\" | sed -n 's/.*onFanSpeed value=\\([0-9][0-9]*\\).*/\\1/p')\n" +
                "      if [ -n \"$V\" ]; then RPM=$V; write_state; fi\n" +
                "      ;;\n" +
                "    *\"onFanPower value=\"*)\n" +
                "      V=$(echo \"$line\" | sed -n 's/.*onFanPower value=\\([0-9][0-9]*\\).*/\\1/p')\n" +
                "      if [ -n \"$V\" ]; then POWER=$V; write_state; fi\n" +
                "      ;;\n" +
                "  esac\n" +
                "done\n";
    }
}
