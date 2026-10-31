package com.gathin.tvhome;

import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;

import androidx.dynamicanimation.animation.FloatValueHolder;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

/**
 * tvOS focus for one tile (apple_style): the tile grows to {@link #SCALE} and rises, a soft drawn shadow opens
 * under it and a specular light fades in on its artwork. With parallax, a D-pad press leans the tile up to
 * {@link #MAX_TILT}° and slides the light, and a tile that takes focus arrives leaning the way focus travelled,
 * then settles. Everything is a spring on transform and alpha only: no layout and no redraw per frame.
 */
final class AppleFocus {
    static final float SCALE = 1.08f;
    static final float MAX_TILT = 3f;
    /** auto_accent colour (0xAARRGGBB), or 0 for the neutral black shadow. Set by MainActivity. */
    static int accent;

    /** The last D-pad direction pressed on a tile and when, so the tile that takes focus knows where it came from. */
    private static int lastDx, lastDy;
    private static long lastKeyAt;

    private final View item, card, shadow, glare;
    private final float density, liftPx;
    private final boolean animations, parallax, reduced;
    private final FloatValueHolder lift = new FloatValueHolder(0f);
    private final FloatValueHolder tiltX = new FloatValueHolder(0f);
    private final FloatValueHolder tiltY = new FloatValueHolder(0f);
    private final SpringAnimation liftSpring, tiltXSpring, tiltYSpring;
    private final Runnable settle = this::settle;
    private int shadowColor = 1;   // never a real colour, so the first focus sets the tint
    private boolean focused;

    AppleFocus(View item, View card, View shadow, View glare, boolean animations, boolean parallax, boolean reduced) {
        this.item = item;
        this.card = card;
        this.shadow = shadow;
        this.glare = glare;
        this.animations = animations;
        this.parallax = parallax && !reduced;
        this.reduced = reduced;
        density = card.getResources().getDisplayMetrics().density;
        liftPx = card.getResources().getDimension(R.dimen.apple_focus_lift);
        liftSpring = new SpringAnimation(lift).setSpring(new SpringForce(0f));
        liftSpring.setMinimumVisibleChange(0.002f);
        tiltXSpring = new SpringAnimation(tiltX).setSpring(new SpringForce(0f));
        tiltYSpring = new SpringAnimation(tiltY).setSpring(new SpringForce(0f));
        tiltXSpring.setMinimumVisibleChange(0.004f);
        tiltYSpring.setMinimumVisibleChange(0.004f);
        liftSpring.addUpdateListener((a, v, vel) -> apply());
        tiltXSpring.addUpdateListener((a, v, vel) -> apply());
        tiltYSpring.addUpdateListener((a, v, vel) -> apply());
        liftSpring.addEndListener((a, canceled, v, vel) -> {
            if (!focused && !canceled) item.setTranslationZ(0f);
        });
    }

    /** Sizes the perspective and the light's travel to the tile. Call once the tile size is known. */
    void setTileSize(int w, int h) {
        // The renderer places the camera at distance * 72 / dpi px: put it two tile widths away, whatever the
        // density, so 3° reads as the same gentle depth on every TV.
        card.setCameraDistance(w * 2f * card.getResources().getDisplayMetrics().densityDpi / 72f);
    }

