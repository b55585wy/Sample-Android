# Sample vs FacialCollection 差异分析报告

> 生成时间: 2025-12-15
>
> 项目定位: Sample = FacialCollection + 有线血氧仪

---

## 目录

1. [UI/交互差异](#一-ui交互差异) ✅ 已完成
2. [功能缺失/实现差异](#二-功能缺失实现差异)
3. [设备连接管理差异](#三-设备连接管理差异)
4. [数据存储结构差异](#四-数据存储结构差异)
5. [参数配置对比](#五-参数配置对比)
6. [已知Bug/问题](#六-已知bug问题)
7. [修复优先级建议](#七-修复优先级建议)

---

## 一、UI/交互差异 ✅ 已全部完成

### 1.1 摄像头标签文字显示不全 ✅ 已修复

~~| 项目 | 当前值 | 目标值 |~~
~~|------|--------|--------|~~
~~| Sample | "前置摄像头" / "后置摄像头" | "前置录制" / "后置录制" |~~

**修复结果:** 已改为 "前置" / "后置" / "双摄" 三个简短标签

---

### 1.2 摄像头启动时机不一致 ✅ 已修复

~~点击按钮立即启动摄像头~~ → 现在点击按钮只更新选择状态，开始录制时才启动摄像头

---

### 1.3 预览显示位置不一致 ✅ 已修复

~~浮动弹窗卡片~~ → 现在在CameraViewHolder卡片内显示预览

---

### 1.4 未录制时的占位符提示 ✅ 已修复

已实现：灰色占位符 + "点击开始录制显示预览" / "摄像头启动中..."

---

## 二、功能缺失/实现差异

### 2.1 双摄同开 ✅ 已实现（画中画模式）

~~| 项目 | Sample (当前) | FacialCollection (目标) |~~
~~|------|--------------|------------------------|~~
~~| 实现方式 | 顺序启动两个独立的 `CameraHelper` | 使用 `AVCaptureMultiCamSession` 硬件级双摄 |~~

**修复结果:**
- 前置摄像头：主预览区域（全屏）
- 后置摄像头：画中画叠加层（右上角 120×68dp）
- 预览比例：约16:9（220dp高度）

**注意:** Android不支持硬件级双摄同步，当前实现为两个独立CameraHelper，时间戳可能有微小偏差

---

### 2.2 录制时长控制 - 完全缺失

| 项目 | Sample (当前) | FacialCollection (目标) |
|------|--------------|------------------------|
| 时长配置 | 无 | 1-7200秒可配置 |
| 自动停止 | 无 | 达到设定时长自动停止 |
| 剩余时间显示 | 无 | 实时显示剩余时间 |

**FacialCollection 实现** (`DataCollectionCoordinator.swift` 第380-400行):
```swift
// 设置项
@AppStorage("recording_duration") var maxRecordingDuration: Int = 30

// 定时器检查
private func startDurationTimer() {
    durationTimer = Timer.scheduledTimer(withTimeInterval: 0.1, repeats: true) { _ in
        let elapsed = Date().timeIntervalSince(recordingStartTime)
        if elapsed >= Double(maxRecordingDuration) {
            self.stopRecording()
            self.updateStatus("✅ 录制完成（\(maxRecordingDuration)秒）")
        }
    }
}
```

**需要添加到 Sample:**
1. `SettingsActivity` 添加录制时长配置 (Slider 1-7200秒)
2. `RecordingCoordinator` 添加定时器和自动停止逻辑
3. UI 显示剩余时间

---

### 2.3 录制协调 - 指环/ECG未纳入一键录制

| 项目 | Sample (当前) | FacialCollection (目标) |
|------|--------------|------------------------|
| 一键录制包含 | 摄像头、IMU、音频、血氧仪 | 摄像头、IMU、指环、ECG |
| 指环同步 | ❌ 未纳入 | ✅ `ringController.beginSynchronizedMeasurement()` |
| ECG同步 | ❌ 未纳入 | ✅ `ecgController.beginSynchronizedMeasurement()` |

**Sample 当前实现** (`RecordingCoordinator.java` 第30-50行):
```java
public void start(String experimentId) {
    SessionManager.getInstance().ensureSession(context, experimentId);
    TimeSync.startSessionClock();

    if (enableIMU) imuRecorder.startRecording();
    if (enableAudio) audioRecorder.startRecording();
    if (enableSpO2 && oximeterManager.isConnected())
        oximeterManager.startRecording("unused");
    if (enableECG && VivaLink.isDeviceConnected())
        VivaLink.startSampling();

    // ⚠️ 缺少指环同步测量！
    // if (enableRing && ringConnected)
    //     ringController.beginSynchronizedMeasurement();

    isRecording = true;
}
```

**FacialCollection 实现** (`DataCollectionCoordinator.swift` 第207-262行):
```swift
func startRecording(selection: CameraSelectionMode) {
    // ...
    // 启动指环同步测量
    ringController.beginSynchronizedMeasurement(
        duration: ringDuration,
        sessionDirectory: sessionDir
    )

    // 启动ECG同步测量
    ecgController.beginSynchronizedMeasurement(
        duration: ringDuration,
        sessionDirectory: sessionDir
    )
}
```

**修复方案:**
1. 在 `RecordingCoordinator` 中添加指环和ECG的同步测量调用
2. 需要实现 `RingController.beginSynchronizedMeasurement()` 方法
3. 需要实现 `EcgController.beginSynchronizedMeasurement()` 方法

---

### 2.4 数据云同步 - 完全缺失(请忽略，本项目不用实现)

| 项目 | Sample (当前) | FacialCollection (目标) |
|------|--------------|------------------------|
| 自动同步 | ❌ 完全没有 | ✅ 后台自动上传 |
| 同步状态管理 | ❌ | ✅ `FileSyncManager` 跟踪已同步文件 |
| 上传端点 | ❌ | `https://www.facephys.com/api/upload/general` |

**FacialCollection 相关文件:**
- `Storage/AutoSyncManager.swift` - 自动同步管理
- `Utils/SFTPUploader.swift` - 文件上传
- `Storage/FileSyncManager.swift` - 同步状态跟踪

**功能说明:**
```swift
// 启用自动同步
AutoSyncManager.shared.setAutoSyncEnabled(true)

// 录制完成后添加到队列
func addFileToSyncQueue(fileURL: URL, experimentId: String, sessionId: String)

// 定时检查并上传（每50秒或应用激活时）
Timer.scheduledTimer(withTimeInterval: 50, repeats: true) { ... }
```

**是否需要实现:** 根据项目需求决定，可作为后续功能。

---

### 2.5 云端心率API - 缺失（请忽略，本项目不用实现）

| 项目 | Sample (当前) | FacialCollection (目标) |
|------|--------------|------------------------|
| 本地推理 | ✅ ONNX模型 | ✅ 支持 |
| 云端分析 | ❌ 无 | ✅ `HeartRateAPIClient` |
| API端点 | - | `https://www.facephys.com/api/video` |

**FacialCollection 实现** (`API/HeartRateAPIClient.swift`):
```swift
// API配置
private let baseURL = "https://www.facephys.com/api/video"
keyID = "CG3g8lFu64pB403_Pgqtrw"
secretKey = "DEsWe6UceRjJOkh1kry64e1JlFSZY6fMb9ygDPMnQWI"

// 分析流程
func analyzeVideo(videoURL: URL) -> AnyPublisher<HeartRateAnalysisResult, Error> {
    // 1. 压缩视频 (640x480, 500kbps)
    // 2. 生成HMAC-SHA256签名
    // 3. 上传分析
    // 4. 返回心率结果
}
```

**是否需要实现:** 可选功能，本地ONNX推理已满足基本需求。

---

### 2.6 时间戳标记 - 实现可能不完整

| 项目 | Sample (当前) | FacialCollection (目标) |
|------|--------------|------------------------|
| UI入口 | ✅ `btn_Timestamp` 按钮 | ✅ 录制界面标记按钮 |
| 数据记录 | ⚠️ 未确认 | ✅ `timestamps.csv` |
| 标记内容 | 未确认 | timestamp, marker_id, label, description |

**FacialCollection 实现** (`Core/TimestampRecorder.swift`):
```swift
func recordTimestamp(label: String, description: String = "") {
    let timestamp = Date().timeIntervalSince1970 - startTime
    let markerId = UUID().uuidString.prefix(8)
    let line = "\(timestamp),\(markerId),\(label),\(description)\n"
    logger.writeLine(line)
}
```

**需要确认 Sample 的 `TimestampFragment` 是否正确记录到文件。**

---

## 三、设备连接管理差异

### 3.1 指环蓝牙连接

| 功能 | Sample (当前) | FacialCollection (目标) |
|------|--------------|------------------------|
| 连接状态机 | 基础连接 | 11级状态机 |
| Keep-Alive | ❌ 无 | ✅ 30秒心跳 |
| 自动重连 | ❌ 无 | ✅ 10分钟内999次尝试 |
| 断开处理 | 基础 | 区分手动断开/异常断开 |

**FacialCollection 11级连接状态** (`Ring/Models/RingDeviceModels.swift`):
```swift
enum ConnectionStatus {
    case gattConnecting        // 1: GATT连接中
    case gattConnected         // 2: GATT已连接
    case serviceConnecting     // 3: 发现服务中
    case serviceConnected      // 4: 服务已发现
    case writeConnecting       // 5: 设置Write特征
    case respondConnecting     // 6: 设置Notify特征
    case connected             // 7: 完全连接
    case serviceDisconnected   // 8: 服务丢失
    case writeDisconnected     // 9: Write失败
    case respondDisconnected   // 10: Notify失败
    case disconnected          // 11: 断开连接
    case scanning              // 扫描中
}
```

**FacialCollection Keep-Alive** (`Ring/Services/BLEManager.swift`):
```swift
// 每30秒发送心跳保活
keepAliveTimer = Timer.scheduledTimer(withTimeInterval: 30.0, repeats: true) { _ in
    self.sendKeepAlive()
}
```

**FacialCollection 自动重连**:
```swift
BCLRingManager.shared.startConnect(
    peripheral: peripheral,
    isAutoReconnect: true,
    autoReconnectTimeLimit: 600,  // 10分钟
    autoReconnectMaxAttempts: 999
)
```

---

### 3.2 ECG设备连接

| 功能 | Sample (当前) | FacialCollection (目标) |
|------|--------------|------------------------|
| 数据模式 | 默认模式 | Live/Dual/FullDual/RTS 可切换 |
| 自动重连 | ❌ 无 | ✅ APP启动后60秒内自动重连 |
| 推荐模式 | - | FullDual Mode (研究用) |

**FacialCollection 数据模式切换** (`ECG/ViewModels/ECGMeasurementController.swift`):
```swift
func switchDataMode(_ mode: ECGDataMode) {
    switch mode {
    case .live: VVConfigManager.switch(toLiveMode: deviceName)
    case .dual: VVConfigManager.switch(toDualMode: deviceName)
    case .fullDual: VVConfigManager.switch(toFullDualMode: deviceName)  // 推荐
    case .rts: VVConfigManager.switch(toRTSMode: deviceName)
    }
}
```

**FacialCollection 自动重连**:
```swift
bleManager.bleReconnectEnabled = true
bleManager.startAutomaticReconnect(afterAppLaunch: 60) { ... }
```

---

### 3.3 血氧仪USB连接 (Sample独有)

Sample 有完整的 USB 血氧仪实现，这是 FacialCollection 所没有的：

**相关文件:**
- `device/OximeterManager.java` - USB连接和数据解析
- `device/OximeterService.java` - 后台服务
- `OximeterViewHolder.java` - UI展示

**数据协议:**
```java
// BVP数据: 0xEB 0x00 [X] [BVP]
// HR/SpO2数据: 0xEB 0x01 0x05 [HR] [SpO2]
```

---

## 四、数据存储结构差异

### 4.1 目录结构对比

**Sample 当前结构:**
```
Movies/FacialCollection/{experimentId}/Session_{wallMs}/
├── front/
│   ├── front_camera_{timestamp}.mp4
│   └── frame_metadata_front_{timestamp}.csv
├── back/
│   ├── back_camera_{timestamp}.mp4
│   └── frame_metadata_back_{timestamp}.csv
├── imu/
│   ├── imu_accelerometer_data.csv
│   └── imu_gyroscope_data.csv
├── audio/
│   ├── mic1_audio_record.pcm
│   ├── mic2_audio_record.pcm
│   ├── mic1_timestamp.txt
│   └── mic2_timestamp.txt
├── ring/
│   ├── ring_{timestamp}.csv
│   └── ring_data_{timestamp}.csv
├── ecg/
├── spo2/
│   └── spo2_{timestamp}.csv
├── inference/
└── markers/
```

**FacialCollection 结构:**
```
Documents/FacialCollection/{experimentId}/
├── experiment_info.json              ← Sample 缺少
└── Session_{timestamp}/
    ├── FrontCamera/                  ← 大小写不同 (front vs FrontCamera)
    │   ├── frontcamera_{ts}.mp4
    │   ├── compressed_*.mp4          ← Sample 缺少压缩视频
    │   └── frame_metadata_{ts}.csv
    ├── BackCamera/
    │   ├── backcamera_{ts}.mp4
    │   └── frame_metadata_{ts}.csv
    ├── IMU/
    │   └── imu_data.csv              ← 单文件 vs Sample的双文件
    ├── RingLog/                      ← 目录名不同 (ring vs RingLog)
    │   └── MainSession_{ts}.txt
    ├── ECGLog/                       ← 目录名不同 (ecg vs ECGLog)
    │   └── ecg_{ts}.csv
    ├── PatientInfo/                  ← Sample 缺少
    └── timestamps.csv                ← Sample 可能在 markers/ 目录
```

### 4.2 CSV格式对比

**IMU数据:**
| Sample | FacialCollection |
|--------|------------------|
| 分为两个文件: `imu_accelerometer_data.csv` 和 `imu_gyroscope_data.csv` | 单文件: `imu_data.csv` |
| Header: `sensor_ns,wall_ms,relative_s,accel_x,accel_y,accel_z` | Header: `timestamp,accelerometerX,accelerometerY,accelerometerZ,gyroscopeX,gyroscopeY,gyroscopeZ` |

**帧元数据:**
| Sample | FacialCollection |
|--------|------------------|
| Header: `frame_number,sensor_ns,wall_ms` | Header: `timestamp,sensortimestamp,systemtimestamp,frameNumber` |

**建议:** 统一为 FacialCollection 的格式，便于后续数据处理。

---

## 五、参数配置对比

### 5.1 摄像头参数

| 参数 | Sample | FacialCollection | 是否一致 |
|------|--------|------------------|----------|
| 分辨率 | 1920x1080 | 1920x1080 | ✅ |
| 帧率 | 30fps | 30fps | ✅ |
| 比特率 | 10Mbps | 10Mbps | ✅ |
| 编码格式 | H.264 | H.264 High Profile | ⚠️ Profile可能不同 |
| 像素格式 | - | kCVPixelFormatType_32BGRA | - |

### 5.2 IMU参数

| 参数 | Sample | FacialCollection | 是否一致 |
|------|--------|------------------|----------|
| 采样率 | 200Hz | 200Hz | ✅ |
| 加速度计单位 | m/s² | m/s² (含重力) | ✅ |
| 陀螺仪单位 | rad/s | rad/s | ✅ |

### 5.3 音频参数

| 参数 | Sample | FacialCollection | 是否一致 |
|------|--------|------------------|----------|
| 采样率 | 44100Hz | 44100Hz | ✅ |
| 通道数 | 2 (双麦克风) | 2 (立体声) | ⚠️ 含义不同 |
| 位深 | 16bit | 16bit | ✅ |
| 格式 | PCM | PCM | ✅ |

### 5.4 设置项对比

| 设置项 | Sample | FacialCollection |
|--------|--------|------------------|
| 实验ID | ✅ `experiment_id` | ✅ `experiment_id` |
| 录制时长 | ❌ 缺少 | ✅ `recording_duration` (1-7200秒) |
| AI推理开关 | ✅ `enable_inference` | ✅ `enable_inference` |
| 自动同步 | ❌ 缺少 | ✅ `auto_sync_enabled` |
| 闪光灯 | ✅ `flashlight` | ✅ 手电筒控制 |
| 戒指MAC | ✅ `mac_address` | ✅ `ring_mac_address` |

---

## 六、已知Bug/问题

### 6.1 血氧仪VID/PID硬编码

**文件:** `device/OximeterManager.java`

**问题:**
```java
private static final int VENDOR_ID = 0x1234;   // 占位符
private static final int PRODUCT_ID = 0x5678;  // 占位符
```

**影响:** 当前代码未验证USB设备的VID/PID，可能连接到错误设备。

**修复:** 需要替换为实际血氧仪的VID/PID。

---

### 6.2 摄像头延迟硬编码

**文件:** `activity/ListActivity.java` 第377行

**问题:**
```java
new Handler(Looper.getMainLooper()).postDelayed(() -> {
    frontCameraHelper.startFrontRecording();
}, 1000);  // 固定1秒延迟
```

**影响:** 硬编码延迟可能不适用于所有设备，部分设备可能需要更长时间初始化。

**修复:** 应使用 `SurfaceHolder.Callback.surfaceCreated()` 回调来确定Surface就绪时机。

---

### 6.3 戒指文件下载去重问题

**文件:** `utils/NotificationHandler.java`

**问题:**
```java
private static final Set<String> processedFileKeys = new HashSet<>();
// 静态Set在应用生命周期内不清空
```

**影响:** 重启应用后无法重新下载已处理过的文件。

**修复:** 应将处理记录持久化到SharedPreferences，并提供清除选项。

---

### 6.4 ECG单设备限制

**文件:** `utils/VivaLink.java`

**问题:**
```java
private static Device MainDevice;  // 静态单例
```

**影响:** 只支持连接一个ECG设备，不支持多设备场景。

**修复:** 如需支持多设备，需改为设备列表管理。

---

### 6.5 TimeSync未初始化警告

**文件:** `core/TimeSync.java`

**问题:**
```java
private static void ensure() {
    if (!initialized) {
        Log.w(TAG, "TimeSync not initialized, auto-starting session clock");
        startSessionClock();  // 自动初始化
    }
}
```

**影响:** 如果各模块在 `startAllRecording()` 之前就开始记录数据，时间基准可能不一致。

**修复:** 确保在任何数据记录之前显式调用 `TimeSync.startSessionClock()`。

---

### 6.6 音频双麦克风假设

**文件:** `media/MultiMicAudioRecorderHelper.java`

**问题:**
```java
audioRecord2 = new AudioRecord(
    MediaRecorder.AudioSource.VOICE_COMMUNICATION, ...);
```

**影响:** 并非所有Android设备都支持 `VOICE_COMMUNICATION` 音频源，可能导致初始化失败。

**修复:** 添加音频源可用性检查，失败时降级使用单麦克风。

---

## 七、修复优先级建议

### 高优先级 (影响核心功能)

| 序号 | 问题 | 原因 |
|------|------|------|
| 1 | UI差异（标签文字、启动时机、预览位置） | 用户体验一致性 |
| 2 | 录制协调（指环/ECG同步） | 数据完整性 |
| 3 | 血氧仪VID/PID硬编码 | 可能无法连接设备 |
| 4 | 摄像头延迟硬编码 | 部分设备可能启动失败 |

### 中优先级 (功能完善)

| 序号 | 问题 | 原因 |
|------|------|------|
| 5 | 录制时长控制 | 自动停止避免录制过长 |
| 6 | 设备自动重连 | 提高稳定性 |
| 7 | 时间戳标记完善 | 数据标注需求 |
| 8 | 存储结构统一 | 数据处理一致性 |

### 低优先级 (可选功能)

| 序号 | 问题 | 原因 |
|------|------|------|
| 9 | 数据云同步 | 可手动导出替代 |
| 10 | 云端心率API | 本地推理已满足需求 |
| 11 | 双摄真正同开 | 需要设备支持 |

---

## 附录：关键文件路径

### Sample 项目

| 功能 | 文件路径 |
|------|----------|
| 主界面 | `app/src/main/java/com/tsinghua/sample/activity/ListActivity.java` |
| 设备适配器 | `app/src/main/java/com/tsinghua/sample/DeviceAdapter.java` |
| 摄像头ViewHolder | `app/src/main/java/com/tsinghua/sample/CameraViewHolder.java` |
| 录制协调器 | `app/src/main/java/com/tsinghua/sample/core/RecordingCoordinator.java` |
| 时间同步 | `app/src/main/java/com/tsinghua/sample/core/TimeSync.java` |
| 会话管理 | `app/src/main/java/com/tsinghua/sample/core/SessionManager.java` |
| 常量定义 | `app/src/main/java/com/tsinghua/sample/core/Constants.java` |
| 血氧仪管理 | `app/src/main/java/com/tsinghua/sample/device/OximeterManager.java` |
| 摄像头布局 | `app/src/main/res/layout/item_camera.xml` |
| 主界面布局 | `app/src/main/res/layout/activity_list.xml` |

### FacialCollection 项目

| 功能 | 文件路径 |
|------|----------|
| 主界面 | `/Users/Zhuanz/FacialCollection/FacialCollection/Views/MainListView_New.swift` |
| 数据协调器 | `/Users/Zhuanz/FacialCollection/FacialCollection/Core/DataCollectionCoordinator.swift` |
| 摄像头管理 | `/Users/Zhuanz/FacialCollection/FacialCollection/Camera/CameraManager_Single.swift` |
| 双摄管理 | `/Users/Zhuanz/FacialCollection/FacialCollection/Camera/DualCameraManager.swift` |
| 指环控制器 | `/Users/Zhuanz/FacialCollection/FacialCollection/Ring/ViewModels/RingMeasurementController.swift` |
| ECG控制器 | `/Users/Zhuanz/FacialCollection/FacialCollection/ECG/ViewModels/ECGMeasurementController.swift` |
| 心率API | `/Users/Zhuanz/FacialCollection/FacialCollection/API/HeartRateAPIClient.swift` |
| 设置界面 | `/Users/Zhuanz/FacialCollection/FacialCollection/Views/SettingsView_New.swift` |
