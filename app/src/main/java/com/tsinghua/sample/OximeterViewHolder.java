package com.tsinghua.sample;

import static android.content.Context.MODE_PRIVATE;
import static com.tsinghua.sample.MainActivity.hexStringToByteArray;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.RecyclerView;

import com.lm.sdk.LmAPI;
import com.tsinghua.sample.device.model.OximeterData;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class OximeterViewHolder extends RecyclerView.ViewHolder {
    public TextView deviceName;
    public Button startBtn;
    public ImageButton settingsBtn;
    public View infoLayout;
    public TextView hrText, spo2Text;

    private boolean infoVisible = false;
    private BufferedWriter logWriter;
    private boolean isRecording = false;
    public OximeterViewHolder(View itemView) {
        super(itemView);
        deviceName = itemView.findViewById(R.id.deviceName);
        startBtn = itemView.findViewById(R.id.startBtn);
        settingsBtn = itemView.findViewById(R.id.settingsBtn);
        infoLayout = itemView.findViewById(R.id.infoLayout);
        hrText = itemView.findViewById(R.id.hrText);
        spo2Text = itemView.findViewById(R.id.spo2Text);
    }
    public void toggleInfo() {
        if (infoVisible) {
            infoLayout.animate()
                    .translationY(-100) // 向上滑出
                    .alpha(0)
                    .setDuration(200)
                    .withEndAction(() -> infoLayout.setVisibility(View.GONE))
                    .start();
        } else {
            infoLayout.setVisibility(View.VISIBLE);
            infoLayout.setAlpha(0);
            infoLayout.setTranslationY(-100); // 初始从上方位置
            infoLayout.animate()
                    .translationY(0)   // 向下滑入
                    .alpha(1)
                    .setDuration(200)
                    .start();
        }
        infoVisible = !infoVisible;
    }

    public void bindData(OximeterData data) {
        if (data.hr >= 0)   hrText.setText("HR: " + data.hr + " bpm");
        if (data.spo2 >= 0) spo2Text.setText("SpO₂: " + data.spo2 + " %");
        recordData(data);
    }

    public void startRecord(Context context){
        SharedPreferences prefs = context.getSharedPreferences("AppSettings", MODE_PRIVATE);
        try {
            String experimentId = prefs.getString("experiment_id", "");
            String directoryPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES) + "/Sample/" + experimentId + "/OximeterLog/";
            File directory = new File(directoryPath);
            if (!directory.exists()) {
                if (directory.mkdirs()) {
                    Log.d("FileSave", "Directory created successfully: " + directoryPath);
                } else {
                    Log.e("FileSave", "Failed to create directory: " + directoryPath);
                    return;
                }
            }
            String fileName = "OximeterLog_" + System.currentTimeMillis() + ".csv";
            File logFile = new File(directory, fileName);
            logWriter = new BufferedWriter(new FileWriter(logFile, true));
            isRecording =true;

        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(context, "Failed to start logging", Toast.LENGTH_SHORT).show();
        }

    }
    void stopRecord() {
        try {
            if (logWriter != null) {
                logWriter.close();
            }
            Toast.makeText(itemView.getContext(), "已停止录制", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(itemView.getContext(), "停止录制时出错", Toast.LENGTH_SHORT).show();
        } finally {
            isRecording = false;
            logWriter = null;
        }
    }
    private void recordData(OximeterData data) {
        if (!isRecording || logWriter == null) return;
        try {
            logWriter.write(data.timestamp + ","
                    + data.bvp + ","
                    + data.hr  + ","
                    + data.spo2);
            logWriter.newLine();
            logWriter.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
