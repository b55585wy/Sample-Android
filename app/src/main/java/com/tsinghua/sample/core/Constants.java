package com.tsinghua.sample.core;

/**
 * 应用常量定义
 * 与iOS FacialCollection项目的Constants.swift完全对齐
 * 参考文档: Android_Logic_Specification.md 第5节
 */
public final class Constants {

    private Constants() {} // 防止实例化

    // ============ 帧尺寸参数 ============

    /** 推理输入帧宽度 */
    public static final int FRAME_WIDTH = 36;

    /** 推理输入帧高度 */
    public static final int FRAME_HEIGHT = 36;

    /** 推理输入帧通道数（RGB） */
    public static final int FRAME_CHANNELS = 3;

    /** ONNX输入张量的shape [batch, sequence, height, width, channels] */
    public static final long[] FRAME_SHAPE = {1, 1, 36, 36, 3};

    // ============ 摄像头参数 ============

    /** 摄像头分辨率宽度 */
    public static final int CAMERA_WIDTH = 1920;

    /** 摄像头分辨率高度 */
    public static final int CAMERA_HEIGHT = 1080;

    /** 视频帧率 */
    public static final int VIDEO_FRAME_RATE = 30;

    /** 视频比特率 (10 Mbps) */
    public static final int VIDEO_BIT_RATE = 10_000_000;

    // ============ 缓冲区大小 ============

    /** 信号缓冲区大小（300帧窗口） */
    public static final int SIGNAL_BUFFER_SIZE = 300;

    /** 时间戳缓冲区大小 */
    public static final int TIMESTAMP_BUFFER_SIZE = 300;

    // ============ 时间参数 ============

    /** 默认帧率（用于计算Δt） */
    public static final float DEFAULT_FPS = 30.0f;

    /** 最小帧间隔（秒）- 对应90 FPS */
    public static final float MIN_FRAME_INTERVAL = 1.0f / 90.0f; // 0.0111秒 = 11.11ms

    // ============ Welch推理参数 ============

    /** Welch推理触发阈值 */
    public static final int WELCH_TRIGGER_THRESHOLD = 300;

    /** Welch计数重置值（重置为270，不是0！下次触发需要再等30帧） */
    public static final int WELCH_RESET_VALUE = 270;

    /** Welch初始计数值 */
    public static final int WELCH_INITIAL_COUNT = 60;

    /** 心率修正时使用的帧间隔数（300个时间戳之间有299个间隔） */
    public static final float HEART_RATE_CORRECTION_FRAME_COUNT = 299.0f;

    // ============ 卡尔曼滤波参数 ============

    /** 输出信号卡尔曼滤波 - 过程噪声 */
    public static final float OUTPUT_KALMAN_Q = 1.0f;

    /** 输出信号卡尔曼滤波 - 测量噪声 */
    public static final float OUTPUT_KALMAN_R = 0.5f;

    /** 心率卡尔曼滤波 - 过程噪声 */
    public static final float HEART_RATE_KALMAN_Q = 1.0f;

    /** 心率卡尔曼滤波 - 测量噪声（r=2.0，比输出信号的0.5大，输出更平滑） */
    public static final float HEART_RATE_KALMAN_R = 2.0f;

    /** 卡尔曼滤波初始估计误差 */
    public static final float KALMAN_INITIAL_ERROR = 1.0f;

    // ============ 人脸检测参数 ============

    /** 连续无效帧触发阈值 */
    public static final int MAX_INVALID_FRAMES = 60;

    /** 人脸边界框扩展因子 */
    public static final float FACE_BOX_SCALE_FACTOR = 1.5f;

    /** 人脸中心Y轴偏移系数 */
    public static final float FACE_CENTER_Y_OFFSET = 0.4f;

    // ============ EMA平滑参数 ============

    /** EMA sigmoid函数因子 */
    public static final float EMA_SIGMOID_FACTOR = 20.0f;

    // ============ IMU传感器参数 ============

    /** IMU采样率（Hz） */
    public static final double IMU_SAMPLING_RATE = 200.0;

    /** IMU采样间隔（秒） */
    public static final double IMU_SAMPLING_INTERVAL = 1.0 / 200.0; // 0.005秒

    // ============ 音频参数 ============

    /** 音频采样率 */
    public static final int AUDIO_SAMPLE_RATE = 44100;

    /** 音频通道数（立体声） */
    public static final int AUDIO_CHANNELS = 2;

