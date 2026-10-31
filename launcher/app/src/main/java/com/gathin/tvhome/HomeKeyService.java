package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Sends the remote's Home button to this launcher on remotes that emit KEYCODE_ALL_APPS for Home
 * (Xiaomi sticks and boxes). Android TV 9 routes that key only to the system-image launcher, so once
 * the stock launcher is disabled the button does nothing. Every other key passes through untouched.
 *
 * On Fire TV the stock launcher (com.amazon.tv.launcher) cannot be disabled and keeps answering the Home
 * button, so this service also takes KEYCODE_HOME there and sends the user back here whenever the
 * Amazon launcher comes to the front.
 *
 * Funções de sistema que passam por este serviço (cada uma só roda com a sua opção ligada): mic_tizentube
 * (teclas de microfone abrem a busca por voz no TizenTube), quick_menu (Home longo abre o painel rápido),
 * parental (tela de PIN sobre o app protegido), phone_remote (servidor na rede local, ações de controle) e
 * sleep_timer (bloqueio da tela ao expirar).
 *
 * Enable over adb:
 *   settings put secure enabled_accessibility_services com.gathin.tvhome/.HomeKeyService
 *   settings put secure accessibility_enabled 1
 */
public class HomeKeyService extends AccessibilityService {

    /** Fire OS home screen that can't be disabled; when it comes to the front, this launcher replaces it. */
    private static final String FIRE_TV_LAUNCHER = "com.amazon.tv.launcher";
    private static final ComponentName FIRE_TV_SETTINGS =
            new ComponentName("com.amazon.tv.launcher", "com.amazon.tv.launcher.ui.MainSettingsActivity");

    /** Remote buttons that should open Settings (config.xml, settings_key_scancodes and settings_key_keycodes). */
    private int[] settingsScancodes = new int[0];
    private int[] settingsKeycodes = new int[0];
    private boolean settingsPanel;
    /** app_keys, the same map InputKeyService reads, for setups that run only this service. */
    private Map<Integer, String> appKeys = Collections.emptyMap();
    private boolean fireTv;

    /** Botões do controle pelo celular aceitos em {@link #remoteKey}. */
    static final Set<String> REMOTE_KEYS = new HashSet<>(Arrays.asList(
            "up", "down", "left", "right", "ok", "back", "home", "volup", "voldown", "mute"));
    private static final long HOME_LONG_MS = 600;
    private static final long USAGE_REFRESH_MS = 20_000;
    private static volatile HomeKeyService instance;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean micOn;
    private int[] micKeycodes = MicSearch.DEFAULT_KEYCODES;
    private boolean quickMenu;
    private boolean homeLong;
    private boolean parentalOn;
    private Set<String> protectedApps = Collections.emptySet();
    private int limitMinutes;
    private long usedMs;
    private long usageAt;
    private final Runnable openMenu = () -> {
        homeLong = true;
        startActivity(new Intent(this, QuickSettingsActivity.class)
                .putExtra(QuickSettingsActivity.EXTRA_CONTROL_CENTER, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    };
    private final Runnable refreshLater = this::refresh;
    private final SharedPreferences.OnSharedPreferenceChangeListener choicesListener = (p, key) -> {
        handler.removeCallbacks(refreshLater);
        handler.postDelayed(refreshLater, 300);
    };

    static HomeKeyService instance() {
        return instance;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE).registerOnSharedPreferenceChangeListener(choicesListener);
        refresh();
        settingsScancodes = getResources().getIntArray(R.array.settings_key_scancodes);
        // Keycodes and the panel choice can also come from a theme; theme.sh restarts this service.
        Config config = Config.load(this);
        settingsKeycodes = config.settingsKeycodes;
        settingsPanel = config.settingsKeyPanel;
        appKeys = RemoteKeys.parseAppKeys(config.appKeys);
        fireTv = getPackageManager().resolveActivity(new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME).setPackage(FIRE_TV_LAUNCHER), 0) != null;
    }

    /** Lê de novo as opções de sistema; roda ao conectar e depois de qualquer mudança em launcher_settings. */
    private void refresh() {
        Config config = Config.load(this);
        appKeys = RemoteKeys.parseAppKeys(config.appKeys);
        micOn = config.feature("mic_tizentube");
        micKeycodes = micOn ? Config.micKeycodes(this) : MicSearch.DEFAULT_KEYCODES;
        quickMenu = config.feature("quick_menu");
        parentalOn = config.feature(Parental.FEATURE) && Parental.hasPin(this);
        protectedApps = parentalOn ? Parental.apps(this) : Collections.emptySet();
        limitMinutes = parentalOn ? Parental.limit(this) : 0;
        usageAt = 0;
        if (config.feature(RemoteServer.FEATURE)) RemoteServer.start(this);
        else RemoteServer.stop();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        instance = null;
        handler.removeCallbacksAndMessages(null);
        getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(choicesListener);
        RemoteServer.stop();
        return super.onUnbind(intent);
    }

