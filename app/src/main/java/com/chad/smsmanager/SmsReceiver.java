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
        try { context.getContentResolver().insert(Telephony.Sms.Inbox.CONTENT_URI,values); notifyUser(context,"收到新短信","短信已保存。清理完成后请恢复原短信应用。",2); }
        catch(Exception e){notifyUser(context,"短信保存失败","请立即恢复原短信应用，并检查系统短信权限。",3);}
    }
    static void notifyUser(Context c,String title,String body,int id){
        NotificationManager nm=c.getSystemService(NotificationManager.class);nm.createNotificationChannel(new NotificationChannel("sms","短信与清理提醒",NotificationManager.IMPORTANCE_HIGH));
        PendingIntent pi=PendingIntent.getActivity(c,0,new Intent(c,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification n=new Notification.Builder(c,"sms").setSmallIcon(android.R.drawable.ic_dialog_email).setContentTitle(title).setContentText(body).setContentIntent(pi).setAutoCancel(true).setVisibility(Notification.VISIBILITY_PRIVATE).build();
        try{nm.notify(id,n);}catch(SecurityException ignored){}
    }
}
