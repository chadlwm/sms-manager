package com.chad.smsmanager;
import android.app.*;
import android.content.*;
import android.os.Build;
import android.provider.Telephony;
import android.telephony.SmsMessage;
public class SmsReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){
        if(!Telephony.Sms.Intents.SMS_DELIVER_ACTION.equals(intent.getAction()))return;
        SmsMessage[] parts=Telephony.Sms.Intents.getMessagesFromIntent(intent);if(parts==null||parts.length==0)return;
        StringBuilder body=new StringBuilder();for(SmsMessage part:parts)body.append(part.getMessageBody());
        ContentValues values=new ContentValues();values.put("address",parts[0].getOriginatingAddress());values.put("body",body.toString());values.put("date",System.currentTimeMillis());values.put("date_sent",parts[0].getTimestampMillis());values.put("read",0);values.put("seen",0);values.put("sub_id",intent.getIntExtra("subscription",-1));
        final PendingResult pending = goAsync();
        final Context app = context.getApplicationContext();
        new Thread(() -> {
            try {
                android.net.Uri saved = app.getContentResolver().insert(Telephony.Sms.Inbox.CONTENT_URI, values);
                if (saved == null) throw new IllegalStateException("SMS provider returned no saved URI");
                notifyUser(app,"收到新短信","系统短信库已返回保存确认；清理后请恢复原应用。",2);
            } catch (Exception e) {
                String failure = "普通 SMS 保存未成功确认（" + e.getClass().getSimpleName() + "）。此工具没有自动恢复这条短信的能力，请恢复原应用，并向发送者核实/要求重发。";
                SafetyState.event(app, failure);
                notifyUser(app,"短信保存未确认",failure,3);
            } finally { pending.finish(); }
        }, "sms-receive").start();
    }
    static void notifyUser(Context c,String title,String body,int id){
        try {
        NotificationManager nm=c.getSystemService(NotificationManager.class);nm.createNotificationChannel(new NotificationChannel("sms","短信与清理提醒",NotificationManager.IMPORTANCE_HIGH));
        PendingIntent pi=PendingIntent.getActivity(c,0,new Intent(c,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification n=new Notification.Builder(c,"sms").setSmallIcon(android.R.drawable.ic_dialog_email).setContentTitle(title).setContentText(body).setContentIntent(pi).setAutoCancel(true).setVisibility(Notification.VISIBILITY_PRIVATE).build();
        nm.notify(id,n);
        } catch(RuntimeException ignored) { /* Notification availability must not change the SMS save result. */ }
    }
}