    private boolean isSettingsKey(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_SETTINGS) return true;
        for (int code : settingsScancodes) if (event.getScanCode() == code) return true;
        for (int code : settingsKeycodes) if (event.getKeyCode() == code) return true;
        return false;
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        // The stock launcher handled the remote's Settings button; with it disabled the button does nothing.
        if (isSettingsKey(event)) {
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                Intent open = settingsPanel ? new Intent(this, QuickSettingsActivity.class)
                        : fireTv ? new Intent().setComponent(FIRE_TV_SETTINGS) : new Intent(Settings.ACTION_SETTINGS);
                startActivity(open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            }
            return true;
        }
        String app = appKeys.get(event.getKeyCode());
        if (app != null) {
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) RemoteKeys.launch(this, app);
            return true;
        }
        if (micOn && MicSearch.contains(micKeycodes, event.getKeyCode())) {
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                startActivity(new Intent(this, MicSearchActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_NO_ANIMATION));
            }
            return true;
        }
        if (quickMenu && event.getKeyCode() == KeyEvent.KEYCODE_HOME) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (event.getRepeatCount() == 0) {
                    homeLong = false;
                    handler.postDelayed(openMenu, HOME_LONG_MS);
                }
            } else {
                handler.removeCallbacks(openMenu);
                if (!homeLong && !event.isCanceled()) goHome();
                homeLong = false;
            }
            return true;
        }
        boolean fireHome = event.getKeyCode() == KeyEvent.KEYCODE_HOME && fireTv;
        if (event.getKeyCode() != KeyEvent.KEYCODE_ALL_APPS && !fireHome) return false;
        if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) goHome();
        return true;
    }

    private void goHome() {
        Intent home = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .setPackage(getPackageName())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        startActivity(home);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Fire OS still opens its own home screen (after boot, from its menus); swap it for this one. Its settings
        // screens are a different class and stay open.
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && FIRE_TV_LAUNCHER.contentEquals(String.valueOf(event.getPackageName()))
                && String.valueOf(event.getClassName()).contains("HomeActivity")) {
            goHome();
        }
        if (parentalOn && event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            checkParental(String.valueOf(event.getPackageName()));
        }
    }

    // ---- controle parental ------------------------------------------------------------------------

    private void checkParental(String pkg) {
        if (pkg.equals(getPackageName()) || Parental.isShowing()) return;
        // Só apps com entrada de lançamento: ignora teclado, barra do sistema e diálogos.
        if (getPackageManager().getLaunchIntentForPackage(pkg) == null
                && getPackageManager().getLeanbackLaunchIntentForPackage(pkg) == null) return;
        long now = System.currentTimeMillis();
        boolean locked = protectedApps.contains(pkg);
        if (limitMinutes > 0 && now - usageAt > USAGE_REFRESH_MS) {
            usedMs = Parental.usedToday(this);
            usageAt = now;
        }
        boolean over = Parental.overLimit(usedMs, limitMinutes);
        long until = Math.max(Parental.unlockedUntil(pkg), over ? Parental.unlockedUntil(Parental.ANY) : 0);
        if (Parental.needsPin(locked, until, now, over)) {
            startActivity(ParentalActivity.intent(this, pkg, over));
        }
    }

    // ---- temporizador -------------------------------------------------------------------------------

    /** Coloca a TV em espera ao fim do temporizador. Sem o serviço ativo, só mostra o relógio de tela cheia. */
    static void sleepNow(Context ctx) {
        HomeKeyService s = instance;
        boolean locked = false;
        if (s != null && Build.VERSION.SDK_INT >= 28) locked = s.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
        if (!locked) {
            ctx.startActivity(new Intent(ctx, SaverNowActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
    }

    // ---- controle pelo celular -----------------------------------------------------------------------

    /** Executa um botão do celular na thread principal. D-pad e OK usam a navegação de foco dos nós de acessibilidade. */
    boolean remoteKey(String key) {
        AudioManager audio = (AudioManager) getSystemService(AUDIO_SERVICE);
        switch (key) {
            case "back": return performGlobalAction(GLOBAL_ACTION_BACK);
            case "home": return performGlobalAction(GLOBAL_ACTION_HOME);
            case "volup":
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI);
                return true;
            case "voldown":
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI);
                return true;
            case "mute":
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI);
                return true;
            case "up": return dpad(View.FOCUS_UP, 16);
            case "down": return dpad(View.FOCUS_DOWN, 17);
            case "left": return dpad(View.FOCUS_LEFT, 18);
            case "right": return dpad(View.FOCUS_RIGHT, 19);
            case "ok": return click();
            default: return false;
        }
    }

    /** Android 13+ tem as ações de D-pad prontas (GLOBAL_ACTION_DPAD_*). Antes, procura o próximo nó com foco. */
    private boolean dpad(int direction, int globalAction) {
        if (Build.VERSION.SDK_INT >= 33 && performGlobalAction(globalAction)) return true;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;
        AccessibilityNodeInfo current = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        AccessibilityNodeInfo next = current == null ? firstFocusable(root) : current.focusSearch(direction);
        return next != null && next.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
    }

    private static AccessibilityNodeInfo firstFocusable(AccessibilityNodeInfo node) {
        if (node.isFocusable() && node.isVisibleToUser()) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) continue;
            AccessibilityNodeInfo found = firstFocusable(child);
            if (found != null) return found;
        }
        return null;
    }

    private boolean click() {
        if (Build.VERSION.SDK_INT >= 33 && performGlobalAction(20)) return true;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        AccessibilityNodeInfo node = root == null ? null : root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        while (node != null && !node.isClickable()) node = node.getParent();
        return node != null && node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
    }

    /** Escreve no campo de texto em foco (o texto do campo é trocado pelo recebido). */
    boolean remoteText(String text) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        AccessibilityNodeInfo node = root == null ? null : root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (node == null || !node.isEditable()) return false;
        Bundle args = new Bundle();
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
    }

    @Override
    public void onInterrupt() {
    }
}
