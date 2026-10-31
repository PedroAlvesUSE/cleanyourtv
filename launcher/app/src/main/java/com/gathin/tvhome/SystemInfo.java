package com.gathin.tvhome;

import android.app.ActivityManager;
import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.text.format.Formatter;

/** Painel da TV (tv_panel): armazenamento, memória, rede e versões, em texto curto. */
final class SystemInfo {
    private SystemInfo() { }

    static String storage(Context ctx) {
        StatFs fs = new StatFs(Environment.getDataDirectory().getPath());
        return ctx.getString(R.string.info_free_of, Formatter.formatShortFileSize(ctx, fs.getAvailableBytes()),
                Formatter.formatShortFileSize(ctx, fs.getTotalBytes()));
    }

    static String memory(Context ctx) {
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        ((ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE)).getMemoryInfo(mi);
        return ctx.getString(R.string.info_free_of, Formatter.formatShortFileSize(ctx, mi.availMem),
                Formatter.formatShortFileSize(ctx, mi.totalMem));
    }

    static long freeMemory(Context ctx) {
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        ((ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE)).getMemoryInfo(mi);
        return mi.availMem;
    }

    /** Nome da rede Wi-Fi, ou "" se o Android não o informa (precisa da permissão de localização). */
    @SuppressWarnings("deprecation")
    static String ssid(Context ctx) {
        try {
            WifiManager wm = (WifiManager) ctx.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            WifiInfo info = wm == null ? null : wm.getConnectionInfo();
            String ssid = info == null ? null : info.getSSID();
            if (ssid == null) return "";
            ssid = ssid.replace("\"", "").trim();
            return ssid.contains("unknown") || ssid.equals("0x") ? "" : ssid;
        } catch (RuntimeException e) {
            return "";
        }
    }

    static String network(Context ctx) {
        String ssid = ssid(ctx);
        String ip = LanServer.localIp();
        if (ip == null) return ctx.getString(R.string.offline);
        return ssid.isEmpty() ? ip : ssid + " · " + ip;
    }

    static String versions(Context ctx) {
        String launcher = "";
        try {
            launcher = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0).versionName;
        } catch (Exception ignored) {
        }
        return ctx.getString(R.string.info_versions, Build.VERSION.RELEASE, launcher);
    }

    /** As quatro linhas do painel, uma por linha. */
    static String summary(Context ctx) {
        return ctx.getString(R.string.info_storage) + ": " + storage(ctx) + "\n"
                + ctx.getString(R.string.info_memory) + ": " + memory(ctx) + "\n"
                + ctx.getString(R.string.info_network) + ": " + network(ctx) + "\n"
                + versions(ctx);
    }
}
