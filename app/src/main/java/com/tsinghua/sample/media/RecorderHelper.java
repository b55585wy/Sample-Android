package com.tsinghua.sample.media;

import static android.content.Context.MODE_PRIVATE;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.hardware.camera2.*;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.view.Surface;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.google.mediapipe.solutions.facemesh.FaceMesh;
import com.google.mediapipe.solutions.facemesh.FaceMeshOptions;
import com.tsinghua.sample.utils.FacePreprocessor;
import com.tsinghua.sample.utils.HeartRateEstimator;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class RecorderHelper {
    private static final String TAG = "RecorderHelper";
    private final CameraHelper cameraHelper;
    private final Context context;

    private MediaRecorder mediaRecorderFront, mediaRecorderBack;
    public String startTimestamp;
    public String stopTimestamp;
    public File outputDirectory;
    public File newOutputDirectory;


    private List<String> frameDataFront = new ArrayList<>();
    private List<String> frameDataBack = new ArrayList<>();

    public boolean isFlashlightOn = true;
    public RecorderHelper(CameraHelper cameraHelper, Context context) {
        this.cameraHelper = cameraHelper;
        this.context = context;
        SharedPreferences sharedPreferences = context.getSharedPreferences("SettingsPrefs", MODE_PRIVATE);
        isFlashlightOn = sharedPreferences.getBoolean("flashlight", false);
    }

    private String generateTimestamp() {
        return String.valueOf(System.currentTimeMillis());
    }
    private void prepareDirectories() {
        SharedPreferences prefs = context.getSharedPreferences("AppSettings", MODE_PRIVATE);
        String experimentId = prefs.getString("experiment_id", "default");
        String dirPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
                + "/Sample/" + experimentId + "/";
        File dir = new File(dirPath);
        if (!dir.exists()) dir.mkdirs();

        if (outputDirectory == null) {
            outputDirectory = new File(dir, "Sample_" + startTimestamp);
            if (!outputDirectory.exists()) outputDirectory.mkdirs();
        }
    }
    public void setupFrontRecording() {
        startTimestamp  = null;
        outputDirectory = null;
        frameDataFront.clear();
        startTimestamp = generateTimestamp();
        prepareDirectories();

        File frontOutputFile = new File(outputDirectory, "front_camera_" + startTimestamp + ".mp4");
        setupMediaRecorder(frontOutputFile.getAbsolutePath(), true, 270);

        Surface frontSurface = cameraHelper.getSurfaceViewFront().getHolder().getSurface();

        try {
            cameraHelper.getCameraDeviceFront().createCaptureSession(
                    Arrays.asList(frontSurface, mediaRecorderFront.getSurface()),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            cameraHelper.setCaptureSessionFront(session);
                            try {
                                CaptureRequest.Builder builder = cameraHelper.getCameraDeviceFront()
                                        .createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
                                builder.addTarget(frontSurface);
                                builder.addTarget(mediaRecorderFront.getSurface());

                                session.setRepeatingRequest(builder.build(), new CameraCaptureSession.CaptureCallback() {
                                    @Override
                                    public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                                                   @NonNull CaptureRequest request,
                                                                   @NonNull TotalCaptureResult result) {
                                        long sensortimestamp = result.get(CaptureResult.SENSOR_TIMESTAMP);
                                        long timestamp = System.currentTimeMillis();  // 获取当前时间戳

                                        long frameNumber = result.getFrameNumber();
                                        frameDataFront.add(String.format(Locale.getDefault(), "%d,%d,%d", timestamp,sensortimestamp, frameNumber));
                                    }
                                }, new Handler());
                                mediaRecorderFront.start();
                            } catch (CameraAccessException e) {
                                Log.e(TAG, "Front recording error", e);
                            }
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            Toast.makeText(context, "前摄配置失败", Toast.LENGTH_SHORT).show();
                        }
                    }, null);
        } catch (CameraAccessException e) {
            Log.e(TAG, "Front camera session error", e);
        }
    }

    public void setupBackRecording() {
        startTimestamp  = null;
        outputDirectory = null;
        frameDataBack.clear();
        if (startTimestamp == null) startTimestamp = generateTimestamp();
        prepareDirectories();

        File backOutputFile = new File(outputDirectory, "back_camera_" + startTimestamp + ".mp4");
        setupMediaRecorder(backOutputFile.getAbsolutePath(), false, 90);

        Surface backSurface = cameraHelper.getSurfaceViewBack().getHolder().getSurface();

        try {
            cameraHelper.getCameraDeviceBack().createCaptureSession(
                    Arrays.asList(backSurface, mediaRecorderBack.getSurface()),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            cameraHelper.setCaptureSessionBack(session);
                            try {
                                CaptureRequest.Builder builder = cameraHelper.getCameraDeviceBack()
                                        .createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
                                builder.addTarget(backSurface);
                                builder.addTarget(mediaRecorderBack.getSurface());
                                builder.set(CaptureRequest.FLASH_MODE, isFlashlightOn ?
                                        CaptureRequest.FLASH_MODE_TORCH : CaptureRequest.FLASH_MODE_OFF);

                                session.setRepeatingRequest(builder.build(), new CameraCaptureSession.CaptureCallback() {
                                    @Override
                                    public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                                                   @NonNull CaptureRequest request,
                                                                   @NonNull TotalCaptureResult result) {
                                        long sensortimestamp = result.get(CaptureResult.SENSOR_TIMESTAMP);
                                        long timestamp = System.currentTimeMillis();;  // 获取当前时间戳
                                        long frameNumber = result.getFrameNumber();
                                        frameDataBack.add(String.format(Locale.getDefault(), "%d,%d,%d", timestamp,sensortimestamp, frameNumber));
                                    }
                                }, new Handler());
                                mediaRecorderBack.start();
                            } catch (CameraAccessException e) {
                                Log.e(TAG, "Back recording error", e);
                            }
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            Toast.makeText(context, "后摄配置失败", Toast.LENGTH_SHORT).show();
                        }
                    }, null);
        } catch (CameraAccessException e) {
            Log.e(TAG, "Back camera session error", e);
        }
    }
    private void setupMediaRecorder(String outputPath, boolean isFront, int rotate) {
        MediaRecorder recorder = new MediaRecorder();
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        AudioDeviceInfo[] devices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS);
        for (AudioDeviceInfo d : devices) {
            if (d.getType() == AudioDeviceInfo.TYPE_BUILTIN_MIC) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    recorder.setPreferredDevice(d);
                }
                break;
            }
        }
        recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        recorder.setOutputFile(outputPath);
        recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        recorder.setVideoSize(1920, 1080);
        recorder.setVideoFrameRate(30);
        recorder.setVideoEncodingBitRate(10000000);
        recorder.setOrientationHint(rotate);

        try {
            recorder.prepare();
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (isFront) mediaRecorderFront = recorder;
        else mediaRecorderBack = recorder;
    }

    public void stopFrontRecording() {
        try {
            if (mediaRecorderFront != null) {
                cameraHelper.getCaptureSessionFront().stopRepeating();
                mediaRecorderFront.stop();
                mediaRecorderFront.release();
                mediaRecorderFront=null;

            }
        } catch (Exception e) {
            Log.e(TAG, "停止前摄录像失败", e);
        }

        stopTimestamp = generateTimestamp();
        renameDirectoryAndSave("front_camera_data_" + startTimestamp + ".txt", frameDataFront);
    }

    public void stopBackRecording() {
        try {
            if (mediaRecorderBack != null) {
                cameraHelper.getCaptureSessionBack().stopRepeating();
                mediaRecorderBack.stop();
                mediaRecorderBack.release();
                mediaRecorderBack=null;

            }
        } catch (Exception e) {
            Log.e(TAG, "停止后摄录像失败", e);
        }

        stopTimestamp = generateTimestamp();
        renameDirectoryAndSave("back_camera_data_" + startTimestamp + ".txt", frameDataBack);
    }
    private void renameDirectoryAndSave(String filename, List<String> data) {
        SharedPreferences prefs = context.getSharedPreferences("AppSettings", MODE_PRIVATE);
        String experimentId = prefs.getString("experiment_id", "default");
        String dirPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
                + "/Sample/" + experimentId + "/";
        File dir = new File(dirPath);
        if (!dir.exists()) dir.mkdirs();

        newOutputDirectory = new File(dir, "Sample_" + startTimestamp + "_" + stopTimestamp);
        outputDirectory.renameTo(newOutputDirectory);

        saveDataToFile(newOutputDirectory, filename, data);
    }
    private void saveDataToFile(File directory, String filename, List<String> data) {
        File file = new File(directory, filename);
        try (FileOutputStream fos = new FileOutputStream(file);
             OutputStreamWriter writer = new OutputStreamWriter(fos)) {
            for (String line : data) {
                writer.write(line + "\n");
            }
            writer.write("total: " + data.size() + "\n");
        } catch (IOException e) {
            Log.e(TAG, "写入帧数据失败: " + filename, e);
        }
    }
    public static Bitmap convertJPEGToBitmap(Image image) {
        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
    }
    private Bitmap rotateBitmap(Bitmap bitmap, int rotationDegrees) {
        Matrix matrix = new Matrix();
        matrix.postRotate(rotationDegrees);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }

}
