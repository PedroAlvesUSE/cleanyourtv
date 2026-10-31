package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;

import java.util.Map;
import java.util.TreeMap;

/**
 * Which apps may fill Continue watching through the provider. An app that writes is listed as waiting; the TV
 * owner allows or blocks it in Settings. The decision is pinned to the app's signing certificate, as for module
 * cards (Cards.digest): an app signed with another key waits again, and its items are dropped.
 */
final class ContinueApps {
    static final String PREFS = "continue_apps";
    static final String SHOW = "show";
    static final String HIDE = "hide";
    static final String WAIT = "wait";
    /** Debug builds only: the adb shell is allowed, so `adb shell content insert` can test the row. */
    static final String SHELL = "com.android.shell";

    private ContinueApps() {
    }

    /** The stored value: "decision:digest". */
    static String value(String decision, String digest) {
        return decision + ':' + digest;
    }

    /** The decision a stored value gives for the app's current signer: SHOW, HIDE or WAIT (also when unknown). */
    static String decision(String stored, String digest) {
        if (stored == null || digest == null) return WAIT;
        int colon = stored.indexOf(':');
        if (colon <= 0 || !stored.substring(colon + 1).equals(digest)) return WAIT;
        String d = stored.substring(0, colon);
        return SHOW.equals(d) || HIDE.equals(d) ? d : WAIT;
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static boolean debugShell(Context ctx, String pkg) {
        return SHELL.equals(pkg) && (ctx.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    }

    static String digest(Context ctx, String pkg) {
        try {
            return Cards.digest(ctx.getPackageManager(), pkg);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Whether the app may write now. An unknown app, or one with a new signer, is listed as waiting.
     */
    static boolean allowed(Context ctx, String pkg) {
        if (debugShell(ctx, pkg)) return true;
        String digest = digest(ctx, pkg);
        if (digest == null) return false;
        SharedPreferences p = prefs(ctx);
        String stored = p.getString(pkg, null);
        String d = decision(stored, digest);
        if (WAIT.equals(d) && !value(WAIT, digest).equals(stored)) p.edit().putString(pkg, value(WAIT, digest)).apply();
        return SHOW.equals(d);
    }

    /** Whether the Home row may show the app's provider items (no write to the list). */
    static boolean shown(Context ctx, String pkg) {
        if (debugShell(ctx, pkg)) return true;
        String stored = prefs(ctx).getString(pkg, null);
        return stored != null && stored.startsWith(SHOW + ':') && SHOW.equals(decision(stored, digest(ctx, pkg)));
    }

    static void decide(Context ctx, String pkg, boolean show) {
        String digest = digest(ctx, pkg);
        if (digest == null) {
            prefs(ctx).edit().remove(pkg).apply();
            return;
        }
        prefs(ctx).edit().putString(pkg, value(show ? SHOW : HIDE, digest)).apply();
    }

    /** Every listed app and its decision for the current signer, by package name. */
    static Map<String, String> all(Context ctx) {
        Map<String, String> out = new TreeMap<>();
        for (Map.Entry<String, ?> e : prefs(ctx).getAll().entrySet()) {
            if (!(e.getValue() instanceof String)) continue;
            out.put(e.getKey(), decision((String) e.getValue(), digest(ctx, e.getKey())));
        }
        return out;
    }

    static int waiting(Context ctx) {
        int n = 0;
        for (String d : all(ctx).values()) if (WAIT.equals(d)) n++;
        return n;
    }
}
