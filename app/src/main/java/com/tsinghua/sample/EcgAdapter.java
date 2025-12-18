package com.tsinghua.sample;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.tsinghua.sample.activity.EcgSettingsActivity;
import com.vivalnk.sdk.model.Device;

import java.util.List;

/**
 * 设置页心电设备列表适配器（简化版）
 * 仅负责展示扫描到的设备，点击即选中并回调到 EcgSettingsActivity
 */
public class EcgAdapter extends RecyclerView.Adapter<EcgAdapter.ViewHolder> {
    private final EcgSettingsActivity activity;
    private final List<Device> devices;
    private final List<Device> selected;

    public EcgAdapter(EcgSettingsActivity activity, List<Device> devices, List<Device> selected) {
        this.activity = activity;
        this.devices = devices;
        this.selected = selected;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_ecg_device, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Device d = devices.get(position);
        holder.name.setText(d.getName());
        holder.mac.setText(d.getId());
        holder.itemView.setOnClickListener(v -> {
            activity.onDeviceSelected(d);
            Toast.makeText(activity, "已选择 " + d.getName(), Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public int getItemCount() {
        return devices == null ? 0 : devices.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView mac;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.deviceName);
            mac = itemView.findViewById(R.id.deviceMAC);
        }
    }
}
