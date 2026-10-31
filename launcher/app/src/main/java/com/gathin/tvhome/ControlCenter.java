package com.gathin.tvhome;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

/**
 * Painel rápido do Home longo (quick_menu), no estilo da Central de Controle do tvOS: painel de vidro
 * translúcido no lado direito, linhas com o foco da tela de Configurações, entrada deslizando da direita em
 * 250 ms. Volume, entradas HDMI, temporizador (sleep_timer), Wi-Fi, protetor de tela, limpeza (quick_clean) e
 * informações da TV (tv_panel). Cada linha só existe com a sua opção ligada.
 */
final class ControlCenter {
    private static final int WIDTH_DP = 380;
    // Cantos concêntricos: painel de 28 dp com recuo de 12 dp, então as linhas e o bloco de informações têm 16 dp.
    private static final int PANEL_RADIUS_DP = 28;
    private static final int INSET_DP = 12;
    private static final int ITEM_RADIUS_DP = PANEL_RADIUS_DP - INSET_DP;
    /** Fundo do item em foco: opaco (o tom do vidro #33FFFFFF sobre o painel), para cobrir os vizinhos e projetar sombra. */
    static final int FOCUS_FILL = 0xFF48484C;
    /** Altura do item em foco sobre os vizinhos, em dp. */
    static final float FOCUS_LIFT_DP = 4;
    private static final int TEXT = 0xFFF5F5F7;
    private static final int TEXT_2 = 0xFFA1A1A6;

    private final Activity a;
    private final float dp;
    private final boolean motion;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final AudioManager audio;
    private LinearLayout list;
    private View panel;
    private TextView volumeStatus;
    private TextView sleepStatus;
    private TextView cleanStatus;
    private boolean closing;

    private ControlCenter(Activity a) {
        this.a = a;
        this.dp = a.getResources().getDisplayMetrics().density;
        this.motion = android.animation.ValueAnimator.areAnimatorsEnabled();
        this.audio = (AudioManager) a.getSystemService(Context.AUDIO_SERVICE);
    }

    static ControlCenter show(Activity a) {
        ControlCenter c = new ControlCenter(a);
        c.build();
        return c;
    }

    private GradientDrawable round(int color, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        return g;
    }

