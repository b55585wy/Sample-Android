package com.tsinghua.sample;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.tsinghua.sample.activity.EcgSettingsActivity;
import com.vivalnk.sdk.model.Device;

import java.util.ArrayList;
import java.util.List;

public class EcgAdapter extends RecyclerView.Adapter<EcgAdapter.DeviceViewHolder> {
    private Context context;
    private ArrayList<Device> devices;
    private List<Device> selectedDevices; // 👈 选中的设备列表

    public EcgAdapter(Context context, ArrayList<Device> devices, List<Device> selectedDevices) {
        this.context = context;
        this.devices = devices;
        this.selectedDevices = selectedDevices;
    }

    @NonNull
    @Override
    public DeviceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_ecg_device, parent, false);
        return new DeviceViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DeviceViewHolder holder, int position) {
        Device device = devices.get(position);

        holder.deviceName.setText(device.getName());
        holder.deviceMAC.setText(device.getId());

        boolean isSelected = selectedDevices.contains(device);

        // 背景色
        int bgColor   = ContextCompat.getColor(context,
                isSelected ? R.color.ecgPrimary : R.color.ecgSurface);
        holder.rootCard.setCardBackgroundColor(bgColor);

        // 字体颜色
        int textColor = ContextCompat.getColor(context,
                isSelected ? R.color.ecgOnPrimary : R.color.ecgOnSurface);
        holder.deviceName.setTextColor(textColor);
        holder.deviceMAC.setTextColor(textColor);

        holder.itemView.setOnClickListener(v ->
                ((EcgSettingsActivity) context).onDeviceSelected(device));
    }

    @Override
    public int getItemCount() {
        return devices.size(); // ✅ 用 devices.size()
    }

    public static class DeviceViewHolder extends RecyclerView.ViewHolder {
        CardView rootCard;          // ⭐ 新成员
        TextView deviceName, deviceMAC;

        public DeviceViewHolder(View itemView) {
            super(itemView);
            rootCard   = itemView.findViewById(R.id.rootCard);
            deviceName = itemView.findViewById(R.id.deviceName);
            deviceMAC  = itemView.findViewById(R.id.deviceMAC);
        }
    }
}
