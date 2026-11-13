package com.chad.smsmanager;

import android.content.*;
import android.database.Cursor;
import android.provider.Settings;
import android.provider.Telephony;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Verified per-message deletion with a durable result and cooperative cancellation. */
final class CleanupTask {
    private static final AtomicBoolean running = new AtomicBoolean(false);
    private static final AtomicBoolean cancelled = new AtomicBoolean(false);
    static boolean isRunning() { return running.get(); }
    static void cancel() { if (running.get()) cancelled.set(true); }
    static long operationId(Context c) { return SafetyState.prefs(c).getLong("delete_operation_id", 0); }
    static String progress(Context c) {
        SharedPreferences p = SafetyState.prefs(c);
        return "已处理 " + p.getInt("delete_processed", 0) + " / " + p.getInt("delete_total", 0) + " 条" + (cancelled.get() && running.get() ? " · 正在停止后续删除" : "");
    }
    static String summary(Context c) {
        SharedPreferences p = SafetyState.prefs(c);
        return JournalSummary.formatDetailed(p.getString("delete_state", ""), running.get(), p.getInt("delete_total", 0),
            p.getInt("delete_processed", 0), p.getInt("delete_count", 0), p.getInt("delete_missing", 0),
            p.getInt("delete_failed", 0), p.getInt("delete_unknown", 0), p.getLong("delete_pending", -1), p.getString("delete_result", ""));
    }
    private static boolean allowed(Context c) {
        return !cancelled.get() && SafetyState.isDefault(c) && Settings.Global.getInt(c.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0) == 1;
    }
    static boolean start(Context context, ArrayList<Long> input) {
        long[] ids=new long[input.size()];for(int i=0;i<ids.length;i++)ids[i]=input.get(i);return start(context,ids);
    }
    static boolean start(Context context, long[] input) {
        if (input.length==0 || !running.compareAndSet(false, true)) return false;
        cancelled.set(false);
        Context c = context.getApplicationContext();
        final long[] ids = SmsSelection.sortedUnique(input);
        SharedPreferences p = SafetyState.prefs(c);
        long operation = Math.max(System.currentTimeMillis(), p.getLong("delete_operation_id", 0) + 1);
        if (!p.edit().putLong("delete_operation_id", operation).putString("delete_state", "running")
            .putString("delete_outcome", "running").putInt("delete_total", ids.length).putInt("delete_processed", 0)
            .putInt("delete_count", 0).putInt("delete_missing", 0).putInt("delete_failed", 0).putInt("delete_unknown", 0)
            .putLong("delete_pending", -1).putString("delete_result", "").commit()) { running.set(false); return false; }
        new Thread(() -> {
            int count = 0, missing = 0, failed = 0, unknown = 0, processed = 0;
            String result = "已完成逐条核验。", outcome = "completed";
            VerifiedDeletion.Store store = new VerifiedDeletion.Store() {
                public boolean exists(long id) throws Exception {
                    try (Cursor cursor = c.getContentResolver().query(Telephony.Sms.CONTENT_URI, new String[]{"_id"}, "_id = ?", new String[]{Long.toString(id)}, null)) {
                        if (cursor == null) throw new IllegalStateException("SMS query returned no cursor");
                        return cursor.moveToFirst();
                    }
                }
                public int deleteItem(long id) { return c.getContentResolver().delete(ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, id), null, null); }
                public int deleteSelected(long id) { return c.getContentResolver().delete(Telephony.Sms.CONTENT_URI, "_id = ?", new String[]{Long.toString(id)}); }
            };
            try {
                for (long id : ids) {
                    if (cancelled.get()) { result = "已停止后续删除；已删除的短信不能撤销。"; outcome = "cancelled"; break; }
                    if (!SafetyState.isDefault(c)) { result = "默认短信角色已变更，已停止。请在系统设置核对默认应用。"; outcome = "stopped"; break; }
                    if (Settings.Global.getInt(c.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0) != 1) { result = "飞行模式已关闭，已停止；请立即恢复原短信应用。"; outcome = "stopped"; break; }
                    if (!p.edit().putLong("delete_pending", id).commit()) { result = "无法保存操作日志，未继续删除。"; outcome = "failed"; break; }
                    VerifiedDeletion.Result one = VerifiedDeletion.run(store, () -> allowed(c), id);
                    processed++;
                    switch (one.kind) {
                        case DELETED: count++; break;
                        case ALREADY_ABSENT: missing++; break;
                        case FAILED: failed++; break;
                        case UNCONFIRMED: unknown++; break;
                    }
                    long pending = one.kind == VerifiedDeletion.Kind.UNCONFIRMED ? id : -1;
                    if (!p.edit().putInt("delete_processed", processed).putInt("delete_count", count).putInt("delete_missing", missing)
                        .putInt("delete_failed", failed).putInt("delete_unknown", unknown).putLong("delete_pending", pending).commit()) {
                        result = "结果日志保存失败，无法保证记录完整。请重新查询核对，未继续删除。"; outcome = "failed"; break;
                    }
                    if (one.kind == VerifiedDeletion.Kind.FAILED || one.kind == VerifiedDeletion.Kind.UNCONFIRMED) {
                        result = one.detail; outcome = count > 0 ? "partial" : "failed";
                        if (cancelled.get()) { result = "已停止后续删除，已删除的短信不能撤销。\n" + result; outcome = "cancelled"; }
                        break;
                    }
                }
            } catch (Exception e) {
                result = "任务中断（" + e.getClass().getSimpleName() + "），未自动重试。请重新查询核对。"; outcome = "failed";
            } finally {
                p.edit().putString("delete_state", "finished").putString("delete_outcome", outcome).putString("delete_result", result).commit();
                running.set(false);
                SafetyState.refreshNotification(c);
            }
        }, "sms-cleanup").start();
        return true;
    }
}
