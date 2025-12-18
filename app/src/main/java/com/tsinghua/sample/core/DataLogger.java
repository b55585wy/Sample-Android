package com.tsinghua.sample.core;

import android.util.Log;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 轻量 CSV/文本写入器，封装基础的创建、写入、flush、close。
 * 仅做最小依赖，便于在各采集模块中复用。
 *
 * 性能优化：使用异步队列写入，避免阻塞调用线程
 */
public class DataLogger {
    private static final String TAG = "DataLogger";
    private final File file;
    private BufferedWriter writer;

    // 异步写入队列
    private final BlockingQueue<String> writeQueue = new LinkedBlockingQueue<>(10000);
    private Thread writerThread;
    private volatile boolean running = true;

    // 批量写入控制
    private static final int FLUSH_INTERVAL_MS = 1000; // 每秒flush一次
    private long lastFlushTime = 0;
    private int linesSinceFlush = 0;
    private static final int LINES_PER_FLUSH = 100; // 每100行flush一次

    public DataLogger(File file, String header) throws IOException {
        this.file = file;
        ensureParentExists(file);
        this.writer = new BufferedWriter(new FileWriter(file, false), 8192); // 8KB缓冲
        if (header != null && !header.isEmpty()) {
            writer.write(header);
            if (!header.endsWith("\n")) {
                writer.write("\n");
            }
            writer.flush();
        }

        // 启动异步写入线程
        startWriterThread();
    }

    private void startWriterThread() {
        writerThread = new Thread(() -> {
            while (running || !writeQueue.isEmpty()) {
                try {
                    // 使用带超时的poll，避免无限阻塞
                    String line = writeQueue.poll(100, TimeUnit.MILLISECONDS);
                    if (line != null) {
                        writeLineInternal(line);
                    }

                    // 检查是否需要flush
                    long now = System.currentTimeMillis();
                    if (linesSinceFlush >= LINES_PER_FLUSH ||
                        (now - lastFlushTime) >= FLUSH_INTERVAL_MS) {
                        flushInternal();
                        lastFlushTime = now;
                        linesSinceFlush = 0;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            // 退出前确保所有数据写入
            drainQueue();
            flushInternal();
            Log.d(TAG, "Writer thread exited for: " + file.getName());
        }, "DataLogger-" + file.getName());
        writerThread.setPriority(Thread.MIN_PRIORITY + 1); // 低优先级
        writerThread.start();
    }

    private void drainQueue() {
        String line;
        while ((line = writeQueue.poll()) != null) {
            writeLineInternal(line);
        }
    }

    private void writeLineInternal(String line) {
        if (writer == null) return;
        try {
            writer.write(line);
            if (!line.endsWith("\n")) {
                writer.write("\n");
            }
            linesSinceFlush++;
        } catch (IOException e) {
            Log.e(TAG, "writeLineInternal error", e);
        }
    }

    private void flushInternal() {
        if (writer == null) return;
        try {
            writer.flush();
        } catch (IOException e) {
            Log.e(TAG, "flushInternal error", e);
        }
    }

    private void ensureParentExists(File f) {
        File parent = f.getParentFile();
        if (parent != null && !parent.exists()) {
            //noinspection ResultOfMethodCallIgnored
            parent.mkdirs();
        }
    }

    /**
     * 异步写入一行数据（非阻塞）
     */
    public void writeLine(String line) {
        if (!running || writer == null) return;

        // 尝试加入队列，如果队列满则丢弃（避免阻塞）
        if (!writeQueue.offer(line)) {
            Log.w(TAG, "Write queue full, dropping line");
        }
    }

    /**
     * 同步flush（等待队列清空）
     */
    public void flush() {
        if (writer == null) return;

        // 等待队列清空
        long startTime = System.currentTimeMillis();
        while (!writeQueue.isEmpty() && (System.currentTimeMillis() - startTime) < 5000) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        flushInternal();
    }

    public void close() {
        running = false;

        // 等待写入线程结束
        if (writerThread != null) {
            try {
                writerThread.join(5000); // 最多等5秒
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // 确保所有数据写入
        drainQueue();

        if (writer != null) {
            try {
                writer.flush();
                writer.close();
            } catch (IOException e) {
                Log.e(TAG, "close error", e);
            } finally {
                writer = null;
            }
        }
    }

    public File getFile() {
        return file;
    }
}