    void onFocusChanged(boolean hasFocus) {
        focused = hasFocus;
        card.removeCallbacks(settle);
        boolean motion = Apple.motion(animations);
        if (hasFocus) {
            item.setTranslationZ(1f);   // draws above the neighbours (no outline, so no system shadow)
            int color = Apple.shadow(accent);
            if (color != shadowColor && shadow.getBackground() != null) {
                shadowColor = color;
                if (accent == 0) shadow.getBackground().setTintList(null);
                else shadow.getBackground().setTint(color);
            }
        }
        if (!motion) {
            stopAll();
            lift.setValue(hasFocus ? 1f : 0f);
            tiltX.setValue(0f);
            tiltY.setValue(0f);
            apply();
            if (!hasFocus) item.setTranslationZ(0f);
            return;
        }
        SpringForce s = liftSpring.getSpring();
        if (reduced) {
            s.setStiffness(1400f).setDampingRatio(0.95f);
        } else if (hasFocus) {
            // Medium stiffness and a damping of 0.75: the tile pops forward with a hint of overshoot.
            s.setStiffness(700f).setDampingRatio(0.75f);
        } else {
            // Out faster than in, and without overshoot, so moving along a row never feels sticky.
            s.setStiffness(1500f).setDampingRatio(1f);
        }
        liftSpring.animateToFinalPosition(hasFocus ? 1f : 0f);
        if (hasFocus && useParallax(motion) && SystemClock.uptimeMillis() - lastKeyAt < 350) {
            // Arrive leaning the way focus moved, then settle to the centre with a small wobble.
            tiltXSpring.cancel();
            tiltYSpring.cancel();
            tiltX.setValue(lastDx * 0.65f);
            tiltY.setValue(lastDy * 0.65f);
            animateTilt(0f, 0f, 260f, 0.55f);
        } else {
            animateTilt(0f, 0f, hasFocus ? 400f : 1200f, 1f);
        }
    }

    /** A D-pad key on this (focused) tile: lean towards it. If focus moves, the next tile takes over at once. */
    void onKey(int keyCode) {
        int dx = keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : keyCode == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 0;
        int dy = keyCode == KeyEvent.KEYCODE_DPAD_DOWN ? 1 : keyCode == KeyEvent.KEYCODE_DPAD_UP ? -1 : 0;
        lastDx = dx;
        lastDy = dy;
        lastKeyAt = SystemClock.uptimeMillis();
        if (!focused || !useParallax(Apple.motion(animations))) return;
        animateTilt(dx, dy, 900f, 0.8f);
        card.removeCallbacks(settle);
        card.postDelayed(settle, 140);
    }

    private void settle() {
        if (focused) animateTilt(0f, 0f, 220f, 0.55f);
    }

    private boolean useParallax(boolean motion) {
        return parallax && motion;
    }

    private void animateTilt(float x, float y, float stiffness, float damping) {
        tiltXSpring.getSpring().setStiffness(stiffness).setDampingRatio(damping);
        tiltYSpring.getSpring().setStiffness(stiffness).setDampingRatio(damping);
        tiltXSpring.animateToFinalPosition(x);
        tiltYSpring.animateToFinalPosition(y);
    }

    private void stopAll() {
        liftSpring.cancel();
        tiltXSpring.cancel();
        tiltYSpring.cancel();
    }

    /** Puts the tile at rest at once (rebind, reload): focused or not, no tilt. */
    void snap(boolean hasFocus) {
        focused = hasFocus;
        card.removeCallbacks(settle);
        stopAll();
        lift.setValue(hasFocus ? 1f : 0f);
        tiltX.setValue(0f);
        tiltY.setValue(0f);
        item.setTranslationZ(hasFocus ? 1f : 0f);
        apply();
    }

    private void apply() {
        float p = lift.getValue();
        float tx = tiltX.getValue(), ty = tiltY.getValue();
        float s = 1f + (SCALE - 1f) * p;
        float a = Math.max(0f, Math.min(1f, p));
        card.setScaleX(s);
        card.setScaleY(s);
        card.setTranslationY(-liftPx * p + ty * 1.5f * density);
        card.setTranslationX(tx * 3f * density);
        // Pressed towards a side, that edge goes back: a positive rotationY takes the right edge away from the
        // viewer and a positive rotationX the top edge (checked on screen).
        card.setRotationY(tx * MAX_TILT);
        card.setRotationX(-ty * MAX_TILT);
        shadow.setAlpha(a);
        shadow.setScaleX(s);
        shadow.setScaleY(s);
        // The shadow stays on the ground while the tile rises, and shifts a little against the tilt.
        shadow.setTranslationY(3f * density * p);
        shadow.setTranslationX(-tx * 2f * density);
        glare.setAlpha(a);
        glare.setTranslationX(tx * card.getWidth() * 0.32f);
        glare.setTranslationY(ty * card.getHeight() * 0.3f);
    }
}
