package com.tsinghua.sample.utils;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.ImageView;


import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;

public class HeartRateEstimator {

    private final OrtEnvironment env;
    private final OrtSession signalSession;
    private final OrtSession welchSession;
    private final OrtSession hrSession;
    // 保存上一帧时间戳，类似 JS 中的 lastTimestamp
    private Long lastTimestamp = null;
    private PlotView plotView;


    // 保存隐藏状态的 Map，键为 state tensor 的名字，值为 OnnxTensor
    private final Map<String, OnnxTensor> state = new HashMap<>();
    private final Map<String, OnnxTensor> signalFeeds = new HashMap<>();
    private final Map<String, OnnxTensor> welchFeeds = new HashMap<>();
    private final Map<String, OnnxTensor> hrFeeds = new HashMap<>();
    //private WebSocketManager webSocketManager;
    private final List<Float> signalOutput = new ArrayList<>();
    private final Deque<Long> timeStamps = new ArrayDeque<>(); // 存最近 300 帧的时间戳(毫秒)
    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private KalmanFilter1D kfOutput;
    private KalmanFilter1D kfHR;
    private int welchCount;
    private ImageView imageView;

    private final FloatBuffer dtBuffer = FloatBuffer.allocate(1);
    private OnnxTensor dtTensor = null;

    private final float[] signalArray300 = new float[300];

    // 用于统计 FPS 的时间队列
    private final Deque<Long> frameTimes = new ArrayDeque<>();
    // HeartRateEstimator 类中
    private OrtSession.Result lastResult = null; // 缓存上一帧的 result（避免被自动 close）

    private final FloatBuffer frameBuffer = ByteBuffer
            .allocateDirect(36 * 36 * 3 * 4)  // = 36*36*3 floats, each float 4 bytes
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer();

    private String Id;
    private final long[] frameShape = {1, 1, 36, 36, 3};
    private final BufferedWriter csvWriter;

    // 构造函数
    public HeartRateEstimator(InputStream modelStream,
                              InputStream stateJsonStream,
                              InputStream welchModelStream,
                              InputStream hrModelStream,
                              PlotView plotView,
                              String outDir

    ) throws Exception {
        env = OrtEnvironment.getEnvironment();
        OrtSession.SessionOptions options = new OrtSession.SessionOptions();
        options.setIntraOpNumThreads(1);
        options.setExecutionMode(OrtSession.SessionOptions.ExecutionMode.SEQUENTIAL);
        options.setMemoryPatternOptimization(true);
        options.setCPUArenaAllocator(true);
        options.addConfigEntry("session.use_env_allocators", "1");
        this.plotView = plotView;
        File d = new File(outDir);
        if (!d.exists()) d.mkdirs();
        File csv = new File(d, "hr_log.csv");
        boolean isNew = !csv.exists();
        csvWriter = new BufferedWriter(new FileWriter(csv, true));
        if (isNew) {
            // 写入表头
            csvWriter.write("timestamp,output,hr\n");
            csvWriter.flush();
        }
        this.imageView = imageView;
        //this.webSocketManager = webSocketManager;
        signalSession = env.createSession(readAllBytesCompat(modelStream), options);
        welchSession = env.createSession(readAllBytesCompat(welchModelStream), options);
        hrSession = env.createSession(readAllBytesCompat(hrModelStream), options);
        loadInitialState(stateJsonStream);

        // 填充初始信号值
        for (int i = 0; i < 300; i++) {
            signalOutput.add(0f);
        }
        welchCount = 300 - 240; // 初始化
    }

