package com.tsinghua.sample.activity;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.tsinghua.sample.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 采集信息填写页面
 * 录制结束后弹出，用于填写受试者的基本信息
 */
public class PatientInfoActivity extends AppCompatActivity {
    private static final String TAG = "PatientInfoActivity";

    // Intent参数键
    public static final String EXTRA_SESSION_DIR = "session_dir";
    public static final String EXTRA_SESSION_ID = "session_id";
    public static final String EXTRA_RECORDING_TIME = "recording_time";

    // 视图组件 - 基本信息
    private TextInputEditText etName;
    private RadioGroup rgGender;
    private RadioButton rbMale;
    private RadioButton rbFemale;
    private TextInputEditText etHeight;
    private TextInputEditText etWeight;
    private TextInputEditText etAge;
    private TextInputEditText etNotes;

    // 视图组件 - 录制信息
    private TextView tvSessionId;
    private TextView tvRecordingTime;
    private TextView tvDeviceModel;

    // 按钮
    private MaterialButton btnSubmit;
    private MaterialButton btnSkip;

    // 会话信息
    private String sessionDir;
    private String sessionId;
    private String recordingTime;
    private String deviceModel;

    /**
     * 启动采集信息页面的便捷方法
     */
    public static void start(Context context, String sessionDir, String sessionId, String recordingTime) {
        Intent intent = new Intent(context, PatientInfoActivity.class);
        intent.putExtra(EXTRA_SESSION_DIR, sessionDir);
        intent.putExtra(EXTRA_SESSION_ID, sessionId);
        intent.putExtra(EXTRA_RECORDING_TIME, recordingTime);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_patient_info);

        // 获取Intent参数
        sessionDir = getIntent().getStringExtra(EXTRA_SESSION_DIR);
        sessionId = getIntent().getStringExtra(EXTRA_SESSION_ID);
        recordingTime = getIntent().getStringExtra(EXTRA_RECORDING_TIME);

        // 获取设备型号
        deviceModel = getDeviceModel();

        // 初始化视图
        initViews();

