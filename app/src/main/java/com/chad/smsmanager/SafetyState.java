package com.chad.smsmanager;

import android.app.*;
import android.content.*;
import android.app.role.RoleManager;
import android.provider.Telephony;

/** Persists recovery guidance, never claims to restore a role without user action. */
final class SafetyState {
    static android.content.SharedPreferences prefs(Context c) { return c.getSharedPreferences("safety", Context.MODE_PRIVATE); }
    static String defaultPackage(Context c) {
        try { return Telephony.Sms.getDefaultSmsPackage(c); } catch (RuntimeException e) { return null; }
    }
    static Boolean roleHeld(Context c) {
        try { RoleManager manager=c.getSystemService(RoleManager.class);
            return manager!=null&&manager.isRoleAvailable(RoleManager.ROLE_SMS) ? manager.isRoleHeld(RoleManager.ROLE_SMS) : null;
        } catch (RuntimeException e) { return null; }
    }
    static DefaultSmsPolicy.Status roleStatus(Context c) { return DefaultSmsPolicy.classify(roleHeld(c),c.getPackageName(),defaultPackage(c)); }
    static boolean isDefault(Context c) { return roleStatus(c)==DefaultSmsPolicy.Status.HELD; }
    static boolean needsRecovery(Context c) {
        return Boolean.TRUE.equals(roleHeld(c))||c.getPackageName().equals(defaultPackage(c));
    }
    static String diagnostics(Context c) {
        Boolean role=roleHeld(c); String current=defaultPackage(c);
        return "Android " + android.os.Build.VERSION.RELEASE + " / API " + android.os.Build.VERSION.SDK_INT +
            "\nROLE_SMS：" + (role==null?"不可用或读取失败":role?"已持有":"未持有") +
            "\n默认短信包：" + (current==null?"未能读取":current) + "\n本应用：" + c.getPackageName() +
            "\n角色回调：" + prefs(c).getString("role_callback", "尚无回调") +
            "\n此信息不含短信正文或发件人。";
    }
    static void rememberOriginal(Context c) {
        String original = defaultPackage(c);
        if (original != null && !original.equals(c.getPackageName())) prefs(c).edit().putString("original", original).commit();
    }
    static void event(Context c, String text) { prefs(c).edit().putString("last_event", text).putLong("event_time", System.currentTimeMillis()).commit(); }
    static String guidance(Context c) {
        String original = prefs(c).getString("original", "");
        String current = defaultPackage(c);
        if (isDefault(c)) return "⚠ 当前仍是默认短信应用。退出、清理取消或重启都不会自动恢复！\nMMS 不会下载或保存，收信无法保障；发送短信/RCS/快捷回复不受支持。请立即在系统设置恢复原短信应用，再关闭飞行模式。\n原默认应用：" + (original.isEmpty() ? "未知，请在系统设置选择" : original);
        if (roleStatus(c)==DefaultSmsPolicy.Status.INCONSISTENT || roleStatus(c)==DefaultSmsPolicy.Status.UNKNOWN) return "默认短信状态暂无法可靠核对，已禁用删除。请在系统默认应用设置确认并恢复原应用；不要把退出应用当作恢复。\n" + diagnostics(c);
        if (!original.isEmpty()) return original.equals(current) ? "已核对：系统默认短信应用已恢复为 " + original : "本工具当前不是默认短信应用，但尚未核对恢复为原应用 " + original + "。请到系统设置确认。";
        return "只读模式。删除需临时默认角色，不能保证期间收信安全。";
    }
    static void refreshNotification(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (!needsRecovery(c)) { nm.cancel(10); return; }
        nm.createNotificationChannel(new NotificationChannel("recovery", "恢复默认短信应用提醒", NotificationManager.IMPORTANCE_HIGH));
        PendingIntent pi = PendingIntent.getActivity(c, 10, new Intent(c, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(c, "recovery").setSmallIcon(R.drawable.ic_notification_sms)
            .setContentTitle("请恢复原默认短信应用")
            .setContentText("清理工具仍为默认；MMS 和收信安全无法保障。点击查看恢复入口。")
            .setContentIntent(pi).setOngoing(true).setVisibility(Notification.VISIBILITY_PRIVATE).build();
        try { nm.notify(10, n); } catch (SecurityException ignored) { /* Persistent in-app guidance remains. */ }
    }
}
