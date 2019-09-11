package com.andreasgift.targetapp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootCompletedIntentReceiver extends BroadcastReceiver {

    @Override
        public void onReceive(Context context, Intent intent) {
            if ("android.intent.action.BOOT_COMPLETED".equals(intent.getAction())) {
                Intent start = new Intent(context, TrackingActivity.class);
                start.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(start);
            }
        }
}