        // 显示会话信息
        displaySessionInfo();
    }

    private void initViews() {
        // 基本信息
        etName = findViewById(R.id.etName);
        rgGender = findViewById(R.id.rgGender);
        rbMale = findViewById(R.id.rbMale);
        rbFemale = findViewById(R.id.rbFemale);
        etHeight = findViewById(R.id.etHeight);
        etWeight = findViewById(R.id.etWeight);
        etAge = findViewById(R.id.etAge);

        // 录制信息
        tvSessionId = findViewById(R.id.tvSessionId);
        tvRecordingTime = findViewById(R.id.tvRecordingTime);
        tvDeviceModel = findViewById(R.id.tvDeviceModel);

        // 备注
        etNotes = findViewById(R.id.etNotes);

        // 按钮
        btnSubmit = findViewById(R.id.btnSubmit);
        btnSkip = findViewById(R.id.btnSkip);

        // 设置按钮事件
        btnSubmit.setOnClickListener(v -> submitInfo());
        btnSkip.setOnClickListener(v -> skipInfo());
    }

    private void displaySessionInfo() {
        if (sessionId != null) {
            tvSessionId.setText(sessionId);
        }
        if (recordingTime != null) {
            tvRecordingTime.setText(recordingTime);
        }
        tvDeviceModel.setText(deviceModel);
    }

    /**
     * 获取设备型号
     */
    private String getDeviceModel() {
        String manufacturer = Build.MANUFACTURER;
        String model = Build.MODEL;
        String androidVersion = Build.VERSION.RELEASE;
        int sdkVersion = Build.VERSION.SDK_INT;

        return String.format(Locale.US, "%s %s (Android %s, SDK %d)",
                manufacturer, model, androidVersion, sdkVersion);
    }

    /**
     * 获取选中的性别
     */
    private String getSelectedGender() {
        int checkedId = rgGender.getCheckedRadioButtonId();
        if (checkedId == R.id.rbMale) {
            return "男";
        } else if (checkedId == R.id.rbFemale) {
            return "女";
        }
        return "";
    }

    /**
     * 提交信息
     */
    private void submitInfo() {
        // 验证必填字段
        String name = getTextValue(etName);
        if (name.isEmpty()) {
            Toast.makeText(this, "请输入姓名", Toast.LENGTH_SHORT).show();
            etName.requestFocus();
            return;
        }

        // 保存信息到文件
        boolean success = saveInfoToFile(name);

        if (success) {
            Toast.makeText(this, "采集信息已保存", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "保存失败", Toast.LENGTH_SHORT).show();
        }

        finish();
    }

    /**
     * 跳过信息填写
     */
    private void skipInfo() {
        Log.w(TAG, "用户跳过信息填写");
        finish();
    }

    /**
     * 保存信息到文件
     */
    private boolean saveInfoToFile(String name) {
        if (sessionDir == null || sessionDir.isEmpty()) {
            Log.e(TAG, "Session目录为空");
            return false;
        }

        // 构建info目录路径
        File infoDir = new File(sessionDir, "info");
        if (!infoDir.exists()) {
            if (!infoDir.mkdirs()) {
                Log.e(TAG, "无法创建info目录: " + infoDir.getAbsolutePath());
                return false;
            }
        }

        // 生成文件名
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        File infoFile = new File(infoDir, "subject_info_" + timestamp + ".txt");

        // 构建文件内容
        String content = buildInfoContent(name);

        // 写入文件
        try (FileOutputStream fos = new FileOutputStream(infoFile);
             OutputStreamWriter writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {

            writer.write(content);
            writer.flush();

            Log.i(TAG, "采集信息已保存: " + infoFile.getAbsolutePath());
            return true;

        } catch (Exception e) {
            Log.e(TAG, "保存采集信息失败", e);
            return false;
        }
    }

    /**
     * 构建信息文件内容
     */
    private String buildInfoContent(String name) {
        StringBuilder sb = new StringBuilder();
        String dateTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());

        sb.append("=== 采集信息记录 ===\n");
        sb.append("姓名: ").append(name).append("\n");

        // 性别
        String gender = getSelectedGender();
        if (!gender.isEmpty()) {
            sb.append("性别: ").append(gender).append("\n");
        }

        // 年龄
        String age = getTextValue(etAge);
        if (!age.isEmpty()) {
            sb.append("年龄: ").append(age).append(" 岁\n");
        }

        // 身高
        String height = getTextValue(etHeight);
        if (!height.isEmpty()) {
            sb.append("身高: ").append(height).append(" cm\n");
        }

        // 体重
        String weight = getTextValue(etWeight);
        if (!weight.isEmpty()) {
            sb.append("体重: ").append(weight).append(" kg\n");
        }

        sb.append("\n");
        sb.append("=== 录制信息 ===\n");
        sb.append("会话ID: ").append(sessionId != null ? sessionId : "--").append("\n");
        sb.append("录制时长: ").append(recordingTime != null ? recordingTime : "--").append("\n");
        sb.append("提交时间: ").append(dateTime).append("\n");

        sb.append("\n");
        sb.append("=== 设备信息 ===\n");
        sb.append("设备型号: ").append(deviceModel).append("\n");
        sb.append("制造商: ").append(Build.MANUFACTURER).append("\n");
        sb.append("品牌: ").append(Build.BRAND).append("\n");
        sb.append("型号: ").append(Build.MODEL).append("\n");
        sb.append("设备名: ").append(Build.DEVICE).append("\n");
        sb.append("产品名: ").append(Build.PRODUCT).append("\n");
        sb.append("Android版本: ").append(Build.VERSION.RELEASE).append("\n");
        sb.append("SDK版本: ").append(Build.VERSION.SDK_INT).append("\n");

        // 备注
        String notes = getTextValue(etNotes);
        if (!notes.isEmpty()) {
            sb.append("\n=== 备注 ===\n");
            sb.append(notes).append("\n");
        }

        sb.append("\n--- 记录结束 ---\n");

        return sb.toString();
    }

    /**
     * 获取输入框文本值（去除首尾空格）
     */
    private String getTextValue(TextInputEditText editText) {
        if (editText == null || editText.getText() == null) {
            return "";
        }
        return editText.getText().toString().trim();
    }

    @Override
    public void onBackPressed() {
        // 禁用返回键，必须点击提交或跳过
        Toast.makeText(this, "请点击提交或跳过", Toast.LENGTH_SHORT).show();
    }
}
