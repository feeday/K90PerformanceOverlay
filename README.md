# K90 性能悬浮监控

![k90.png](https://github.com/feeday/K90PerformanceOverlay/blob/main/k90.png)



面向 **Redmi K90 / HyperOS / Android 16** 的轻量悬浮性能监控 APK。

当前稳定版：**4.1 Stable**

项目以“普通权限、无需 Root、稳定悬浮”为目标，优先显示 K90 上能够可靠读取的性能数据。针对 HyperOS / SELinux 明确禁止普通 APK 访问的 GPU KGSL/devfreq 节点，稳定版不再显示无意义的 `N/A` GPU 占用率和频率。

## 当前功能

- CPU：实时总占用率
- CPU：当前最高核心频率
- CPU：CPU / CPUSS 温度
- GPU：GPU / GPUSS 温度
- BAT：电池温度
- RAM：已用内存 / 总内存 / 占用率
- 默认每 1 秒刷新
- 半透明悬浮窗
- 悬浮框可拖动
- 长按悬浮框关闭
- Android 16 前台服务
- 无需 Root
- 无需 Shizuku

## 悬浮窗示例

```text
K90 MONITOR 4.1  ·  拖动
CPU  12.7%  1.02GHz  51.7°C
GPU  48.8°C   BAT  39.5°C
RAM  7.5G / 14.8G  51.1%
长按关闭
```

## K90 实机适配情况

已针对以下设备环境进行实际测试：

```text
Device: annibale
Model: 2510DRK44C
SoC: Qualcomm SM8750
Platform: sun
Android API: 36
HyperOS: Android 16
```

当前确认：

| 指标 | K90 状态 |
| --- | --- |
| CPU 占用率 | ✅ 可读取 |
| CPU 频率 | ✅ 可读取 |
| CPU / CPUSS 温度 | ✅ 可读取 |
| GPU / GPUSS 温度 | ✅ 可读取 |
| RAM | ✅ 可读取 |
| 电池温度 | ✅ 可读取 |
| GPU 占用率 | ❌ 普通 APK 被 SELinux 限制 |
| GPU 频率 | ❌ 普通 APK 被 SELinux 限制 |

K90 上 Qualcomm KGSL GPU 节点实际存在，例如：

```text
/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage
/sys/class/kgsl/kgsl-3d0/gpubusy
/sys/class/kgsl/kgsl-3d0/devfreq/gpu_load
/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq
/sys/class/kgsl/kgsl-3d0/clock_mhz
/sys/class/kgsl/kgsl-3d0/gpuclk
```

但在当前 HyperOS / Android 16 环境下，普通第三方 APK 对这些节点均表现为：

```text
EXISTS=true
READ=false
```

因此 4.1 Stable 主动移除了 GPU 占用率和 GPU 频率显示，只保留能够稳定读取的 GPU 温度。

## 数据读取方式

项目采用 Android 公共 API和普通应用可访问的系统节点：

- RAM：`ActivityManager.MemoryInfo`
- 电池温度：`ACTION_BATTERY_CHANGED`
- CPU 占用：多级回退读取
  - `/proc/stat`
  - CPU idle 累计时间差值
  - Android `top` 输出回退
- CPU 频率：`/sys/devices/system/cpu/cpu*/cpufreq/`
- CPU / GPU 温度：动态扫描 `/sys/class/thermal/thermal_zone*`

其中 CPU 占用率的多级回退是针对 K90 / HyperOS 做的适配，因此即使 `/proc/stat` 受限，仍有机会通过其他普通权限接口得到实时 CPU 使用率。

## GitHub 一键构建 APK

本仓库已经配置 GitHub Actions，不需要在电脑安装 Android Studio。

1. 打开仓库顶部 **Actions**。
2. 左侧选择 **Build APK**。
3. 点击 **Run workflow**。
4. 等待构建变成绿色 **Success**。
5. 打开该次运行页面。
6. 在页面底部 **Artifacts** 下载 `K90PerformanceOverlay-APK`。
7. 解压后得到：

```text
K90PerformanceOverlay-debug.apk
```

每次向 `main` 分支提交 Android 工程相关代码时也会自动触发构建。

## 本地构建

构建环境：

- JDK 17
- Android SDK 36
- Build Tools 36.0.0
- Android Gradle Plugin 8.13.2
- Gradle 8.13

Android Studio 中执行：

```text
Build → Build APK(s)
```

APK 默认输出：

```text
app/build/outputs/apk/debug/app-debug.apk
```

Windows 也可以使用仓库中的一键构建脚本。

## HyperOS 使用方法

1. 安装 APK。
2. 打开应用。
3. 授予“显示在其他应用上层 / 悬浮窗”权限。
4. 点击“开始悬浮监控”。
5. 拖动悬浮框可以调整位置。
6. 长按悬浮框可以关闭监控。

如果锁屏或长时间后台后悬浮服务被 HyperOS 回收，可以在系统应用管理中：

- 允许后台运行
- 关闭本应用省电限制
- 必要时允许自启动

## 关于安兔兔温度显示

安兔兔能够显示 `cpuss-*`、`gpuss-*` 等多路温度，并不代表普通 APK 同样能够读取 GPU 占用率和 GPU 频率。

K90 当前开放了 thermal 温度节点，因此本项目可以读取 CPU / GPU 温度；但 KGSL/devfreq 性能节点受到更严格的 SELinux 权限控制。

所以两类数据需要区分：

```text
CPU/GPU thermal 温度    → 普通 APK 可读取
GPU utilization / clock → 当前 HyperOS 普通 APK 不可读取
```

## 安装安全提示

GitHub Actions 当前生成的是 **debug 签名 APK**，适合个人手机侧载和测试。

如果未来用于正式发布，应使用自己的 release keystore 签名，并将签名材料通过 GitHub Secrets 等方式安全管理。
