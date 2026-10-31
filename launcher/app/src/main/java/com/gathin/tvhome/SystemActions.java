package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Linhas de ação das funções de sistema em Configurações (informações da TV, limpeza, backup, controle pelo
 * celular, controle parental). Cada ação confere a sua opção antes: uma função desligada não roda nada.
 */
final class SystemActions {
    static final String TV_INFO = "tv_info";
    static final String CLEAN_NOW = "clean_now";
    static final String BACKUP_OPEN = "backup_open";
    static final String REMOTE_INFO = "remote_info";
    static final String PARENTAL_PIN = "parental_pin_set";
    static final String PARENTAL_APPS = "parental_apps_pick";
    static final String PARENTAL_LIMIT = "parental_limit_pick";
    static final int REQUEST_BACKUP = 71;

    private SystemActions() { }

    static boolean handles(String key) {
        return TV_INFO.equals(key) || CLEAN_NOW.equals(key) || BACKUP_OPEN.equals(key) || REMOTE_INFO.equals(key)
                || PARENTAL_PIN.equals(key) || PARENTAL_APPS.equals(key) || PARENTAL_LIMIT.equals(key);
    }

    /** O texto cinza no fim da linha. */
    static String value(Context ctx, String key) {
        if (PARENTAL_PIN.equals(key)) return Parental.hasPin(ctx) ? "••••" : "";
        if (PARENTAL_APPS.equals(key)) return String.valueOf(Parental.apps(ctx).size());
        if (PARENTAL_LIMIT.equals(key)) return limitLabel(ctx, Parental.limit(ctx));
        return "";
    }

    private static String limitLabel(Context ctx, int minutes) {
        return minutes == 0 ? ctx.getString(R.string.off) : ctx.getString(R.string.minutes, minutes);
    }

    /** Ligar ou desligar o controle parental com PIN definido exige o PIN. Devolve true se tratou o clique. */
    static boolean guard(Activity a, String key, Runnable proceed) {
        if (!Parental.FEATURE.equals(key) || !Parental.hasPin(a)) return false;
        askPin(a, R.string.parental_enter, pin -> {
            if (Parental.verify(a, pin)) proceed.run();
            else Toast.makeText(a, R.string.parental_wrong, Toast.LENGTH_SHORT).show();
        });
        return true;
    }

