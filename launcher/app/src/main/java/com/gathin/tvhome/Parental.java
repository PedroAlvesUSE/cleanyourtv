package com.gathin.tvhome;

import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Controle parental (parental): um PIN de 4 dígitos (guardado como hash com sal) para os apps escolhidos e um
 * limite diário de uso da TV em minutos. O HomeKeyService vê o app protegido chegar à frente e abre a tela de
 * PIN por cima. Sem root isso não é à prova de tudo: outro caminho (a Play Store, o ADB, um serviço desligado)
 * ainda abre o app, e quem sabe desligar o serviço de acessibilidade desfaz o bloqueio.
 */
final class Parental {
    static final String FEATURE = "parental";
    static final String PIN_KEY = "parental_pin";
    static final String APPS_KEY = "parental_apps";
    static final String LIMIT_KEY = "parental_limit";
    static final long UNLOCK_MS = 30 * 60_000L;
    /** Minutos por dia; 0 desliga o limite. */
    static final int[] LIMITS = {0, 30, 60, 120, 180, 240};
    /** Chave de desbloqueio que vale para qualquer app (depois do limite diário). */
    static final String ANY = "*";

    private static final Map<String, Long> UNLOCKED = new HashMap<>();
    private static boolean showing;

    private Parental() { }

    /** Pede o PIN quando o app é protegido, ou o limite do dia acabou, e o desbloqueio de 30 minutos não vale. */
    static boolean needsPin(boolean protectedApp, long unlockedUntil, long now, boolean overLimit) {
        return (protectedApp || overLimit) && now >= unlockedUntil;
    }

    static boolean overLimit(long usedMs, int limitMinutes) {
        return limitMinutes > 0 && usedMs >= limitMinutes * 60_000L;
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
    }

    static boolean hasPin(Context ctx) {
        return prefs(ctx).getString(PIN_KEY, null) != null;
    }

    static boolean setPin(Context ctx, String pin) {
        if (!PinHash.valid(pin)) return false;
        prefs(ctx).edit().putString(PIN_KEY, PinHash.encode(pin)).apply();
        return true;
    }

    static boolean verify(Context ctx, String pin) {
        return PinHash.verify(prefs(ctx).getString(PIN_KEY, null), pin);
    }

    static Set<String> apps(Context ctx) {
        return new HashSet<>(prefs(ctx).getStringSet(APPS_KEY, new HashSet<>()));
    }

    static void setApps(Context ctx, Set<String> apps) {
        prefs(ctx).edit().putStringSet(APPS_KEY, new HashSet<>(apps)).apply();
    }

    static int limit(Context ctx) {
        return prefs(ctx).getInt(LIMIT_KEY, 0);
    }

    static void setLimit(Context ctx, int minutes) {
        prefs(ctx).edit().putInt(LIMIT_KEY, minutes).apply();
    }

    static synchronized long unlockedUntil(String key) {
        Long t = UNLOCKED.get(key);
        return t == null ? 0 : t;
    }

    static synchronized void unlock(String pkg, boolean any, long now) {
        UNLOCKED.put(pkg, now + UNLOCK_MS);
        if (any) UNLOCKED.put(ANY, now + UNLOCK_MS);
    }

    static synchronized boolean isShowing() {
        return showing;
    }

    static synchronized void setShowing(boolean value) {
        showing = value;
    }

    /** Uso de hoje, em ms, de todos os apps menos o launcher. 0 se a permissão de uso não foi concedida. */
    static long usedToday(Context ctx) {
        try {
            UsageStatsManager usm = (UsageStatsManager) ctx.getSystemService(Context.USAGE_STATS_SERVICE);
            Calendar start = Calendar.getInstance();
            start.set(Calendar.HOUR_OF_DAY, 0);
            start.set(Calendar.MINUTE, 0);
            start.set(Calendar.SECOND, 0);
            start.set(Calendar.MILLISECOND, 0);
            Map<String, UsageStats> all = usm.queryAndAggregateUsageStats(start.getTimeInMillis(), System.currentTimeMillis());
            long total = 0;
            for (Map.Entry<String, UsageStats> e : all.entrySet()) {
                if (!e.getKey().equals(ctx.getPackageName())) total += e.getValue().getTotalTimeInForeground();
            }
            return total;
        } catch (RuntimeException e) {
            return 0;
        }
    }

    /** Volta para a tela inicial do launcher (quando o PIN é cancelado). */
    static void goHome(Context ctx) {
        ctx.startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .setPackage(ctx.getPackageName()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }
}
