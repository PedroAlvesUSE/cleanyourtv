package com.gathin.tvhome;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Handler;
import android.os.Looper;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Limpeza rápida (quick_clean): encerra os processos em segundo plano dos apps e mede a memória liberada. */
final class QuickClean {
    private QuickClean() { }

    interface Done {
        void freed(long bytes);
    }

    /** Roda fora da thread principal e chama {@code done} na principal com os bytes liberados (0 se nada mudou). */
    static void run(Context ctx, Done done) {
        Context app = ctx.getApplicationContext();
        new Thread(() -> {
            long before = SystemInfo.freeMemory(app);
            ActivityManager am = (ActivityManager) app.getSystemService(Context.ACTIVITY_SERVICE);
            for (String pkg : userApps(app)) am.killBackgroundProcesses(pkg);
            try {
                Thread.sleep(800);
            } catch (InterruptedException ignored) {
            }
            long freed = Math.max(0, SystemInfo.freeMemory(app) - before);
            new Handler(Looper.getMainLooper()).post(() -> done.freed(freed));
        }, "quick-clean").start();
    }

    /** Pacotes com entrada na TV ou no celular, sem o launcher. */
    static Set<String> userApps(Context ctx) {
        PackageManager pm = ctx.getPackageManager();
        Set<String> out = new HashSet<>();
        for (String category : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            List<ResolveInfo> list = pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category), 0);
            for (ResolveInfo r : list) {
                String pkg = r.activityInfo.packageName;
                if (!pkg.equals(ctx.getPackageName()) && (r.activityInfo.applicationInfo.flags & ApplicationInfo.FLAG_PERSISTENT) == 0) {
                    out.add(pkg);
                }
            }
        }
        return out;
    }
}