    /** Depois de uma chave ligada ou desligada na tela. */
    static void toggled(Activity a, String key, boolean on, Runnable refresh) {
        if (Parental.FEATURE.equals(key) && on && !Parental.hasPin(a)) {
            run(a, PARENTAL_PIN, refresh);
        } else if (RemoteServer.FEATURE.equals(key) && on) {
            // O HomeKeyService liga o servidor ao ver a mudança; o diálogo espera por ele.
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (!a.isFinishing()) run(a, REMOTE_INFO, refresh);
            }, 800);
        }
    }

    static void run(Activity a, String key, Runnable refresh) {
        String needs = TV_INFO.equals(key) ? "tv_panel" : CLEAN_NOW.equals(key) ? "quick_clean"
                : BACKUP_OPEN.equals(key) ? "config_backup" : REMOTE_INFO.equals(key) ? RemoteServer.FEATURE : null;
        if (needs != null && !Config.feature(a, needs)) {
            Toast.makeText(a, R.string.turn_on_first, Toast.LENGTH_LONG).show();
            return;
        }
        if (TV_INFO.equals(key)) {
            int pad = (int) (12 * a.getResources().getDisplayMetrics().density);
            FrameLayout box = new FrameLayout(a);
            box.setPadding(pad, pad, pad, 0);
            box.addView(SystemInfo.panel(a, false));
            new AlertDialog.Builder(a).setTitle(R.string.set_tv_info).setView(box)
                    .setPositiveButton(android.R.string.ok, null).show();
        } else if (CLEAN_NOW.equals(key)) {
            Toast.makeText(a, R.string.cc_cleaning, Toast.LENGTH_SHORT).show();
            QuickClean.run(a, bytes -> Toast.makeText(a, bytes > 0 ? a.getString(R.string.cc_freed,
                    android.text.format.Formatter.formatShortFileSize(a, bytes)) : a.getString(R.string.cc_nothing),
                    Toast.LENGTH_LONG).show());
        } else if (BACKUP_OPEN.equals(key)) {
            a.startActivityForResult(new Intent(a, BackupActivity.class), REQUEST_BACKUP);
        } else if (REMOTE_INFO.equals(key)) {
            remoteInfo(a);
        } else if (Parental.hasPin(a)) {
            askPin(a, R.string.parental_enter, pin -> {
                if (Parental.verify(a, pin)) parental(a, key, refresh);
                else Toast.makeText(a, R.string.parental_wrong, Toast.LENGTH_SHORT).show();
            });
        } else {
            parental(a, key, refresh);
        }
    }

    private static void parental(Activity a, String key, Runnable refresh) {
        if (PARENTAL_PIN.equals(key)) {
            askPin(a, R.string.parental_new, pin -> {
                if (Parental.setPin(a, pin)) Toast.makeText(a, R.string.parental_saved, Toast.LENGTH_SHORT).show();
                refresh.run();
            });
        } else if (PARENTAL_APPS.equals(key)) {
            pickApps(a, refresh);
        } else if (PARENTAL_LIMIT.equals(key)) {
            String[] labels = new String[Parental.LIMITS.length];
            int checked = 0;
            for (int i = 0; i < labels.length; i++) {
                labels[i] = limitLabel(a, Parental.LIMITS[i]);
                if (Parental.LIMITS[i] == Parental.limit(a)) checked = i;
            }
            new AlertDialog.Builder(a).setTitle(R.string.set_parental_limit)
                    .setSingleChoiceItems(labels, checked, (d, which) -> {
                        Parental.setLimit(a, Parental.LIMITS[which]);
                        d.dismiss();
                        refresh.run();
                    }).show();
        }
    }

    private static void pickApps(Activity a, Runnable refresh) {
        PackageManager pm = a.getPackageManager();
        List<String> pkgs = new ArrayList<>(QuickClean.userApps(a));
        final List<String> labels = new ArrayList<>();
        for (String p : pkgs) {
            try {
                labels.add(pm.getApplicationLabel(pm.getApplicationInfo(p, 0)).toString());
            } catch (PackageManager.NameNotFoundException e) {
                labels.add(p);
            }
        }
        Integer[] order = new Integer[pkgs.size()];
        for (int i = 0; i < order.length; i++) order[i] = i;
        java.util.Arrays.sort(order, (x, y) -> labels.get(x).compareToIgnoreCase(labels.get(y)));
        String[] names = new String[order.length];
        boolean[] on = new boolean[order.length];
        Set<String> chosen = Parental.apps(a);
        for (int i = 0; i < order.length; i++) {
            names[i] = labels.get(order[i]);
            on[i] = chosen.contains(pkgs.get(order[i]));
        }
        Set<String> result = new HashSet<>(chosen);
        new AlertDialog.Builder(a).setTitle(R.string.set_parental_apps)
                .setMultiChoiceItems(names, on, (d, which, checked) -> {
                    String p = pkgs.get(order[which]);
                    if (checked) result.add(p);
                    else result.remove(p);
                })
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    Parental.setApps(a, result);
                    refresh.run();
                })
                .setNegativeButton(android.R.string.cancel, null).show();
    }

    private static void remoteInfo(Activity a) {
        String url = RemoteServer.url();
        String message = url == null ? a.getString(R.string.remote_off)
                : a.getString(R.string.remote_info, url, RemoteServer.pin(a));
        new AlertDialog.Builder(a).setTitle(R.string.set_phone).setMessage(message)
                .setPositiveButton(android.R.string.ok, null)
                .setNeutralButton(R.string.remote_new_pin, (d, w) -> {
                    RemoteServer.renewPin(a);
                    remoteInfo(a);
                }).show();
    }

    /** Pede um PIN de 4 dígitos; só chama {@code done} com 4 dígitos. */
    static void askPin(Activity a, int title, java.util.function.Consumer<String> done) {
        EditText input = new EditText(a);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(4)});
        input.setGravity(android.view.Gravity.CENTER);
        input.setTextSize(28);
        new AlertDialog.Builder(a).setTitle(title).setView(input)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    String pin = input.getText().toString();
                    if (PinHash.valid(pin)) done.accept(pin);
                    else Toast.makeText(a, R.string.parental_four, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(android.R.string.cancel, null).show();
    }
}
