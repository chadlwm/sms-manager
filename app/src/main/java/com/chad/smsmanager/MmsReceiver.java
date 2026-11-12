package com.chad.smsmanager;
import android.content.*;
/** This cleanup utility does not download MMS. Never use it as a permanent default. */
public class MmsReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        SmsReceiver.notifyUser(context,"彩信未下载","此工具不支持彩信。请立即恢复原短信应用；本次彩信可能需要发送者重发。",4);
    }
}
