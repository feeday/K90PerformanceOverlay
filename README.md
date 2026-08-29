# K90 性能悬浮监控

![K90](https://github.com/feeday/K90PerformanceOverlay/blob/main/up.jpg)

面向 **Redmi K90 / HyperOS / Android 16** 的轻量悬浮性能监控 APK。

当前版本：**5.4**

项目核心目标是：**普通权限、无需 Root、无需 Shizuku，也能稳定显示 K90 的系统性能与温度信息。**

红魔散热器数据属于可选扩展，不是系统监控运行的必要条件。即使没有红魔散热器、没有连接红魔官方 App、没有任何桥接数据，K90 系统悬浮监控依然可以正常使用。

---

## 主要功能

### K90 系统监控

- CPU 实时总占用率
- CPU 当前最高核心频率
- CPU / CPUSS 温度
- GPU / GPUSS 温度
- BAT 电池温度
- RAM 已用 / 总内存 / 占用率
- 默认约 1 秒刷新一次
- 半透明悬浮窗
- 悬浮框可拖动
- 长按悬浮框关闭
- Android 16 前台服务
- 无需 Root
- 无需 Shizuku
- 无需连接任何外设

### 红魔散热器扩展

支持显示红魔散热器官方 App 已解析出的：

- 背夹温度
- 风扇转速 RPM
- 实时功耗 W

红魔数据采用**条件显示**：

- 没有实时风扇数据时，悬浮窗完全不显示 REDMAGIC 区域
- 检测到有效且未过期的 RPM 数据后，自动显示红魔区域
- 红魔数据中断或过期后，红魔区域自动隐藏
- 红魔数据异常不会影响 CPU / GPU / BAT / RAM 系统监控

---

## 悬浮窗效果

### 普通模式

没有红魔实时数据时，只显示 K90 系统信息：

```text
K90 MONITOR 5.4
CPU  12.7%  1.02GHz  51.7°C
GPU  48.8°C   BAT  39.5°C
RAM  7.5G / 14.8G  51.1%
长按关闭
```

### 红魔数据可用时

检测到有效的红魔风扇实时数据后，自动追加：

```text
K90 MONITOR 5.4
CPU  12.7%  1.02GHz  51.7°C
GPU  48.8°C   BAT  39.5°C
RAM  7.5G / 14.8G  51.1%

REDMAGIC
FAN 3540 RPM   CLAMP 19.0°C
PWR 4 W
长按关闭
```

没有红魔数据时，不会显示：

```text
REDMAGIC
FAN --
CLAMP --
PWR --
```

而是整块隐藏。

---

## K90 实机适配

当前主要针对以下环境开发与测试：

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
| GPU 占用率 | ❌ 普通 APK 被 SELinux 限制 |
| GPU 频率 | ❌ 普通 APK 被 SELinux 限制 |
| 红魔背夹温度 | ⚙️ 可选扩展 |
| 红魔风扇 RPM | ⚙️ 可选扩展 |
| 红魔功耗 | ⚙️ 可选扩展 |

---

## 数据读取方式

K90 系统数据主要通过 Android 公共 API 和普通应用可访问的系统节点读取：

- RAM：`ActivityManager.MemoryInfo`
- 电池温度：`ACTION_BATTERY_CHANGED`
- CPU 占用：多级回退读取
  - `/proc/stat`
  - CPU idle 累计时间差值
  - Android `top` 输出回退
- CPU 频率：`/sys/devices/system/cpu/cpu*/cpufreq/`
- CPU / GPU 温度：动态扫描 `/sys/class/thermal/thermal_zone*`

针对 K90 / HyperOS，CPU 占用率加入了多级回退策略，即使某些 `/proc` 数据受到限制，也尽量保证悬浮窗能够持续得到可用结果。

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

也就是说：节点存在，但 SELinux 不允许普通 App 读取。

因此本项目不显示无意义的 GPU `N/A` 占用率和频率，只保留目前可以稳定读取的 GPU 温度。

```text
CPU/GPU thermal 温度    → 普通 APK 可读取
GPU utilization / clock → 当前 HyperOS 普通 APK 不可读取
```

---

## 红魔散热器数据原理

红魔散热器 8 Pro 由红魔官方 App 负责 BLE 连接、设备鉴权和控制。

官方 App 在获得散热器数据后，会在系统日志中输出已经解析好的信息，例如：

```text
onTemperature values=[19]
onFanSpeed value=3540
onFanPower value=4
```

因此本项目不需要再次控制散热器，也不与红魔官方 App 抢占 BLE 连接。

当前扩展方式是把具有 shell/root 权限的环境读取到的红魔日志写入：

```text
/sdcard/Android/data/com.ppt.k90monitor/files/redmagic_metrics.txt
```

格式示例：

```text
TEMP=19
RPM=3540
POWER=4
UPDATED=1787980000
```

APK 只读取这个文件并显示数据。

### 为什么 APK 不能直接读取红魔 App 日志

Android 16 下普通第三方 APK 无权读取其他 App 的完整 logcat。

项目曾尝试 `READ_LOGS`，但在当前 K90 / HyperOS 环境中，shell 也无法通过普通 `pm grant` 为第三方 APK 授予该权限。

因此当前版本不会要求 `READ_LOGS`，也不会因为缺少该权限影响系统监控。

### 当前红魔桥接方式

如果希望显示红魔数据，需要一个具有 shell 或 root 权限的环境读取 `neoDevice` 日志，再写入桥接文件。

应用首次启动时会在自身外部目录生成：

```text
/sdcard/Android/data/com.ppt.k90monitor/files/redmagic_bridge.sh
```

当前可通过 AYA shell 等具有 shell 权限的环境运行。

这只是**红魔扩展的可选功能**，不是 K90 系统监控的必要步骤。

后续计划加入 Shizuku 方式，由 APK 内直接调用 shell 身份执行桥接，减少手动操作。

---

## 使用方法

### 只使用 K90 系统监控

1. 安装 APK
2. 打开应用
3. 授予“显示在其他应用上层 / 悬浮窗”权限
4. 点击“开始 K90 系统悬浮监控”
5. 拖动悬浮框调整位置
6. 长按悬浮框关闭

不需要红魔散热器，不需要 AYA，不需要蓝牙权限。

### 使用红魔散热器扩展

1. 打开红魔官方 App
2. 让官方 App 正常连接散热器
3. 确认官方 App 能显示背夹温度 / RPM / 功耗
4. 启动红魔日志桥接
5. K90 悬浮窗检测到有效 RPM 后自动显示 REDMAGIC 区域

如果散热器断开或数据停止更新，REDMAGIC 区域会自动消失，系统监控继续运行。

---

## HyperOS 后台建议

如果锁屏或长时间后台后悬浮服务被 HyperOS 回收，可以在系统应用管理中：

- 允许后台运行
- 关闭本应用省电限制
- 必要时允许自启动

---

## GitHub Actions 一键构建 APK

仓库已经配置 GitHub Actions，不需要在电脑安装 Android Studio。

1. 打开仓库顶部 `Actions`
2. 选择 `Build APK`
3. 点击 `Run workflow`
4. 等待构建显示 `Success`
5. 打开对应构建记录
6. 在 `Artifacts` 下载 APK 构建产物

每次向 `main` 分支提交 Android 工程相关代码时，也会自动触发构建。

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
Build → Build APK(s)
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

红魔遥测属于可选功能，可在更高权限环境可用时自动扩展显示。

---

## 安装安全提示

GitHub Actions 当前主要生成 **debug 签名 APK**，适合个人设备侧载、测试和开发验证。

如果未来正式发布，应使用独立 release keystore 签名，并安全保管签名材料。
