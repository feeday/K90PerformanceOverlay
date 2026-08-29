# K90 性能悬浮监控

面向 **Redmi K90 / HyperOS / Android 16** 的轻量悬浮性能监控 APK。无需 Root；普通权限能读取到的指标直接显示，受 HyperOS / SELinux 限制的底层节点显示 `N/A`，不会因此闪退。

## 功能

- CPU：总占用率、当前最高核心频率、CPU / CPU-subsystem 温度
- GPU：高通 KGSL GPU 占用率、当前频率、GPU / GPUSS 温度
- RAM：系统已用 / 总量 / 占用率
- BAT：电池温度
- 悬浮窗每 1 秒刷新
- 悬浮框可拖动
- 长按悬浮框关闭
- Android 16 前台服务

## GitHub 一键构建 APK

本仓库已经配置 GitHub Actions，不需要你本地安装 Android Studio。

1. 打开仓库顶部 **Actions**。
2. 左侧选择 **Build APK**。
3. 点击 **Run workflow** → **Run workflow**。
4. 构建完成后进入该次运行页面。
5. 在 **Artifacts** 下载 `K90PerformanceOverlay-APK`。
6. 解压后得到 `K90PerformanceOverlay-debug.apk`，可直接安装到手机。

每次向 `main` 分支提交 Android 工程相关文件时也会自动构建。

## 本地 Android Studio 构建

- JDK 17
- Android SDK 36
- Build Tools 36.0.0
- Android Gradle Plugin 8.13.2
- Gradle 8.13

打开项目后执行 **Build → Build APK(s)**，输出位于：

```text
app/build/outputs/apk/debug/app-debug.apk
```

Windows 也可以双击：

```text
一键构建APK.bat
```

## HyperOS 使用

1. 安装 APK。
2. 打开应用并授予“显示在其他应用上层 / 悬浮窗”权限。
3. 点击“开始悬浮监控”。
4. 如果锁屏或长时间后台后服务被回收，在 HyperOS 应用管理中允许后台运行，并关闭本应用的省电限制。

## 指标读取说明

Android 普通第三方应用不能绕过 SELinux。本项目采用公开 Android API + Qualcomm / Android 常见节点多路径探测：

- RAM：`ActivityManager.MemoryInfo`
- 电池温度：`ACTION_BATTERY_CHANGED`
- CPU 占用：优先 `/proc/stat`
- CPU 频率：`/sys/devices/system/cpu/cpu*/cpufreq/`
- GPU：Qualcomm KGSL / devfreq 常见节点
- CPU / GPU 温度：动态扫描 `/sys/class/thermal/thermal_zone*`

因此在不同 HyperOS 版本上，CPU/GPU 的部分底层数据可能显示 `N/A`。如果 K90 上某项无法读取，可以根据该机实际可访问的 sysfs 节点继续做定向适配。

## 安装安全提示

GitHub Actions 生成的是 **debug 签名 APK**，适合自己手机侧载测试。如果以后需要发布正式版本，应使用你自己的 release keystore 签名，并通过 GitHub Secrets 保存签名材料。
