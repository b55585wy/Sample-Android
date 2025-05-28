package com.tsinghua.sample;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.RecyclerView;

import com.tsinghua.sample.utils.NotificationHandler;
import com.vivalnk.sdk.Callback;
import com.vivalnk.sdk.CommandRequest;
import com.vivalnk.sdk.DataReceiveListener;
import com.vivalnk.sdk.SampleDataReceiveListener;
import com.vivalnk.sdk.VitalClient;
import com.vivalnk.sdk.ble.BluetoothConnectListener;
import com.vivalnk.sdk.command.base.CommandType;
import com.vivalnk.sdk.common.ble.connect.BleConnectOptions;
import com.vivalnk.sdk.device.vv330.VV330Manager;
import com.vivalnk.sdk.model.Device;
import com.vivalnk.sdk.model.Motion;
import com.vivalnk.sdk.model.SampleData;
import com.vivalnk.sdk.utils.GSON;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class EcgViewHolder extends RecyclerView.ViewHolder {

    TextView deviceName;
    ImageButton settingsBtn;
    LinearLayout deviceContainer;  // 展开的子设备区域
    private boolean infoVisible = false; // 控制展开收起状态

    public EcgViewHolder(View itemView) {
        super(itemView);

        deviceName = itemView.findViewById(R.id.deviceName);
        deviceContainer = itemView.findViewById(R.id.deviceContainer);
        settingsBtn = itemView.findViewById(R.id.settingsBtn);
        deviceContainer.setVisibility(View.GONE);

        itemView.setOnClickListener(v -> toggleInfo());
    }

    // 绑定数据
    public void bindData(Context context, List<Device> subDevices) {
        if (subDevices == null) {
            subDevices = new ArrayList<>();  // 空保护
        }
        deviceContainer.removeAllViews();
        for (Device device : subDevices) {
            addSubDeviceView(context, device);
        }
    }

    private void addSubDeviceView(Context context, Device device) {
        View subDeviceView = LayoutInflater.from(context).inflate(R.layout.item_sub_ecg_device, deviceContainer, false);

        TextView macText = subDeviceView.findViewById(R.id.macAddress);
        Button connectBtn = subDeviceView.findViewById(R.id.connectBtn);
        Button eraseBtn = subDeviceView.findViewById(R.id.eraseBtn);
        TextView logView = subDeviceView.findViewById(R.id.tvLog);
        PlotView plotView = subDeviceView.findViewById(R.id.plotView);
        PlotView plotViewX = subDeviceView.findViewById(R.id.plotViewX);
        PlotView plotViewY = subDeviceView.findViewById(R.id.plotViewY);
        PlotView plotViewZ = subDeviceView.findViewById(R.id.plotViewZ);
        Button setUserBtn   = subDeviceView.findViewById(R.id.setUserBtn);   // ★
        Button eraseUserBtn = subDeviceView.findViewById(R.id.eraseUserBtn); // ★
        Button toggleSamplingBtn = subDeviceView.findViewById(R.id.toggleSamplingBtn);  // 新的开始/停止按钮
        EditText etUserInfo = subDeviceView.findViewById(R.id.etUserInfo);   // ★

        macText.setText(device.getName());
        plotView.setPlotColor(Color.parseColor("#03A9F4"));
        plotViewX.setPlotColor(Color.parseColor("#FFFF00"));
        plotViewY.setPlotColor(Color.parseColor("#FF00FF"));
        plotViewZ.setPlotColor(Color.parseColor("#00FFFF"));
        final String[] currentUser = {"default"};     // ★ 当前生效的 userName，默认 "default"

        macText.setText(device.getName());
        toggleSamplingBtn.setEnabled(false);
        eraseBtn.setEnabled(false);
        setUserBtn.setEnabled(false);      // ★
        eraseUserBtn.setEnabled(false);    // ★
        setUserBtn.setOnClickListener(b -> {
            String info = etUserInfo.getText().toString().trim();
            if (TextUtils.isEmpty(info) || info.length() > 15) {
                Toast.makeText(context, "内容为空或超过 15 字节", Toast.LENGTH_SHORT).show();
                return;
            }
            CommandRequest req = new CommandRequest.Builder()
                    .setType(CommandType.setUserInfoToFlash)
                    .setTimeout(3_000)
                    .addParam("info", info)
                    .build();
            VitalClient.getInstance().execute(device, req, new Callback() {
                @Override
                public void onComplete(Map<String, Object> data) {
                    Log.d("ECG", "User onComplete: " + data);
                }

                @Override
                public void onError(int code, String msg) {
                    Log.e("ECG", "User onError: code=" + code + ", msg=" + msg);
                }
            });
            Toast.makeText(context, "写入成功", Toast.LENGTH_SHORT).show();
        });

        eraseUserBtn.setOnClickListener(b -> {
            CommandRequest req = new CommandRequest.Builder()
                    .setType(CommandType.eraseUserInfoFromFlash)         // ★
                    .setTimeout(3_000)
                    .build();
            VitalClient.getInstance().execute(device, req, new Callback() {
                @Override
                public void onComplete(Map<String, Object> data) {
                    Log.d("ECG", "cleanUser onComplete: " + data);
                }

                @Override
                public void onError(int code, String msg) {
                    Log.e("ECG", "cleanUser onError: code=" + code + ", msg=" + msg);
                }
            });
            currentUser[0] = "default";                                  // ★ 还原
        });

        // 初始状态是 "开始采样"
        toggleSamplingBtn.setText("开始采样");
        final boolean[] isRecording = {false};
        final BufferedWriter[] writer = {null};
        // 设置按钮点击事件，进行开始和停止的切换
        toggleSamplingBtn.setOnClickListener(v -> {
            if (!isRecording[0]) {                         // —— 开始采样 ——
                startSampling(device);                     // （可放到 onComplete 里再调也行）
                eraseBtn.setEnabled(false);
                setUserBtn.setEnabled(false);
                eraseUserBtn.setEnabled(false);
                /* 1. 读取用户信息 ----------------------------------------- */
                CommandRequest readReq = new CommandRequest.Builder()
                        .setType(CommandType.readUserInfoFromFlash) // 读取
                        .setTimeout(3_000)
                        .build();

                VitalClient.getInstance().execute(device, readReq, new Callback() {
                    private void afterGetUser(String userInfo) {
                        if (TextUtils.isEmpty(userInfo)) userInfo = "default";   // ★ 失败或空 → default
                        currentUser[0] = userInfo;

                        /* 2. 生成实验 ID（若 AppSettings 内无保存） */
                        SharedPreferences sp = context.getSharedPreferences("AppSettings",
                                Context.MODE_PRIVATE);
                        String expId = sp.getString("experiment_id", "");
                        if (TextUtils.isEmpty(expId)) {
                            expId = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                                    .format(new Date());
                        }

                        /* 3. 创建目录 ------------------------------------------------ */
                        File dir = new File(
                                Environment.getExternalStoragePublicDirectory(
                                        Environment.DIRECTORY_MOVIES),
                                "Sample/" + expId + "/VivaLinkLog/" +
                                        device.getName() + "/" + userInfo);

                        if (!dir.exists() && !dir.mkdirs()) {
                            Toast.makeText(context, "无法创建目录", Toast.LENGTH_SHORT).show();
                            return;             // 目录失败，不再继续
                        }

                        /* 4. 打开日志文件 -------------------------------------------- */
                        String fn = "VivaLink_log_" +
                                new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                                        .format(new Date()) + ".txt";
                        try {
                            writer[0] = new BufferedWriter(new FileWriter(new File(dir, fn)));
                        } catch (IOException e) {
                            Toast.makeText(context, "文件创建失败", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        /* 5. 更新状态与 UI（主线程） -------------------------------- */
                        ((Activity) context).runOnUiThread(() -> {
                            isRecording[0] = true;
                            toggleSamplingBtn.setText("停止采样");
                            logView.setText("开始采样: " + device.getName());
                        });
                    }

                    @Override      // 读取成功
                    public void onComplete(Map<String, Object> data) {
                        String ui = data == null ? null : (String) data.get("userInfo");
                        afterGetUser(ui);
                    }

                    @Override      // 读取失败：直接用 default 继续
                    public void onError(int code, String msg) {
                        Log.e("ECG", "readUserInfo onError: " + code + ", " + msg);
                        afterGetUser(null);    // 传 null → 会走 default
                    }
                });
            }
            else {               // —— 停止采样 ——
                stopSampling(device);
                eraseBtn.setEnabled(true);
                setUserBtn.setEnabled(true);
                eraseUserBtn.setEnabled(true);
                try {
                    if (writer[0] != null) {
                        writer[0].close();
                        writer[0] = null;
                    }
                } catch (IOException e) { e.printStackTrace(); }

                isRecording[0] = false;
                toggleSamplingBtn.setText("开始采样");
                logView.setText("停止采样: " + device.getName());
            }
        });

        eraseBtn.setOnClickListener(v->{
            CommandRequest request = new CommandRequest.Builder()
                    .setType(CommandType.eraseFlash)
                    .setTimeout(3*1000)
                    .build();
            VitalClient.getInstance().execute(device, request, new Callback() {
                @Override
                public void onComplete(Map<String, Object> data) {
                    // 开始采样命令一般返回 null，如果有返回数据也可打印出来
                    Log.d("ECG", "erase onComplete: " + data);
                }

                @Override
                public void onError(int code, String msg) {
                    Log.e("ECG", "erase onError: code=" + code + ", msg=" + msg);
                }
            });
        });
        connectBtn.setOnClickListener(v -> {
            toggleSamplingBtn.setEnabled(false);
            eraseBtn.setEnabled(false);
            setUserBtn.setEnabled(false);      // ★
            eraseUserBtn.setEnabled(false);    // ★
            BleConnectOptions options = new BleConnectOptions.Builder().setAutoConnect(false).build();
            VitalClient.getInstance().connect(device, options, new BluetoothConnectListener() {
                @Override
                public void onConnected(Device device) {
                    VitalClient.getInstance().registerDataReceiver(device, new DataReceiveListener() {

                        @Override
                        public void onReceiveData(Device device, Map<String, Object> data) {
                            String logStr = "接收到数据: " + GSON.toJson(data);
                            if (isRecording[0] && writer[0] != null) {
                                try {

                                    writer[0].write(data + "\n");
                                    writer[0].flush();
                                } catch (IOException e) { e.printStackTrace(); }
                            }
                            Log.e("TAG", logStr);
                        }

                        @Override
                        public void onBatteryChange(Device device, Map<String, Object> data) {
                            String logStr = "电池变化: " + GSON.toJson(data);
                            Log.e("TAG", logStr);
                        }

                        @Override
                        public void onDeviceInfoUpdate(Device device, Map<String, Object> data) {
                            String logStr = "设备信息更新: " + GSON.toJson(data);
                            Log.e("TAG", logStr);
                        }

                        @Override
                        public void onLeadStatusChange(Device device, boolean isLeadOn) {
                            String logStr = "导联状态变化: " + isLeadOn;
                            Log.e("TAG", logStr);
                        }

                        @Override
                        public void onFlashStatusChange(Device device, int remainderFlashBlock) {
                            String logStr = "闪光灯状态变化: 剩余块数 = " + remainderFlashBlock;
                            Log.e("TAG", logStr);
                        }

                        @Override
                        public void onFlashUploadFinish(Device device) {
                            String logStr = "上传完成: " + GSON.toJson(device);
                            Log.e("TAG", logStr);
                        }
                    });
                    VitalClient.getInstance().registerSampleDataReceiver(device, new SampleDataReceiveListener() {
                        @Override
                        public void onReceiveSampleData(Device device, boolean flash, SampleData data) {
                            SampleDataReceiveListener.super.onReceiveSampleData(device, flash, data);
                            Integer HR = (Integer) data.extras.get("hr");
                            Integer RR = (Integer) data.extras.get("rr");


                            int[] ecg = (int[]) data.extras.get("ecg");
                            Motion[] acc = (Motion[]) data.extras.get("acc");

                            ((Activity) context).runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    // 在这里更新 UI
                                    logView.setText("HR:" + HR + "RR:" + RR);

                                    for (int value : ecg) {
                                        plotView.addValue(value);
                                    }
                                    for(Motion motion :acc){
                                        plotViewX.addValue(motion.getX());
                                        plotViewY.addValue(motion.getY());
                                        plotViewZ.addValue(motion.getZ());

                                    }
                                }
                            });
                            List<Integer> newECGData = new ArrayList<>();
                            List<Integer> newAccXData = new ArrayList<>();
                            List<Integer> newAccYData = new ArrayList<>();
                            List<Integer> newAccZData = new ArrayList<>();
                            if (ecg != null) {
                                for (int value : ecg) {
                                    newECGData.add(value);
                                }
                            }
                            if (acc != null) {
                                for (Motion motion : acc) {
                                    newAccXData.add(motion.getX());
                                    newAccYData.add(motion.getY());
                                    newAccZData.add(motion.getZ());
                                }
                            }
                        }
                    });

                }

                @Override
                public void onDeviceReady(Device device) {
                    String logStr = "设备已准备好: " + GSON.toJson(device);
                    ((Activity) context).runOnUiThread(() -> {
                        toggleSamplingBtn.setEnabled(true);
                        eraseBtn.setEnabled(true);
                        setUserBtn.setEnabled(true);      // ★
                        eraseUserBtn.setEnabled(true);    // ★
                        Toast.makeText(context,
                                device.getName() + " 已就绪，可开始采样或擦除 Flash",
                                Toast.LENGTH_SHORT).show();

                        // 若你想在 logView 上也提示
                        logView.setText("设备已就绪: " + device.getName());
                    });
                    Log.e("TAG", logStr);
                }

                @Override
                public void onDisconnected(Device device, boolean isForce) {

                }

                @Override
                public void onError(Device device, int code, String msg) {
                    String logStr = "设备连接出错: " + code + ", 错误信息: " + msg;
                    Log.e("TAG", logStr);
                }
            });            logView.setText("连接到设备: " + device.getName());
            Toast.makeText(context, "连接成功: " + device.getName(), Toast.LENGTH_SHORT).show();
        });
        deviceContainer.addView(subDeviceView);

    }


    // 切换展开/收起 info
    public void toggleInfo() {
        if (infoVisible) {
            deviceContainer.animate()
                    .translationY(100)
                    .alpha(0f)
                    .setDuration(200)
                    .withEndAction(() -> deviceContainer.setVisibility(View.GONE))
                    .start();
        } else {
            deviceContainer.setAlpha(0f);
            deviceContainer.setTranslationY(100);
            deviceContainer.setVisibility(View.VISIBLE);
            deviceContainer.animate()
                    .translationY(0)
                    .alpha(1f)
                    .setDuration(200)
                    .start();
        }
        infoVisible = !infoVisible;
    }
    public static void startSampling(Device device) {
        VV330Manager vv330Manager = new VV330Manager(device);
        vv330Manager.switchToFullDualMode(device, new Callback() {
            @Override
            public void onStart() {
                Callback.super.onStart();
            }
        });

        CommandRequest request = new CommandRequest.Builder()
                .setType(CommandType.startSampling)
                .setTimeout(3000)  // 超时时间 3 秒
                .build();

        VitalClient.getInstance().execute(device, request, new Callback() {
            @Override
            public void onComplete(Map<String, Object> data) {
                // 开始采样命令一般返回 null，如果有返回数据也可打印出来
                Log.d("ECG", "startSampling onComplete: " + data);
            }

            @Override
            public void onError(int code, String msg) {
                Log.e("ECG", "startSampling onError: code=" + code + ", msg=" + msg);
            }
        });
    }
    public static void stopSampling(Device device) {
        CommandRequest request = new CommandRequest.Builder()
                .setType(CommandType.stopSampling)
                .setTimeout(3000)  // 超时时间 3 秒
                .build();

        VitalClient.getInstance().execute(device, request, new Callback() {
            @Override
            public void onComplete(Map<String, Object> data) {
                Log.d("ECG", "stopSampling onComplete: " + data);
            }

            @Override
            public void onError(int code, String msg) {
                Log.e("ECG", "stopSampling onError: code=" + code + ", msg=" + msg);
            }
        });
    }
}