    private void build() {
        FrameLayout root = new FrameLayout(a);
        root.setBackgroundColor(0x66000000);
        root.setOnClickListener(v -> close());
        ScrollView scroll = new ScrollView(a);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        // A borda que esmaece também faz o ScrollView parar o item em foco a 24 dp da borda, e não colado nela.
        scroll.setVerticalFadingEdgeEnabled(true);
        scroll.setFadingEdgeLength((int) (24 * dp));
        list = new LinearLayout(a);
        list.setOrientation(LinearLayout.VERTICAL);
        // O item em foco cresce e sai por cima dos vizinhos e do recuo; só o contorno do painel recorta.
        list.setClipChildren(false);
        list.setClipToPadding(false);
        int inset = (int) (INSET_DP * dp);
        list.setPadding(inset, inset, inset, inset);
        scroll.addView(list);
        GradientDrawable glass = round(0xD91C1C1E, PANEL_RADIUS_DP * dp);
        glass.setStroke(Math.max(1, (int) dp), 0x1FFFFFFF);
        scroll.setBackground(glass);
        scroll.setClipToOutline(true);
        scroll.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
        scroll.setElevation(16 * dp);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams((int) (WIDTH_DP * dp), ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.END);
        lp.setMargins(0, (int) (28 * dp), (int) (28 * dp), (int) (28 * dp));
        root.addView(scroll, lp);
        panel = scroll;

        TextView title = text(24, TEXT);
        title.setText(R.string.cc_title);
        title.setPadding((int) (14 * dp), (int) (4 * dp), 0, (int) (10 * dp));
        list.addView(title);

        // Volume: esquerda e direita mudam, OK silencia.
        View volume = row(R.drawable.ic_av, R.string.cc_volume);
        volumeStatus = status(volume);
        volume.setOnClickListener(v -> {
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, 0);
            showVolume();
        });
        volume.setOnKeyListener((v, code, e) -> {
            if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                        code == KeyEvent.KEYCODE_DPAD_LEFT ? AudioManager.ADJUST_LOWER : AudioManager.ADJUST_RAISE, 0);
                showVolume();
                return true;
            }
            return false;
        });
        showVolume();

        View inputs = row(R.drawable.ic_input, R.string.cc_inputs);
        status(inputs).setVisibility(View.GONE);
        inputs.setOnClickListener(v -> open(new Intent("com.android.tv.action.VIEW_INPUTS")));

        if (Config.feature(a, SleepTimer.FEATURE)) {
            View sleep = row(R.drawable.ic_moon, R.string.cc_sleep);
            sleepStatus = status(sleep);
            sleep.setOnClickListener(v -> {
                SleepTimer.set(a, SleepTimer.next(SleepTimer.minutes(a)));
                showSleep();
            });
            showSleep();
        }

        View wifi = row(R.drawable.ic_wifi, R.string.cc_wifi);
        TextView wifiStatus = status(wifi);
        boolean panelOn = Config.feature(a, "tv_panel");
        if (panelOn) wifiStatus.setText(SystemInfo.network(a));
        else wifiStatus.setVisibility(View.GONE);
        wifi.setOnClickListener(v -> open(new Intent(Settings.ACTION_WIFI_SETTINGS)));

        View saver = row(R.drawable.ic_livetv, R.string.cc_saver);
        status(saver).setVisibility(View.GONE);
        saver.setOnClickListener(v -> open(new Intent(a, SaverNowActivity.class)));

        if (Config.feature(a, "quick_clean")) {
            View clean = row(R.drawable.ic_apps, R.string.cc_clean);
            cleanStatus = status(clean);
            clean.setOnClickListener(v -> {
                cleanStatus.setText(R.string.cc_cleaning);
                QuickClean.run(a, bytes -> {
                    if (a.isFinishing()) return;
                    cleanStatus.setText(bytes > 0 ? a.getString(R.string.cc_freed,
                            android.text.format.Formatter.formatShortFileSize(a, bytes)) : a.getString(R.string.cc_nothing));
                });
            });
        }

        if (panelOn) {
            TextView header = text(14, TEXT_2);
            header.setText(R.string.set_tv_info);
            header.setPadding((int) (14 * dp), (int) (18 * dp), 0, (int) (8 * dp));
            list.addView(header);
            ViewGroup info = (ViewGroup) SystemInfo.panel(a, true);
            for (int i = 0; i < info.getChildCount(); i++) {
                if (info.getChildAt(i).isFocusable()) focusScale(info.getChildAt(i));
            }
            list.addView(info, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        list.getChildAt(1).requestFocus();

        a.setContentView(root);
        if (motion) {
            panel.setTranslationX((WIDTH_DP + 28) * dp);
            panel.animate().translationX(0).setDuration(250).setInterpolator(new DecelerateInterpolator(2f)).start();
        }
        handler.postDelayed(tick, 20_000);
    }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (sleepStatus != null) showSleep();
            handler.postDelayed(this, 20_000);
        }
    };

    void stop() {
        handler.removeCallbacksAndMessages(null);
    }

    /** Fecha com a saída deslizando para a direita. */
    void close() {
        if (closing) return;
        closing = true;
        if (!motion) {
            a.finish();
            return;
        }
        panel.animate().translationX((WIDTH_DP + 28) * dp).setDuration(180).withEndAction(a::finish).start();
    }

    private void open(Intent target) {
        try {
            a.startActivity(target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(a, R.string.not_available, Toast.LENGTH_SHORT).show();
        }
        a.finish();
    }

    private void showVolume() {
        int max = Math.max(1, audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        int v = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
        boolean muted = audio.isStreamMute(AudioManager.STREAM_MUSIC);
        volumeStatus.setText(muted ? "◀  " + a.getString(R.string.cc_muted) + "  ▶"
                : "◀  " + (v * 100 / max) + "%  ▶");
    }

    private void showSleep() {
        long left = SleepTimer.remainingMs(SleepTimer.deadline(a), System.currentTimeMillis());
        sleepStatus.setText(left > 0 ? a.getString(R.string.cc_sleep_left, SleepTimer.format(left)) : a.getString(R.string.cc_sleep_off));
    }

    // ---- views -----------------------------------------------------------------------------------

    private TextView text(int sp, int color) {
        TextView t = new TextView(a);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(a.getResources().getFont(R.font.outfit_medium));
        return t;
    }

    private TextView status(View row) {
        return (TextView) ((LinearLayout) row).getChildAt(2);
    }

    /** Linha com ícone, título e estado; foco com fundo opaco (cantos de 16 dp), escala e elevação ({@link #focusScale}). */
    private View row(int icon, int title) {
        LinearLayout row = new LinearLayout(a);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setFocusable(true);
        row.setClickable(true);
        row.setPadding((int) (14 * dp), 0, (int) (14 * dp), 0);
        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[]{android.R.attr.state_focused}, round(FOCUS_FILL, ITEM_RADIUS_DP * dp));
        bg.addState(new int[0], new ColorDrawable(0));
        if (motion) {
            bg.setEnterFadeDuration(150);
            bg.setExitFadeDuration(150);
        }
        row.setBackground(bg);
        ImageView iv = new ImageView(a);
        iv.setImageResource(icon);
        iv.setColorFilter(TEXT);
        row.addView(iv, new LinearLayout.LayoutParams((int) (24 * dp), (int) (24 * dp)));
        TextView name = text(18, TEXT);
        name.setText(title);
        name.setSingleLine(true);
        name.setPadding((int) (14 * dp), 0, (int) (8 * dp), 0);
        row.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView st = text(15, TEXT_2);
        st.setSingleLine(true);
        row.addView(st);
        focusScale(row);
        list.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (58 * dp)));
        return row;
    }

    /**
     * Escala 1,02 com mola e elevação de 4 dp no foco: o item flutua sobre os vizinhos e a borda do grupo, com
     * sombra suave. O recuo de 12 dp cabe a escala e a sombra sem tocar o contorno do painel.
     */
    private void focusScale(View view) {
        SpringAnimation sx = spring(view, DynamicAnimation.SCALE_X);
        SpringAnimation sy = spring(view, DynamicAnimation.SCALE_Y);
        View.OnFocusChangeListener before = view.getOnFocusChangeListener();
        view.setOnFocusChangeListener((v, focused) -> {
            if (before != null) before.onFocusChange(v, focused);
            v.setTranslationZ(focused ? FOCUS_LIFT_DP * dp : 0);
            v.invalidateOutline();
            float scale = focused ? 1.02f : 1f;
            if (motion) {
                sx.animateToFinalPosition(scale);
                sy.animateToFinalPosition(scale);
            } else {
                v.setScaleX(scale);
                v.setScaleY(scale);
            }
        });
    }

    private SpringAnimation spring(View v, DynamicAnimation.ViewProperty property) {
        SpringAnimation s = new SpringAnimation(v, property, 1f);
        s.getSpring().setStiffness(SpringForce.STIFFNESS_MEDIUM).setDampingRatio(0.72f);
        return s;
    }
}