    /** 音频位深度 */
    public static final int AUDIO_BIT_DEPTH = 16;

    // ============ ONNX Session配置 ============

    /** 推理线程数（必须为1，与iOS一致） */
    public static final int ONNX_INTRA_OP_THREADS = 1;

    /** 执行模式：顺序执行 */
    public static final String ONNX_EXECUTION_MODE = "SEQUENTIAL";

    // ============ 文件路径 ============

    /** 数据根目录名称 */
    public static final String DATA_ROOT_DIRECTORY_NAME = "FacialCollection";

    /** 患者信息目录名称 */
    public static final String PATIENT_INFO_DIRECTORY_NAME = "patient_info";

    /** 推理数据目录前缀 */
    public static final String INFERENCE_DIRECTORY_PREFIX = "Inference_";

    /** 会话目录前缀 */
    public static final String SESSION_DIRECTORY_PREFIX = "Session_";

    // ============ 子目录名称 ============

    /** 前置摄像头目录 */
    public static final String DIR_FRONT = "front";

    /** 后置摄像头目录 */
    public static final String DIR_BACK = "back";

    /** IMU数据目录 */
    public static final String DIR_IMU = "imu";

    /** 音频数据目录 */
    public static final String DIR_AUDIO = "audio";

    /** 戒指数据目录 */
    public static final String DIR_RING = "ring";

    /** 心电数据目录 */
    public static final String DIR_ECG = "ecg";

    /** 血氧仪数据目录 */
    public static final String DIR_SPO2 = "spo2";

    /** 推理数据目录 */
    public static final String DIR_INFERENCE = "inference";

    /** 时间标记目录 */
    public static final String DIR_MARKERS = "markers";

    // ============ 图表显示 ============

    /** 图表显示的最大数据点数 */
    public static final int CHART_MAX_DATA_POINTS = 512;

    // ============ 模型文件名 ============

    /** Signal提取模型文件名 */
    public static final String MODEL_SIGNAL = "model.onnx";

    /** LSTM初始隐藏状态文件名 */
    public static final String MODEL_STATE = "state.json";

    /** Welch功率谱估计模型文件名 */
    public static final String MODEL_WELCH = "welch_psd.onnx";

    /** 心率推理模型文件名 */
    public static final String MODEL_HEART_RATE = "get_hr.onnx";

    // ============ ONNX模型输入输出名称 ============

    // Signal Model
    public static final String ONNX_SIGNAL_INPUT_FRAME = "arg_0.1";
    public static final String ONNX_SIGNAL_INPUT_DELTA_T = "onnx::Mul_37";
    public static final String ONNX_SIGNAL_OUTPUT = "output";

    // Welch Model
    public static final String ONNX_WELCH_INPUT = "input";
    public static final String ONNX_WELCH_OUTPUT_FREQS = "freqs";
    public static final String ONNX_WELCH_OUTPUT_PSD = "psd";

    // HR Model
    public static final String ONNX_HR_INPUT_FREQS = "freqs";
    public static final String ONNX_HR_INPUT_PSD = "psd";
    public static final String ONNX_HR_OUTPUT = "hr";

    // ============ CSV文件表头（与iOS对齐） ============

    /** IMU加速度计CSV表头 */
    public static final String CSV_HEADER_ACCELEROMETER = "sensor_ns,wall_ms,relative_s,accel_x,accel_y,accel_z";

    /** IMU陀螺仪CSV表头 */
    public static final String CSV_HEADER_GYROSCOPE = "sensor_ns,wall_ms,relative_s,gyro_x,gyro_y,gyro_z";

    /** 血氧仪CSV表头 */
    public static final String CSV_HEADER_SPO2 = "wall_ms,hr,spo2,bvp";

    /** 帧元数据CSV表头 */
    public static final String CSV_HEADER_FRAME_METADATA = "frame_number,sensor_ns,wall_ms";

    /** ECG数据CSV表头 */
    public static final String CSV_HEADER_ECG = "wall_ms,hr,rr,ecg_values";

    /** 戒指数据CSV表头 */
    public static final String CSV_HEADER_RING = "wall_ms,green,red,ir,accel_x,accel_y,accel_z,gyro_x,gyro_y,gyro_z,temp0,temp1,temp2";

    /** 音频时间戳CSV表头 */
    public static final String CSV_HEADER_AUDIO = "chunk_index,wall_ms,sample_count";
}
