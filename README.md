# K90 性能悬浮监控

![K90](https://github.com/feeday/K90PerformanceOverlay/blob/main/up.jpg)

面向 **Redmi K90 / HyperOS / Android 16** 的轻量悬浮性能监控 APK。

当前版本：**5.6**

项目核心目标是：**普通权限、无需 Root、无需 Shizuku，也能稳定显示 K90 的系统性能、温度和实时网速。**

红魔散热器属于可选扩展。即使没有红魔散热器、没有连接红魔官方 App、没有运行任何桥接，K90 系统悬浮监控仍然可以独立使用。

---

## 主要功能

### K90 系统监控

- CPU 实时总占用率
- CPU 当前最高核心频率
- CPU / CPUSS 温度
- GPU / GPUSS 温度
- BAT 电池温度
- RAM 已用 / 总内存 / 占用率
- 实时下载速度
- 实时上传速度
- 默认约 1 秒刷新一次
- 半透明悬浮窗
- 悬浮框可拖动
- 长按悬浮框关闭
- Android 16 前台服务
- 无需 Root
- 无需 Shizuku
- 无需连接任何外设

> 已移除屏幕刷新率 / 帧率显示。普通 APK 无法可靠读取其他游戏的真实渲染 FPS，因此不再用 `120 Hz` 冒充游戏帧率。

### 两种显示模式

应用支持两种悬浮窗显示模式，并会记住上一次选择。

#### 温度模式

只显示：

- CPU 温度
- GPU 温度
- 红魔背夹温度（仅有实时红魔数据时显示）

示例：

```text
K90 MONITOR 5.6
CPU  40.1°C   GPU 33.0°C
CLAMP -3.0°C
```

如果没有红魔实时数据：

```text
K90 MONITOR 5.6
CPU  40.1°C   GPU 33.0°C
```

#### 全部模式

显示完整系统信息：

```text
K90 MONITOR 5.6
CPU  10.6%  1.79GHz  40.1°C
GPU  33.0°C   BAT 33.3°C
RAM  7.1G / 14.8G  48.3%
NET ↓0B/s  ↑117B/s
```

如果检测到红魔实时数据，会自动追加：

```text
REDMAGIC
FAN 6520 RPM   CLAMP -3.0°C
PWR 37 W
```

---

## 红魔散热器扩展

当前针对 **REDMAGIC Cooler 8 Pro / 红魔散热背夹 8 Pro** 做了只读遥测适配。

支持显示：

- 背夹温度
- 风扇转速 RPM
- 实时功耗 W

红魔数据采用**条件显示**：

- 没有实时 RPM 时，悬浮窗完全不显示 REDMAGIC 区域
- 检测到有效且未过期的 RPM 后，自动显示红魔区域
- 红魔数据中断或过期后，红魔区域自动隐藏
- 红魔数据异常不会影响 K90 系统监控

### 负温度支持

5.6 已确认支持负温度，例如：

```text
-1°C
-2°C
-3°C
```

红魔官方日志实测会出现：

```text
Jacket8ProViewModel onTemperature values=[-2]
Jacket8ProViewModel onTemperature values=[-3]
```

同时原始数据也能看到：

```text
[fe] -> -2°C
[fd] -> -3°C
```

5.6 的桥接脚本直接读取：

```text
Jacket8ProViewModel onTemperature values=[...]
```

避免再被其他中间状态温度字段覆盖成 `0°C`。

---

## 红魔数据工作方式

红魔官方 App 负责：

```text
BLE 连接
设备鉴权
散热器控制
官方数据解析
```

K90 Performance Overlay **不会主动连接红魔散热器，也不会发送 BLE 控制指令**。

官方 App 连接散热器后会输出类似日志：

```text
Jacket8ProViewModel onTemperature values=[-3]
Jacket8ProViewModel onFanSpeed value=6520
Jacket8ProViewModel onFanPower value=37
```

桥接脚本将最新结果写入：

```text
/sdcard/Android/data/com.ppt.k90monitor/files/redmagic_metrics.txt
```

文件格式：

```text
TEMP=-3
RPM=6520
POWER=37
UPDATED=1787980000
```

APK 只读取这个文件并显示数据。

### 为什么 APK 不能直接读取红魔 App 日志

Android 16 下，普通第三方 APK 无权读取其他 App 的完整 logcat。

项目曾尝试 `READ_LOGS`，但当前 K90 / HyperOS 环境下无法通过普通 `pm grant` 为第三方 APK 获得该权限。

因此：

```text
K90 系统监控      -> 普通 APK 独立运行
红魔官方日志读取   -> 需要 shell/root 级桥接
```

这不会影响普通的 CPU / GPU / BAT / RAM / NET 监控。

---

## 红魔桥接脚本

应用启动后会自动生成：

```text
/sdcard/Android/data/com.ppt.k90monitor/files/redmagic_bridge.sh
```

目前可通过 AYA shell 等具有 shell 权限的环境运行。

5.6 对桥接做了单实例保护：

- 启动新版桥接前自动结束旧 `redmagic_bridge.sh`
- 清理旧 metrics / tmp 文件
- 使用 PID 文件避免多个桥接实例同时运行
- 避免多个旧脚本互相抢写 `TEMP=0`

PID 文件：

```text
/sdcard/Android/data/com.ppt.k90monitor/files/redmagic_bridge.pid
```

如果怀疑数据不正确，可以直接检查：

```sh
cat /sdcard/Android/data/com.ppt.k90monitor/files/redmagic_metrics.txt
```

例如官方 App 显示 `-3°C` 时，应看到：

```text
TEMP=-3
RPM=6520
POWER=37
```

---

## K90 实机适配

当前主要针对以下设备环境开发与测试：

```text
Device: annibale
Model: 2510DRK44C
SoC: Qualcomm SM8750
Platform: sun
Android API: 36
HyperOS: Android 16
```

当前数据支持情况：

| 指标 | 状态 |
| --- | --- |
| CPU 占用率 | ✅ 可读取 |
| CPU 频率 | ✅ 可读取 |
| CPU / CPUSS 温度 | ✅ 可读取 |
| GPU / GPUSS 温度 | ✅ 可读取 |
| RAM | ✅ 可读取 |
| 电池温度 | ✅ 可读取 |
| 实时下载速度 | ✅ 可读取 |
| 实时上传速度 | ✅ 可读取 |
| GPU 占用率 | ❌ 普通 APK 被 SELinux 限制 |
| GPU 频率 | ❌ 普通 APK 被 SELinux 限制 |
| 游戏真实 FPS | ❌ 普通 APK 无法可靠获取 |
| 红魔背夹温度 | ⚙️ 可选扩展 |
| 红魔风扇 RPM | ⚙️ 可选扩展 |
| 红魔功耗 | ⚙️ 可选扩展 |

---

## 系统数据读取方式

K90 系统数据主要通过 Android 公共 API 和普通应用可访问的系统节点读取：

- RAM：`ActivityManager.MemoryInfo`
- 电池温度：`ACTION_BATTERY_CHANGED`
- 网络流量：`TrafficStats.getTotalRxBytes()` / `getTotalTxBytes()`
- CPU 占用：多级回退
  - `/proc/stat`
  - CPU idle 累计时间差值
  - Android `top` 输出回退
- CPU 频率：`/sys/devices/system/cpu/cpu*/cpufreq/`
- CPU / GPU 温度：动态扫描 `/sys/class/thermal/thermal_zone*`

实时网速通过总收发字节增量计算：

```text
NET ↓26.8MB/s  ↑3.2MB/s
```

---

## GPU 占用率与频率为什么不显示

K90 上 Qualcomm KGSL 相关节点实际存在，例如：

```text
/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage
/sys/class/kgsl/kgsl-3d0/gpubusy
/sys/class/kgsl/kgsl-3d0/devfreq/gpu_load
/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq
/sys/class/kgsl/kgsl-3d0/clock_mhz
/sys/class/kgsl/kgsl-3d0/gpuclk
```

但在当前 HyperOS / Android 16 环境下，普通第三方 APK 对这些节点通常表现为：

```text
EXISTS=true
READ=false
```

因此项目不显示无意义的 GPU `N/A` 占用率和频率，只保留能够稳定读取的 GPU 温度。

```text
CPU/GPU thermal 温度    -> 普通 APK 可读取
GPU utilization / clock -> 当前 HyperOS 普通 APK 不可读取
```

---

## 使用方法

### 只使用 K90 系统监控

1. 安装 APK
2. 打开应用
3. 授予“显示在其他应用上层 / 悬浮窗”权限
4. 选择“温度模式”或“全部模式”
5. 点击“开始悬浮监控”
6. 拖动悬浮框调整位置
7. 长按悬浮框关闭

不需要：

```text
Root
Shizuku
READ_LOGS
蓝牙权限
红魔官方 App
红魔散热器
AYA
```

### 使用红魔散热器扩展

1. 安装并打开最新版 APK 一次，让应用重新生成 5.6 桥接脚本
2. 打开红魔官方 App
3. 让官方 App 正常连接散热器
4. 确认官方 App 能显示背夹温度 / RPM / 功耗
5. 在具有 shell 权限的环境中启动新版桥接命令
6. K90 悬浮窗检测到有效 RPM 后自动显示 REDMAGIC 区域

如果散热器断开或数据停止更新，REDMAGIC 区域会自动隐藏，系统监控继续运行。

---

## HyperOS 后台建议

如果锁屏或长时间后台后悬浮服务被 HyperOS 回收，可以在系统应用管理中：

- 允许后台运行
- 关闭本应用省电限制
- 必要时允许自启动

---

## GitHub Actions 一键构建 APK

仓库已经配置 `Build APK` workflow，不需要在电脑安装 Android Studio。

构建流程会：

- Checkout 最新 `main`
- 输出当前 commit / versionCode / versionName
- 安装 Android 16 SDK
- 执行 `clean assembleDebug`
- 生成 APK SHA256
- 生成 `BUILD_INFO.txt`
- 上传 Artifact：`K90PerformanceOverlay-APK`

使用方法：

1. 打开仓库顶部 `Actions`
2. 选择 `Build APK`
3. 点击 `Run workflow`
4. 等待构建显示 `Success`
5. 打开对应构建记录
6. 在 `Artifacts` 下载 `K90PerformanceOverlay-APK`

Artifact 中包含：

```text
K90PerformanceOverlay-debug.apk
K90PerformanceOverlay-debug.apk.sha256
BUILD_INFO.txt
```

---

## 本地构建

推荐环境：

```text
JDK 17
Android SDK 36
Android Build Tools 36.0.0
Gradle 8.13
```

Android Studio：

```text
Build -> Build APK(s)
```

默认输出：

```text
app/build/outputs/apk/debug/app-debug.apk
```

---

## 权限原则

K90 系统监控坚持尽量使用普通 Android 权限和公开可访问数据源。

系统监控不要求：

```text
Root
Shizuku
READ_LOGS
蓝牙权限
红魔官方 App
红魔散热器
```

红魔遥测属于可选功能，只在存在有效桥接数据时才显示。

---

## 安装安全提示

GitHub Actions 当前主要生成 **debug 签名 APK**，适合个人设备侧载、测试和开发验证。

如果未来正式发布，应使用独立 release keystore 签名，并安全保管签名材料。
