package com.chad.smsmanager;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
public class RespondService extends Service {
    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public int onStartCommand(Intent intent,int flags,int id) {
        SmsReceiver.notifyUser(this,"快捷回复未发送","此工具不支持发送短信，请恢复原短信应用后重新发送。",5);
        stopSelf(id);return START_NOT_STICKY;
    }
}