    public Float estimateFromFrame(float[][][] frame,Long nowMs) throws Exception {
        if (!isRunning.compareAndSet(false, true)) {
            return null;
        }
        float output;      // instant signal output
        Float hrResult = null;  // 心率结果，如果没算出来就保持 null
        try {

            timeStamps.addLast(nowMs);
            if (timeStamps.size() > 300) {
                timeStamps.removeFirst();
            }
            float dtSeconds = 1f / 30f;
            if (timeStamps.size() > 1) {
                Long[] tsArray = timeStamps.toArray(new Long[0]);
                long prevMs = tsArray[tsArray.length - 2];
                dtSeconds = Math.max((nowMs - prevMs) / 1000f, 1f / 90f);
            }

            frameBuffer.clear(); // 位置=0, limit=capacity
            for (int y = 0; y < 36; y++) {
                for (int x = 0; x < 36; x++) {
                    float[] pixel = frame[y][x];
                    frameBuffer.put(pixel[0]);
                    frameBuffer.put(pixel[1]);
                    frameBuffer.put(pixel[2]);
                }
            }
            frameBuffer.flip(); // 位置=0, limit=写入数据量

            dtBuffer.put(0, dtSeconds);
            dtBuffer.rewind();
            if (dtTensor != null) {
                dtTensor.close();
            }
            dtTensor = OnnxTensor.createTensor(env, dtBuffer, new long[]{});
            signalFeeds.clear();

            OnnxTensor inputTensor = OnnxTensor.createTensor(env, frameBuffer, frameShape);
            signalFeeds.put("arg_0.1", inputTensor);
            signalFeeds.put("onnx::Mul_37", dtTensor);
            signalFeeds.putAll(state);

            float resultValue = 0f;
            OrtSession.Result result = signalSession.run(signalFeeds);
            float[][] outputArr = (float[][]) result.get(0).getValue();
            output = outputArr[0][0];
            List<String> inputNames = new ArrayList<>(signalSession.getInputNames());
            for (int i = 1; i < result.size(); i++) {
                String key = inputNames.get(i);
                state.put(key, (OnnxTensor) result.get(i));
            }

            if (kfOutput == null) {
                kfOutput = new KalmanFilter1D(1f, 0.5f, output, 1f);
            } else {
                output = kfOutput.update(output);
            }
            signalOutput.add(output);
            plotView.addValue(output);

            if (signalOutput.size() > 300) {
                signalOutput.remove(0);
            }


            welchCount++;
            isRunning.set(false);
            if (signalOutput.size() == 300 && welchCount >= 300) {
                welchCount = 270;
                hrResult = estimateHRFromSignal(signalOutput);
            }

        } finally {
            isRunning.set(false);

        }
        try {
            StringBuilder sb = new StringBuilder()
                    .append(nowMs)
                    .append(',')
                    .append(output)
                    .append(',');
            if (hrResult != null) {
                sb.append(hrResult);
            }
            sb.append('\n');
            csvWriter.write(sb.toString());
            csvWriter.flush();
        } catch (IOException e) {
            Log.e("TAG", "写入 CSV 失败", e);
        }
        // ——————————————————————————————

        return hrResult;
    }


