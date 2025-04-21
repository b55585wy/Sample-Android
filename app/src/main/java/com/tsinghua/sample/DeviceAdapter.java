package com.tsinghua.sample;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.recyclerview.widget.RecyclerView;

import com.tsinghua.sample.activity.BackCameraSettingsActivity;
import com.tsinghua.sample.activity.EcgSettingsActivity;
import com.tsinghua.sample.activity.FrontCameraSettingsActivity;
import com.tsinghua.sample.activity.ImuSettingsActivity;
import com.tsinghua.sample.activity.MicrophoneSettingsActivity;
import com.tsinghua.sample.activity.OximeterSettingsActivity;
import com.tsinghua.sample.activity.RingSettingsActivity;
import com.tsinghua.sample.device.OximeterService;
import com.tsinghua.sample.model.Device;

import java.util.List;

public class DeviceAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private Context context;
    private List<Device> devices;
    private OximeterService oxService;
    private boolean serviceBound = false;
    public DeviceAdapter(Context context, List<Device> devices) {
        this.context = context;
        this.devices = devices;
    }
    private final ServiceConnection oximeterConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            oxService = ((OximeterService.LocalBinder) service).getService();
            oxService.setListener(data -> {
                // 实时数据更新，可以对 UI 做刷新操作（建议传 deviceId）
                Log.d("Oximeter", "HR=" + data.hr + " SPO2=" + data.spo2 + " BVP=" + data.bvp);
            });
            serviceBound = true;
            oxService.startRecording(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) + "/oximeter");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound = false;
        }
    };
    @Override
    public int getItemViewType(int position) {
        return devices.get(position).getType();
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(context);
        switch (viewType) {
            case Device.TYPE_FRONT_CAMERA:
                return new FrontCameraViewHolder(inflater.inflate(R.layout.item_front_camera, parent, false));
            case Device.TYPE_BACK_CAMERA:
                return new BackCameraViewHolder(inflater.inflate(R.layout.item_back_camera, parent, false));
            case Device.TYPE_MICROPHONE:
                return new MicrophoneViewHolder(inflater.inflate(R.layout.item_microphone, parent, false));
            case Device.TYPE_IMU:
                return new ImuViewHolder(inflater.inflate(R.layout.item_imu, parent, false));
            case Device.TYPE_RING:
                return new RingViewHolder(inflater.inflate(R.layout.item_ring, parent, false));
            case Device.TYPE_ECG:
                return new EcgViewHolder(inflater.inflate(R.layout.item_ecg, parent, false));
            case Device.TYPE_OXIMETER:
                return new OximeterViewHolder(inflater.inflate(R.layout.item_oximeter, parent, false));
            default:
                throw new IllegalArgumentException("Invalid device type");
        }
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        Device device = devices.get(position);

        if (holder instanceof FrontCameraViewHolder) {
            FrontCameraViewHolder h = (FrontCameraViewHolder) holder;
            h.deviceName.setText(device.getName());
            h.startBtn.setText(device.isRunning() ? "结束" : "开始");
            h.startBtn.setOnClickListener(v -> {
                device.setRunning(!device.isRunning());
                notifyItemChanged(position);
            });
            h.settingsBtn.setOnClickListener(v -> {
                Intent intent = new Intent(context, FrontCameraSettingsActivity.class);
                intent.putExtra("deviceName", device.getName());
                context.startActivity(intent);
            });
        } else if (holder instanceof BackCameraViewHolder) {
            BackCameraViewHolder h = (BackCameraViewHolder) holder;
            h.deviceName.setText(device.getName());
            h.startBtn.setText(device.isRunning() ? "结束" : "开始");
            h.startBtn.setOnClickListener(v -> {
                device.setRunning(!device.isRunning());
                notifyItemChanged(position);
            });
            h.settingsBtn.setOnClickListener(v -> {
                Intent intent = new Intent(context, BackCameraSettingsActivity.class);
                intent.putExtra("deviceName", device.getName());
                context.startActivity(intent);
            });

        } else if (holder instanceof MicrophoneViewHolder) {
            MicrophoneViewHolder h = (MicrophoneViewHolder) holder;
            h.deviceName.setText(device.getName());
            h.startBtn.setText(device.isRunning() ? "结束" : "开始");
            h.startBtn.setOnClickListener(v -> {
                device.setRunning(!device.isRunning());
                notifyItemChanged(position);
            });
            h.settingsBtn.setOnClickListener(v -> {
                Intent intent = new Intent(context, MicrophoneSettingsActivity.class);
                intent.putExtra("deviceName", device.getName());
                context.startActivity(intent);
            });
        } else if (holder instanceof ImuViewHolder) {
            ImuViewHolder h = (ImuViewHolder) holder;
            h.deviceName.setText(device.getName());
            h.startBtn.setText(device.isRunning() ? "结束" : "开始");
            h.startBtn.setOnClickListener(v -> {
                device.setRunning(!device.isRunning());
                notifyItemChanged(position);
            });
            h.settingsBtn.setOnClickListener(v -> {
                Intent intent = new Intent(context, ImuSettingsActivity.class);
                intent.putExtra("deviceName", device.getName());
                context.startActivity(intent);
            });
        } else if (holder instanceof RingViewHolder) {
            RingViewHolder h = (RingViewHolder) holder;
            h.deviceName.setText(device.getName());
            h.startBtn.setText(device.isRunning() ? "结束" : "开始");
            h.startBtn.setOnClickListener(v -> {
                device.setRunning(!device.isRunning());
                notifyItemChanged(position);
            });
            h.settingsBtn.setOnClickListener(v -> {
                Intent intent = new Intent(context, RingSettingsActivity.class);
                intent.putExtra("deviceName", device.getName());
                context.startActivity(intent);
            });
        } else if (holder instanceof EcgViewHolder) {
            EcgViewHolder h = (EcgViewHolder) holder;
            h.deviceName.setText(device.getName());
            h.startBtn.setText(device.isRunning() ? "结束" : "开始");
            h.startBtn.setOnClickListener(v -> {
                device.setRunning(!device.isRunning());
                notifyItemChanged(position);
            });
            h.settingsBtn.setOnClickListener(v -> {
                Intent intent = new Intent(context, EcgSettingsActivity.class);
                intent.putExtra("deviceName", device.getName());
                context.startActivity(intent);
            });
        } else if (holder instanceof OximeterViewHolder) {
            OximeterViewHolder h = (OximeterViewHolder) holder;

            h.deviceName.setText(device.getName());
            h.startBtn.setText(device.isRunning() ? "结束" : "开始");

            h.startBtn.setOnClickListener(v -> {
                boolean running = !device.isRunning();
                device.setRunning(running);
                notifyItemChanged(position);

                if (running) {
                    Intent serviceIntent = new Intent(context, OximeterService.class);
                    context.bindService(serviceIntent, oximeterConnection, Context.BIND_AUTO_CREATE);
                } else {
                    // 停止采集并解绑
                    if (serviceBound && oxService != null) {
                        oxService.stopRecording();
                        context.unbindService(oximeterConnection);
                        serviceBound = false;
                    }
                }
            });

            h.settingsBtn.setOnClickListener(v -> {
                Intent intent = new Intent(context, OximeterSettingsActivity.class);
                intent.putExtra("deviceName", device.getName());
                context.startActivity(intent);
            });
        }
    }

    @Override
    public int getItemCount() {
        return devices.size();
    }
}
