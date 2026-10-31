package com.gathin.tvhome;

import android.app.Activity;
import android.app.Dialog;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

/**
 * Tela de pareamento do controle pelo celular, no estilo tvOS: cartão de vidro com um QR grande que já leva o
 * PIN (o celular entra sem digitar), o endereço e o PIN em texto, e os botões Novo PIN e Concluído.
 */
final class RemoteQrDialog {
    private static final int TEXT = 0xFFF5F5F7;
    private static final int TEXT_2 = 0xFFA1A1A6;
    private static final int TRIES = 6;

    private final Activity a;
    private final float dp;
    private final boolean motion;
    private final Dialog dialog;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Typeface font;
    private ImageView qr;
    private View qrTile;
    private TextView hint;
    private TextView address;
    private TextView pin;
    private TextView renew;
    private int tries;

    private RemoteQrDialog(Activity a) {
        this.a = a;
        this.dp = a.getResources().getDisplayMetrics().density;
        this.motion = android.animation.ValueAnimator.areAnimatorsEnabled();
        this.font = a.getResources().getFont(R.font.outfit_medium);
        this.dialog = new Dialog(a, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
    }

    static void show(Activity a) {
        new RemoteQrDialog(a).build();
    }

    private GradientDrawable round(int color, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        return g;
    }

    private TextView text(int sp, int color) {
        TextView t = new TextView(a);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(font);
        return t;
    }

    private void build() {
        FrameLayout root = new FrameLayout(a);
        root.setBackgroundColor(0xD9000000);

        LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        int pad = (int) (40 * dp);
        card.setPadding(pad, pad, pad, pad);
        GradientDrawable glass = round(0xFF1C1C1E, 36 * dp);
        glass.setStroke((int) dp, 0x26FFFFFF);
        card.setBackground(glass);
        card.setElevation(24 * dp);
        card.setClipChildren(false);
        card.setClipToPadding(false);

        FrameLayout tile = new FrameLayout(a);
        tile.setBackground(round(0xFFFFFFFF, 24 * dp));
        int tp = (int) (10 * dp);
        tile.setPadding(tp, tp, tp, tp);
        qr = new ImageView(a);
        qr.setScaleType(ImageView.ScaleType.FIT_CENTER);
        tile.addView(qr, new FrameLayout.LayoutParams((int) (280 * dp), (int) (280 * dp)));
        card.addView(tile, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        qrTile = tile;

        LinearLayout side = new LinearLayout(a);
        side.setOrientation(LinearLayout.VERTICAL);
        side.setClipChildren(false);
        side.setClipToPadding(false);
        LinearLayout.LayoutParams sideLp = new LinearLayout.LayoutParams((int) (420 * dp), ViewGroup.LayoutParams.WRAP_CONTENT);
        sideLp.setMarginStart((int) (44 * dp));
        card.addView(side, sideLp);

        TextView title = text(34, TEXT);
        title.setText(R.string.set_phone);
        side.addView(title);

        hint = text(18, TEXT_2);
        hint.setLineSpacing(0, 1.2f);
        hint.setPadding(0, (int) (10 * dp), 0, (int) (26 * dp));
        side.addView(hint);

        TextView orLabel = text(15, TEXT_2);
        orLabel.setText(R.string.remote_qr_or);
        side.addView(orLabel);
        address = text(24, TEXT);
        address.setPadding(0, (int) (4 * dp), 0, (int) (20 * dp));
        address.setSingleLine(true);
        side.addView(address);

        TextView pinLabel = text(15, TEXT_2);
        pinLabel.setText("PIN");
        side.addView(pinLabel);
        pin = text(48, TEXT);
        pin.setLetterSpacing(0.3f);
        pin.setPadding(0, 0, 0, (int) (24 * dp));
        side.addView(pin);

        LinearLayout buttons = new LinearLayout(a);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setClipChildren(false);
        buttons.setClipToPadding(false);
        side.addView(buttons);
        TextView done = button(R.string.remote_done);
        done.setOnClickListener(v -> close());
        buttons.addView(done, buttonLp(false));
        renew = button(R.string.remote_new_pin);
        renew.setOnClickListener(v -> {
            RemoteServer.renewPin(a);
            fill();
        });
        buttons.addView(renew, buttonLp(true));

        root.addView(card, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        dialog.setContentView(root);
        Window w = dialog.getWindow();
        if (w != null) w.setBackgroundDrawable(new ColorDrawable(0));
        dialog.setOnDismissListener(d -> handler.removeCallbacksAndMessages(null));
        fill();
        dialog.show();
        done.requestFocus();
        if (motion) {
            root.setAlpha(0f);
            root.animate().alpha(1f).setDuration(200).start();
            card.setScaleX(0.94f);
            card.setScaleY(0.94f);
            card.animate().scaleX(1f).scaleY(1f).setDuration(320).setInterpolator(new DecelerateInterpolator(2f)).start();
        }
    }

    /** Preenche QR, endereço e PIN; com o servidor ainda ligando, tenta de novo a cada segundo. */
    private void fill() {
        String url = RemoteServer.url();
        String pair = RemoteServer.pairUrl(a);
        boolean[][] m = pair == null ? null : Qr.encode(pair);
        if (url == null || m == null) {
            qrTile.setVisibility(View.GONE);
            address.setVisibility(View.GONE);
            pin.setVisibility(View.GONE);
            renew.setVisibility(View.GONE);
            hint.setText(tries < TRIES ? R.string.remote_waiting : R.string.remote_off);
            if (tries++ < TRIES) handler.postDelayed(this::fill, 1000);
            return;
        }
        BitmapDrawable d = new BitmapDrawable(a.getResources(), BackupActivity.bitmap(m));
        d.setFilterBitmap(false);
        qr.setImageDrawable(d);
        qrTile.setVisibility(View.VISIBLE);
        address.setVisibility(View.VISIBLE);
        pin.setVisibility(View.VISIBLE);
        renew.setVisibility(View.VISIBLE);
        hint.setText(R.string.remote_qr_hint);
        address.setText(url.replaceFirst("^http://", ""));
        pin.setText(RemoteServer.pin(a));
    }

    private void close() {
        if (!motion) {
            dialog.dismiss();
            return;
        }
        View root = dialog.findViewById(android.R.id.content);
        root.animate().alpha(0f).setDuration(160).withEndAction(dialog::dismiss).start();
    }

    private LinearLayout.LayoutParams buttonLp(boolean gap) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, (int) (56 * dp));
        if (gap) lp.setMarginStart((int) (16 * dp));
        return lp;
    }

    /** Botão em pílula: vidro em repouso; com foco, fundo claro, texto escuro e escala 1,06 com mola. */
    private TextView button(int label) {
        TextView b = text(19, TEXT);
        b.setText(label);
        b.setGravity(Gravity.CENTER);
        b.setFocusable(true);
        b.setClickable(true);
        b.setMinWidth((int) (150 * dp));
        b.setPadding((int) (28 * dp), 0, (int) (28 * dp), 0);
        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[]{android.R.attr.state_focused}, round(TEXT, 28 * dp));
        bg.addState(new int[0], round(0x33FFFFFF, 28 * dp));
        if (motion) {
            bg.setEnterFadeDuration(150);
            bg.setExitFadeDuration(150);
        }
        b.setBackground(bg);
        b.setTextColor(new ColorStateList(new int[][]{{android.R.attr.state_focused}, {}}, new int[]{0xFF000000, TEXT}));
        SpringAnimation sx = spring(b, DynamicAnimation.SCALE_X);
        SpringAnimation sy = spring(b, DynamicAnimation.SCALE_Y);
        b.setOnFocusChangeListener((v, focused) -> {
            float scale = focused ? 1.06f : 1f;
            if (motion) {
                sx.animateToFinalPosition(scale);
                sy.animateToFinalPosition(scale);
            } else {
                v.setScaleX(scale);
                v.setScaleY(scale);
            }
        });
        return b;
    }

    private SpringAnimation spring(View v, DynamicAnimation.ViewProperty property) {
        SpringAnimation s = new SpringAnimation(v, property, 1f);
        s.getSpring().setStiffness(SpringForce.STIFFNESS_MEDIUM).setDampingRatio(0.72f);
        return s;
    }
}
