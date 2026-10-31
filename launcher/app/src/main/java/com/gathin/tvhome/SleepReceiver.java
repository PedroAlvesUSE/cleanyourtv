package com.gathin.tvhome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Alarme do temporizador: pede ao serviço de acessibilidade para colocar a TV em espera. */
public final class SleepReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        SleepTimer.set(context, 0);
        if (!Config.feature(context, SleepTimer.FEATURE)) return;
        HomeKeyService.sleepNow(context);
    }
}
