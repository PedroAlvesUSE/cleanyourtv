package com.gathin.tvhome;

import android.app.ActivityManager;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.LinearLayout;
import android.widget.TextView;

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

    static String launcherVersion(Context ctx) {
        try {
            String v = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0).versionName;
            return v == null ? "" : v;
        } catch (Exception ignored) {
            return "";
        }
    }

    /**
     * Lista agrupada no estilo do tvOS: um bloco de vidro com cantos de 16 dp, rótulo à esquerda (#F5F5F7),
     * valor à direita (#A1A1A6) e separadores de 1 px recuados até o texto. Com {@code focusable}, o bloco
     * recebe o foco (para o ScrollView chegar até ele) e acende como as linhas do painel rápido.
     */
    static View panel(Context ctx, boolean focusable) {
        float dp = ctx.getResources().getDisplayMetrics().density;
        String[][] rows = {
                {ctx.getString(R.string.info_storage), storage(ctx)},
                {ctx.getString(R.string.info_memory), memory(ctx)},
                {ctx.getString(R.string.info_network), network(ctx)},
                {ctx.getString(R.string.info_android), Build.VERSION.RELEASE},
                {ctx.getString(R.string.info_launcher), launcherVersion(ctx)}};
        LinearLayout group = new LinearLayout(ctx);
        group.setOrientation(LinearLayout.VERTICAL);
        StateListDrawable bg = new StateListDrawable();
        if (focusable) bg.addState(new int[]{android.R.attr.state_focused}, glass(0x33FFFFFF, dp));
        bg.addState(new int[0], glass(0x1AFFFFFF, dp));
        group.setBackground(bg);
        group.setClipToOutline(true);
        group.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        group.setFocusable(focusable);
        int pad = (int) (14 * dp);
        for (int i = 0; i < rows.length; i++) {
            if (i > 0) {
                View line = new View(ctx);
                line.setBackgroundColor(0x1FFFFFFF);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1);
                lp.setMarginStart(pad);
                group.addView(line, lp);
            }
            LinearLayout row = new LinearLayout(ctx);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(pad, 0, pad, 0);
            TextView label = infoText(ctx, 16, 0xFFF5F5F7, R.font.outfit_medium);
            label.setText(rows[i][0]);
            row.addView(label);
            TextView value = infoText(ctx, 15, 0xFFA1A1A6, R.font.outfit_regular);
            value.setText(rows[i][1]);
            value.setGravity(Gravity.END);
            value.setEllipsize(TextUtils.TruncateAt.END);
            value.setPadding((int) (12 * dp), 0, 0, 0);
            row.addView(value, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            group.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (44 * dp)));
        }
        return group;
    }

    private static GradientDrawable glass(int color, float dp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(16 * dp);
        g.setStroke(Math.max(1, (int) dp), 0x1FFFFFFF);
        return g;
    }

    private static TextView infoText(Context ctx, int sp, int color, int font) {
        TextView t = new TextView(ctx);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setSingleLine(true);
        t.setTypeface(ctx.getResources().getFont(font));
        return t;
    }
}
