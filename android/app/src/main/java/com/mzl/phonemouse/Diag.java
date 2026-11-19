package com.mzl.phonemouse;

import android.content.Context;
import android.os.Build;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 本地日志：写到应用私有目录，拔掉数据线也照常记录。
 * 文件位置：Android/data/com.mzl.phonemouse/files/diag.log
 * 超过 1.5MB 自动滚动一个备份（diag.1.log）。
 */
public final class Diag {

    private static final Object LOCK = new Object();
    private static final long MAX_BYTES = 1500 * 1024;
    private static File logFile;
    private static final SimpleDateFormat FMT =
            new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US);

    private Diag() {
    }

    public static void init(Context c) {
        try {
            File dir = c.getExternalFilesDir(null);
            if (dir == null) dir = c.getFilesDir();
            if (!dir.exists()) dir.mkdirs();
            logFile = new File(dir, "diag.log");
            log("APP", "==== 启动 v" + version(c) + " 机型=" + Build.MODEL
                    + " Android=" + Build.VERSION.RELEASE + " ====");
        } catch (Exception ignored) {
        }
    }

    private static String version(Context c) {
        try {
            return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "?";
        }
    }

    public static void log(String tag, String msg) {
        android.util.Log.d("PhoneMouse", tag + " " + msg);
        if (logFile == null) return;
        synchronized (LOCK) {
            try {
                if (logFile.length() > MAX_BYTES) {
                    File old = new File(logFile.getParentFile(), "diag.1.log");
                    if (old.exists()) old.delete();
                    logFile.renameTo(old);
                }
                BufferedWriter w = new BufferedWriter(new FileWriter(logFile, true));
                w.write(FMT.format(new Date()));
                w.write(" [");
                w.write(tag);
                w.write("] ");
                w.write(msg);
                w.write("\n");
                w.close();
            } catch (Exception ignored) {
            }
        }
    }
}
