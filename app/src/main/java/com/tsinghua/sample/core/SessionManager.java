package com.tsinghua.sample.core;

import android.content.Context;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 录制会话管理：
 * 负责创建统一的目录结构：
 * Movies/FacialCollection/{experimentId}/Session_{wallMs}/
 *  子目录：front、back、imu、audio、ring、ecg、spo2、inference、markers
 */
public class SessionManager {
    private static final String TAG = "SessionManager";
    private static final String ROOT_DIR = "FacialCollection";
    private static SessionManager instance;

    private File sessionDir;
    private String experimentId;

    private SessionManager() {}

    public static synchronized SessionManager getInstance() {
        if (instance == null) {
            instance = new SessionManager();
        }
        return instance;
    }

    /** 若尚未有会话则创建；有则复用。 */
    public synchronized File ensureSession(Context context, String expId) {
        if (sessionDir != null) return sessionDir;
        return startSession(context, expId);
    }

    public synchronized File startSession(Context context, String expId) {
        experimentId = TextUtils.isEmpty(expId) ? "default" : expId;
        long wallMs = System.currentTimeMillis();
        String sessionName = "Session_" + wallMs;

        File base = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES);
        File root = new File(base, ROOT_DIR + "/" + experimentId + "/" + sessionName);
        if (!root.exists() && !root.mkdirs()) {
            Log.e(TAG, "无法创建会话目录: " + root.getAbsolutePath());
            return null;
        }
        sessionDir = root;
        createSubDirs();

        // 启动统一时基
        TimeSync.startSessionClock();

        Log.i(TAG, "会话开始: " + root.getAbsolutePath());
        return sessionDir;
    }

    private void createSubDirs() {
        String[] subs = {"front", "back", "imu", "audio", "ring", "ecg", "spo2", "inference", "markers", "info"};
        for (String s : subs) {
            File f = new File(sessionDir, s);
            //noinspection ResultOfMethodCallIgnored
            f.mkdirs();
        }
    }

    public File getSessionDir() {
        return sessionDir;
    }

    public File subDir(String name) {
        if (sessionDir == null) return null;
        return new File(sessionDir, name);
    }

    public File newFile(String subDir, String prefix, String ext) {
        File dir = subDir(subDir);
        if (dir == null) return null;
        String ts = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(new Date());
        return new File(dir, prefix + "_" + ts + ext);
    }

    public String getExperimentId() {
        return experimentId;
    }
}
