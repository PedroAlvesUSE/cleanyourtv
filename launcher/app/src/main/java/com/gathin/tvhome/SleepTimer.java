package com.gathin.tvhome;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

/**
 * Temporizador para desligar (sleep_timer): desligado, 1 (para teste), 15, 30, 60 ou 90 minutos. O prazo fica em
 * launcher_settings (chave sleep_deadline, em ms de relógio) e um alarme chama {@link SleepReceiver}.
 * Ao expirar, o serviço de acessibilidade bloqueia a tela (ver HomeKeyService.sleepNow).
 */
final class SleepTimer {
    static final String FEATURE = "sleep_timer";
    static final String DEADLINE_KEY = "sleep_deadline";
    static final String MINUTES_KEY = "sleep_minutes";
    static final int[] STEPS = {0, 1, 15, 30, 60, 90};

    private SleepTimer() { }

    /** Próximo passo do ciclo desligado, 1, 15, 30, 60, 90, desligado. Valor fora da lista volta para 1. */
    static int next(int minutes) {
        for (int i = 0; i < STEPS.length; i++) if (STEPS[i] == minutes) return STEPS[(i + 1) % STEPS.length];
        return STEPS[1];
    }

    static long remainingMs(long deadline, long now) {
        return deadline <= 0 ? 0 : Math.max(0, deadline - now);
    }

    /** "1h 05min", "42min" ou "<1min". */
    static String format(long remainingMs) {
        long min = (remainingMs + 59_999) / 60_000;
        if (min <= 0) return "<1min";
        if (min < 60) return min + "min";
        return (min / 60) + "h " + String.format("%02d", min % 60) + "min";
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
    }

    private static PendingIntent alarm(Context ctx) {
        return PendingIntent.getBroadcast(ctx, 7, new Intent(ctx, SleepReceiver.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** Prazo salvo em ms (0 sem temporizador). Um prazo que já passou conta como sem temporizador. */
    static long deadline(Context ctx) {
        long d = prefs(ctx).getLong(DEADLINE_KEY, 0);
        return d > System.currentTimeMillis() ? d : 0;
    }

    /** Passo escolhido (1, 15, 30, 60, 90) enquanto o temporizador corre; 0 sem temporizador. */
    static int minutes(Context ctx) {
        return deadline(ctx) > 0 ? prefs(ctx).getInt(MINUTES_KEY, 0) : 0;
    }

    static void set(Context ctx, int minutes) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (minutes <= 0) {
            prefs(ctx).edit().remove(DEADLINE_KEY).remove(MINUTES_KEY).apply();
            am.cancel(alarm(ctx));
            return;
        }
        long at = System.currentTimeMillis() + minutes * 60_000L;
        prefs(ctx).edit().putLong(DEADLINE_KEY, at).putInt(MINUTES_KEY, minutes).apply();
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, alarm(ctx));
    }
}
