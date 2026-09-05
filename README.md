# K90 性能悬浮监控

![K90](https://github.com/feeday/K90PerformanceOverlay/blob/main/up.jpg)

面向 **Redmi K90 / HyperOS / Android 16** 的轻量悬浮性能监控 APK。

当前版本：**5.7**

项目核心目标：**普通权限、无需 Root、无需 Shizuku，也能稳定显示 K90 的系统性能、温度和实时网速。**

当前还集成：

- CPU 压力测试 + CPU 频率曲线
- 局域网 FTP 文件传输
- 悬浮窗内 FTP 快速开关
- 可选红魔散热器遥测扩展
- Android 15 / 16 Edge-to-Edge 安全区适配

红魔散热器属于可选扩展。即使没有红魔散热器、没有连接红魔官方 App、没有运行任何桥接，K90 系统悬浮监控和 CPU 压力测试仍然可以独立使用。

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
- 全部模式下可直接开 / 关 FTP
- Android 16 前台服务
- 无需 Root
- 无需 Shizuku
- 无需连接任何外设

> 已移除屏幕刷新率 / 帧率显示。普通 APK 无法可靠读取其他游戏的真实渲染 FPS，因此不再用 `120 Hz` 冒充游戏帧率。

---

## 两种悬浮显示模式

应用支持两种悬浮窗显示模式，并会记住上一次选择。

### 温度模式

温度模式为 **极简单行悬浮窗**。

显示顺序：

```text
C:CPU温度  G:GPU温度  B:电池温度  B:背夹温度
```

示例：

```text
C:60  G:30  B:30  B:18
```

对应含义：

- `C`：CPU 温度
- `G`：GPU 温度
- 第一个 `B`：BAT 电池温度
- 第二个 `B`：红魔背夹 / CLAMP 温度

温度模式特点：

- 只显示一行
- 不显示 `K90 MONITOR` 标题
- 不显示“长按关闭”提示文字
- 不显示 FTP 开关
- 不显示 `°C`
- 不显示小数，四舍五入为整数
- 悬浮窗仍然可以拖动
- 长按关闭功能仍然保留，只是不显示提示文字
- **某项没有有效数据时整项隐藏，不显示 `--`**

例如背夹没有实时数据时：

```text
C:60  G:30  B:30
```

如果 GPU 也没有有效数据：

```text
C:60  B:30
```

### 全部模式

全部模式已经移除顶部 `K90 MONITOR` 标题，主体直接显示监控数据：

```text
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

底部控制区保持一行，避免增加悬浮窗高度：

```text
FTP:关    长按关闭
```

FTP 已运行时：

```text
FTP:开    长按关闭
```

其中：

- 点击 `FTP:开 / 关` 可直接切换 FTP 服务
- 长按悬浮框仍然可以关闭悬浮监控
- FTP 状态会随悬浮窗约 1 秒刷新

---

## CPU 压力测试

当前压力测试已经 **只保留 CPU**。

GPU 压力测试和 CPU + GPU 双烤已移除，原因是当前 K90 / HyperOS 普通 APK 无法稳定读取 GPU 占用率和实时频率，单纯制造 GPU 高负载缺少足够直观的可对比结果。

CPU 压力测试支持：

```text
5 分钟
10 分钟
15 分钟
30 分钟
60 分钟 / 1 小时
```

### 测试方式

开始测试后会根据：

```text
Runtime.getRuntime().availableProcessors()
```

创建对应数量的 CPU 工作线程，并持续执行整数与浮点计算，使 CPU 保持高负载。

测试期间约每秒采样一次。

CPU 频率优先读取：

```text
/sys/devices/system/cpu/cpu*/cpufreq/scaling_cur_freq
```

读取失败时回退：

```text
/sys/devices/system/cpu/cpu*/cpufreq/cpuinfo_cur_freq
```

再失败时回退到现有系统监控可读取的 CPU 频率。

### CPU 频率统计

每次采样会读取所有可读在线 CPU 核心频率。

曲线使用：

```text
当前所有可读在线核心的平均频率
```

测试结束会显示：

- CPU 平均频率
- CPU 最高频率
- CPU 最低频率
- CPU 频率采样次数
- CPU 平均占用率
- CPU 最高温度
- BAT 最高温度
- 实际测试时长

### CPU 频率曲线

压力测试页面内置实时曲线图。

- 横轴：测试时间
- 纵轴：CPU 平均频率 / GHz
- 蓝线：每秒采样的 CPU 平均频率变化
- 橙色参考线：整个测试期间的平均频率

曲线适合观察：

```text
开始阶段高频
        ↓
温度上升
        ↓
是否出现持续降频
        ↓
散热背夹开启后是否改善
```

因此可以用于对比：

- 不使用散热器
- 使用普通散热器
- 使用红魔背夹
- 不同环境温度
- 不同测试时长

### 温度保护

为了避免异常高温持续烧机，CPU 压力测试包含自动保护：

```text
CPU >= 105°C  -> 自动停止
BAT >= 55°C   -> 自动停止
```

离开压力测试页面时也会停止当前压力负载。

---

## FTP 文件传输

内置 **局域网 FTP Server**，可以直接在手机和电脑之间传文件。

FTP 与性能监控服务相互独立，但现在有两种控制方式：

1. 在 APP 主界面启动 / 停止 FTP
2. 在“全部模式”悬浮窗底部直接点击 `FTP:开 / 关`

### 默认设置

```text
端口：2121
用户名：k90
密码：123456
```

端口、用户名和密码都可以在应用主界面修改。

悬浮窗直接启动 FTP 时，会使用主界面已保存的端口、用户名和密码。

启动后会显示当前 FTP 地址，例如：

```text
ftp://192.168.1.100:2121
```

电脑和手机需要处于可以互相访问的同一局域网中。

### 支持功能

- 启动 FTP
- 停止 FTP
- 悬浮窗快速开 / 关 FTP
- 显示当前 FTP 地址
- 一键复制 FTP 地址
- 用户名 / 密码认证
- 文件上传
- 文件下载
- 文件夹浏览
- 新建目录
- 删除文件
- 删除空目录
- 文件 / 目录重命名
- `SIZE`
- `MDTM`
- `REST` 断点读取
- PASV 被动模式
- EPSV 扩展被动模式

可使用：

```text
FileZilla
WinSCP
Windows / Android FTP 客户端
其他标准 FTP 客户端
```

### FTP 共享目录

如果 Android 11+ 已授予“所有文件访问”权限：

```text
FTP 根目录 -> 手机内部存储根目录
```

如果没有授予“所有文件访问”权限：

```text
FTP 根目录 -> 本应用自己的 ftp 文件夹
```

也就是说，不授权全部存储时，FTP 仍然可以使用，只是访问范围会被限制在应用目录内。

### FTP 安全说明

FTP 本身是明文协议，用户名、密码和文件内容不会像 SFTP / HTTPS 那样加密。

建议：

- 只在可信局域网中使用
- 使用后及时停止 FTP
- 不要长期使用默认密码 `123456`
- 不建议直接把 FTP 端口暴露到公网

---

## Android 15 / 16 系统栏适配

项目 `targetSdk` 为 Android API 36。

Android 15 / 16 会更积极地启用 Edge-to-Edge，普通页面如果没有处理系统 Insets，容易出现：

- 顶部标题被状态栏 / 挖孔区域覆盖
- 底部按钮被手势导航条覆盖
- 横屏左右内容贴到系统导航区域

当前已经增加全局安全区处理：

- 状态栏 Insets
- 导航栏 Insets
- Display Cutout / 挖孔区域
- 横屏左右安全区域

并修复了一次安全区适配引起的运行时闪退。

之前的问题代码使用了系统资源 ID 作为 `View.setTag(int, Object)` 的 key，在部分 Android / HyperOS 设备上可能抛出 `IllegalArgumentException`。

当前实现已移除该危险标记逻辑，并增加异常保护：

```text
安全区适配失败 -> 不允许导致整个 Activity 闪退
```

---

## 红魔散热器扩展

当前针对 **REDMAGIC Cooler 8 Pro / 红魔散热背夹 8 Pro** 做了遥测适配。

支持显示：

- 背夹温度
- 风扇转速 RPM
- 实时功耗 W

红魔数据采用**条件显示**：

- 没有实时 RPM 时，悬浮窗不显示无效红魔数据
- 检测到有效且未过期的 RPM 后，自动显示红魔数据
- 红魔数据中断或过期后，相关数据自动隐藏
- 红魔数据异常不会影响 K90 系统监控
- 温度模式下没有背夹温度时，第二个 `B` 整项隐藏

### 负温度支持

已确认支持负温度，例如：

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

桥接脚本直接读取：

```text
Jacket8ProViewModel onTemperature values=[...]
```

避免被其他中间状态温度字段覆盖成 `0°C`。

---

## 红魔数据工作方式

当前稳定使用方式仍然是由红魔官方 App 负责：

```text
BLE 连接
设备鉴权
散热器控制
官方数据解析
```

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

APK 读取这个文件并显示实时遥测数据。

### 为什么普通 APK 不能直接读取红魔 App 日志

Android 16 下，普通第三方 APK 无权读取其他 App 的完整 logcat。

项目曾尝试 `READ_LOGS`，但当前 K90 / HyperOS 环境下无法通过普通 `pm grant` 为第三方 APK 获得该权限。

因此当前稳定方案为：

```text
K90 系统监控      -> 普通 APK 独立运行
红魔官方日志读取   -> 需要 shell/root 级桥接
```

这不会影响普通的 CPU / GPU 温度 / BAT / RAM / NET 监控，也不会影响 CPU 压力测试。

---

## 红魔桥接脚本

应用启动后会自动生成：

```text
/sdcard/Android/data/com.ppt.k90monitor/files/redmagic_bridge.sh
```

目前可通过 AYA shell 等具有 shell 权限的环境运行。

桥接带有单实例保护：

- 启动新版桥接前自动结束旧 `redmagic_bridge.sh`
- 清理旧 metrics / tmp 文件
- 使用 PID 文件避免多个桥接实例同时运行
- 避免多个旧脚本互相抢写 `TEMP=0`

PID 文件：

```text
/sdcard/Android/data/com.ppt.k90monitor/files/redmagic_bridge.pid
```

启动命令可以直接在 APP 中复制。

等价命令示例：

```sh
pkill -f '[r]edmagic_bridge.sh' 2>/dev/null || true; sleep 1; rm -f /sdcard/Android/data/com.ppt.k90monitor/files/redmagic_metrics.txt /sdcard/Android/data/com.ppt.k90monitor/files/redmagic_metrics.tmp /sdcard/Android/data/com.ppt.k90monitor/files/redmagic_bridge.pid; nohup sh /sdcard/Android/data/com.ppt.k90monitor/files/redmagic_bridge.sh >/sdcard/Android/data/com.ppt.k90monitor/files/redmagic_bridge.log 2>&1 &
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
| CPU 压力测试 | ✅ 5 / 10 / 15 / 30 / 60 分钟 |
| CPU 频率曲线 | ✅ 每秒采样 |
| FTP 局域网文件传输 | ✅ 可用 |
| 悬浮窗 FTP 开关 | ✅ 全部模式可用 |
| GPU 占用率 | ❌ 普通 APK 被 SELinux 限制 |
| GPU 频率 | ❌ 普通 APK 被 SELinux 限制 |
| GPU 压力测试 | ❌ 已移除 |
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

这也是当前移除 GPU 压力测试、重点保留 CPU 压力测试与频率曲线的主要原因。

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

### 使用 CPU 压力测试

1. 打开主应用
2. 找到“CPU 压力测试”
3. 点击“进入 CPU 压力测试”
4. 选择 5 / 10 / 15 / 30 / 60 分钟
5. 点击“开始 CPU 压力测试”
6. 页面实时显示 CPU 占用、平均频率、CPU 温度、电池温度
7. 曲线图实时记录频率变化
8. 测试完成后查看平均 / 最高 / 最低频率与完整曲线

### 使用 FTP

主界面方式：

1. 打开应用
2. 找到“FTP 文件传输”
3. 根据需要修改端口、用户名和密码
4. 如需访问整个内部存储，授予“所有文件访问”权限
5. 点击启动 FTP
6. 复制 APP 显示的 FTP 地址
7. 在电脑 FTP 客户端中输入该地址并登录

悬浮窗方式：

1. 使用“全部模式”启动悬浮监控
2. 点击底部 `FTP:关`
3. 状态变为 `FTP:开`
4. 再次点击即可停止 FTP

默认示例：

```text
地址：ftp://192.168.1.100:2121
用户名：k90
密码：123456
```

### 使用红魔散热器扩展

1. 安装并打开最新版 APK 一次，让应用生成桥接脚本
2. 打开红魔官方 App
3. 让官方 App 正常连接散热器
4. 确认官方 App 能显示背夹温度 / RPM / 功耗
5. 在具有 shell 权限的环境中启动桥接命令
6. K90 悬浮窗检测到有效 RPM 后自动显示红魔数据

如果散热器断开或数据停止更新，红魔相关数据会自动隐藏，系统监控继续运行。

---

## HyperOS 后台建议

如果锁屏或长时间后台后悬浮服务或 FTP 服务被 HyperOS 回收，可以在系统应用管理中：

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

基础系统监控和 CPU 压力测试不要求：

```text
Root
Shizuku
READ_LOGS
蓝牙权限
红魔官方 App
红魔散热器
```

FTP 如果只使用应用自己的 FTP 目录，不需要“所有文件访问”；只有需要通过 FTP 管理整个内部存储时才需要额外授权。

红魔遥测属于可选功能，只在存在有效桥接数据时显示。

---

## 最近更新

### 2026-09-05

- 压力测试改为 **CPU-only**
- 移除 GPU 压力测试
- 移除 CPU + GPU 双烤
- 新增 5 / 10 / 15 / 30 / 60 分钟 CPU 压力测试
- 新增 CPU 平均 / 最高 / 最低频率统计
- 新增 CPU 实时频率曲线图
- 曲线按约 1 秒采样在线核心平均频率
- 全部模式移除 `K90 MONITOR` 顶部标题
- 全部模式底部改为 `FTP:开/关    长按关闭`
- 悬浮窗可直接启动 / 停止 FTP
- 温度模式继续保持纯单行显示
- 单 APP / 单桌面图标，压力测试从主界面进入
- 增加 Android 15 / 16 状态栏、导航栏、挖孔安全区适配
- 修复安全区 `View.setTag()` 使用系统资源 ID 导致的运行时闪退

---

## 安装安全提示

GitHub Actions 当前主要生成 **debug 签名 APK**，适合个人设备侧载、测试和开发验证。

如果未来正式发布，应使用独立 release keystore 签名，并安全保管签名材料。