    private float estimateHRFromSignal(List<Float> signal) throws Exception {
        final long t1 = System.nanoTime();

        // 复制信号到复用数组
        final int size = signal.size();
        for (int i = 0; i < size; i++) {
            signalArray300[i] = signal.get(i);
        }

        // 做 Welch
        try (OnnxTensor signalTensor = OnnxTensor.createTensor(
                env,
                FloatBuffer.wrap(signalArray300, 0, size),
                new long[]{1, 1, size})
        ) {
            welchFeeds.clear();
            welchFeeds.put("input", signalTensor);

            try (OrtSession.Result psdResult = welchSession.run(welchFeeds)) {
                float[] freqs = (float[]) ((OnnxTensor) psdResult.get("freqs").orElseThrow()).getValue();
                float[] psd   = ((float[][]) ((OnnxTensor) psdResult.get("psd").orElseThrow()).getValue())[0];

                // 调 HR session
                try (OnnxTensor freqsTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(freqs), new long[]{freqs.length});
                     OnnxTensor psdTensor   = OnnxTensor.createTensor(env, FloatBuffer.wrap(psd),   new long[]{1, psd.length})
                ) {
                    hrFeeds.clear();
                    hrFeeds.put("freqs", freqsTensor);
                    hrFeeds.put("psd", psdTensor);

                    try (OrtSession.Result hrResult = hrSession.run(hrFeeds)) {
                        float hr = ((Number) ((OnnxTensor) hrResult.get("hr").orElseThrow()).getValue()).floatValue();

                        // 根据真实时长修正 HR (因为模型默认按30FPS)
                        if (timeStamps.size() >= 300) {
                            long firstMs = timeStamps.getFirst();
                            long lastMs  = timeStamps.getLast();
                            float durationSec = (lastMs - firstMs) / 1000f;
                            if (durationSec > 0f) {
                                float averageFps = 299f / durationSec; // 299 帧间隔
                                hr *= (averageFps / 30f);
                            }
                        }

                        // 卡尔曼滤波
                        if (kfHR == null) {
                            kfHR = new KalmanFilter1D(1f, 2f, hr, 1f);
                        } else {
                            hr = kfHR.update(hr);
                        }
                        final long t2 = System.nanoTime();
                        Log.e("PROFILE", "计算心率耗时(ms): " + (t2 - t1) / 1e6);
                        return hr;
                    }
                }
            }
        }
    }

    // 如果需要调试，可视化当前输入帧图像
    private void showInputPreview(float[][][] image) {
        int width = 36;
        int height = 36;
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                float r = image[y][x][0];
                float g = image[y][x][1];
                float b = image[y][x][2];
                int color = Color.rgb(
                        (int) (r * 255),
                        (int) (g * 255),
                        (int) (b * 255)
                );
                bitmap.setPixel(x, y, color);
            }
        }

        mainHandler.post(() -> imageView.setImageBitmap(bitmap));
    }

    // 加载初始隐藏状态
    private void loadInitialState(InputStream stateJsonStream) throws Exception {
        String json = new String(readAllBytesCompat(stateJsonStream), StandardCharsets.UTF_8);
        ObjectMapper mapper = new ObjectMapper();
        TypeReference<Map<String, Object>> typeRef = new TypeReference<Map<String, Object>>() {};
        Map<String, Object> parsed = mapper.readValue(json, typeRef);

        for (Map.Entry<String, Object> entry : parsed.entrySet()) {
            String name = entry.getKey();
            Object value = entry.getValue();

            float[] flat = flatten(value);
            long[] shape = shapeOf(value);

            OnnxTensor tensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(flat), shape);

            state.put(name, tensor);
        }
    }


    // 读取流到 byte[]
    private static byte[] readAllBytesCompat(InputStream inputStream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[4096];
        int nRead;
        while ((nRead = inputStream.read(data)) != -1) {
            buffer.write(data, 0, nRead);
        }
        return buffer.toByteArray();
    }

    // 记录当前 FPS
    private void logCurrentFPS(long nowMs) {
        frameTimes.addLast(nowMs);
        while (frameTimes.size() > 1 && nowMs - frameTimes.getFirst() > 1000) {
            frameTimes.removeFirst();
        }
        int fps = frameTimes.size(); // 最近1秒内的帧数
        Log.d("HeartRateEstimator", "当前FPS: " + fps);
    }

    // 展平任意层级结构
    private float[] flatten(Object nested) {
        List<Float> flatList = new ArrayList<>();
        flattenRecursive(nested, flatList);
        float[] result = new float[flatList.size()];
        for (int i = 0; i < flatList.size(); i++) {
            result[i] = flatList.get(i);
        }
        return result;
    }

    private void flattenRecursive(Object o, List<Float> output) {
        if (o instanceof Number) {
            output.add(((Number) o).floatValue());
        } else if (o instanceof List<?>) {
            for (Object item : (List<?>) o) {
                flattenRecursive(item, output);
            }
        } else if (o instanceof float[]) {
            for (float f : (float[]) o) {
                output.add(f);
            }
        } else if (o instanceof Object[]) {
            for (Object item : (Object[]) o) {
                flattenRecursive(item, output);
            }
        } else {
            Log.e("FLATTEN", "Unexpected data type: " + o);
        }
    }

    // 递归推断嵌套结构的形状
    private long[] shapeOf(Object nested) {
        List<Long> shape = new ArrayList<>();
        Object current = nested;
        while (current instanceof List<?> && !((List<?>) current).isEmpty()) {
            shape.add((long) ((List<?>) current).size());
            current = ((List<?>) current).get(0);
        }
        return shape.stream().mapToLong(i -> i).toArray();
    }

    // 一维卡尔曼滤波
    public static class KalmanFilter1D {
        private final float processNoise;
        private final float measurementNoise;
        private float estimate;
        private float estimateError;

        public  KalmanFilter1D(float processNoise, float measurementNoise, float initialState, float initialEstimateError) {
            this.processNoise = processNoise;
            this.measurementNoise = measurementNoise;
            this.estimate = initialState;
            this.estimateError = initialEstimateError;
        }

        public float update(float measurement) {
            float prediction = estimate;
            float predictionError = estimateError + processNoise;
            float kalmanGain = predictionError / (predictionError + measurementNoise);
            estimate = prediction + kalmanGain * (measurement - prediction);
            estimateError = (1 - kalmanGain) * predictionError;
            return estimate;
        }
    }
}
