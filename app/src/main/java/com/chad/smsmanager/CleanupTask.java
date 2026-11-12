package com.chad.smsmanager;

import android.content.*;
import android.provider.Settings;
import android.provider.Telephony;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** A small durable journal; interrupted work is never silently retried. */
final class CleanupTask {
    private static final AtomicBoolean running = new AtomicBoolean(false);
    static boolean isRunning() { return running.get(); }
    static String summary(Context c) {
        android.content.SharedPreferences p = SafetyState.prefs(c);
        return JournalSummary.format(p.getString("delete_state", ""), running.get(), p.getInt("delete_total", 0),
            p.getInt("delete_count", 0), p.getLong("delete_pending", -1), p.getString("delete_result", ""));
    }
    static boolean start(Context context, ArrayList<Long> input) {
        if (!running.compareAndSet(false, true)) return false;
        Context c = context.getApplicationContext();
        final ArrayList<Long> ids = new ArrayList<>(input);
        android.content.SharedPreferences p = SafetyState.prefs(c);
        if (!p.edit().putString("delete_state", "running").putInt("delete_total", ids.size()).putInt("delete_count", 0).putLong("delete_pending", -1).putString("delete_result", "").commit()) { running.set(false); return false; }
        new Thread(() -> {
            int count = 0; String result = "清理完成。";
            try {
                for (long id : ids) {
                    if (!SafetyState.isDefault(c)) { result = "默认角色已变更，已停止。"; break; }
                    if (Settings.Global.getInt(c.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0) != 1) { result = "飞行模式已关闭，已停止；请立即恢复原应用。"; break; }
                    if (!p.edit().putLong("delete_pending", id).commit()) { result = "无法保存操作日志，已停止。"; break; }
                    count += c.getContentResolver().delete(ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, id), null, null);
                    if (!p.edit().putInt("delete_count", count).putLong("delete_pending", -1).commit()) { result = "结果日志保存失败，请重新查询核对。"; break; }
                }
            } catch (Exception e) { result = "操作中断：" + e.getClass().getSimpleName() + "。请重新查询核对，不会自动重试。"; }
            finally {
                p.edit().putString("delete_state", "finished").putString("delete_result", result).commit();
                running.set(false);
                SafetyState.refreshNotification(c);
            }
        }, "sms-cleanup").start();
        return true;
    }
}
