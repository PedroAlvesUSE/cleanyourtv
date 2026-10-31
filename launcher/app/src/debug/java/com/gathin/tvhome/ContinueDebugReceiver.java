package com.gathin.tvhome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Debug builds only: `adb shell content insert` cannot pass a value with a colon (an image address), so this
 * receiver adds a Continue watching item as the adb shell, through the same checks as the provider:
 * adb shell am broadcast -n com.gathin.tvhome/.ContinueDebugReceiver --es item_id ep1 --es title "A title"
 *     --es image_url https://picsum.photos/id/1015/960/540 --ef progress 0.6 [--es pkg some.app --ez approve true]
 */
public class ContinueDebugReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent i) {
        String pkg = i.getStringExtra("pkg");
        if (pkg == null) pkg = ContinueApps.SHELL;
        String[] why = new String[1];
        ContinueStore.Item item = ContinueStore.fromValues(pkg, i.getStringExtra(ContinueProvider.ITEM_ID),
                i.getStringExtra(ContinueProvider.TITLE), i.getStringExtra(ContinueProvider.SUBTITLE),
                i.getStringExtra(ContinueProvider.IMAGE_URL), i.getStringExtra(ContinueProvider.DEEP_LINK),
                (double) i.getFloatExtra(ContinueProvider.PROGRESS, 0f), null, System.currentTimeMillis(), why);
        if (item == null) {
            Log.w(ContinueProvider.TAG, "debug item rejected: " + why[0]);
            return;
        }
        PendingResult result = goAsync();
        boolean approve = i.getBooleanExtra("approve", false);
        String owner = pkg;
        new Thread(() -> {
            if (approve) ContinueApps.decide(ctx, owner, true);
            ContinueProvider.upsert(ctx.getApplicationContext(), item);
            result.finish();
        }).start();
    }
}
