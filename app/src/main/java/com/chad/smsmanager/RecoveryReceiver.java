package com.chad.smsmanager;
import android.content.*;
public class RecoveryReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent intent) { SafetyState.refreshNotification(c); }
}
