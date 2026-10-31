package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.graphics.Color;
import android.graphics.Typeface;
import android.widget.TextView;

/**
 * Shared pieces of the Apple tvOS look (feature apple_style): the switch keys, the type and the colours. The
 * colours match res/values/apple.xml and the Settings screen.
 */
final class Apple {
    static final String STYLE = "apple_style";
    static final String PARALLAX = "parallax";
    static final String TOP_SHELF = "top_shelf";
    static final String AUTO_ACCENT = "auto_accent";

    /** res/values/apple.xml apple_text and apple_text_secondary, as constants for code without a Context. */
    static final int TEXT = 0xFFF5F5F7;
    static final int TEXT_2 = 0xFFA1A1A6;

    /** The system sans at weight 500, the closest to SF Pro Semibold the TV has without a bundled font. */
    static final Typeface MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    static final Typeface REGULAR = Typeface.create("sans-serif", Typeface.NORMAL);
    static final Typeface LIGHT = Typeface.create("sans-serif-light", Typeface.NORMAL);

    private Apple() {
    }

    /** Motion is on in the launcher and the TV's animator scale is not 0. Read each time: the scale can change. */
    static boolean motion(boolean animations) {
        return animations && ValueAnimator.areAnimatorsEnabled();
    }

    /** Type, colour and a soft shadow that keeps the text readable over a bright wallpaper. */
    static void style(TextView text, Typeface face, int color, float density) {
        text.setTypeface(face);
        text.setTextColor(color);
        text.setShadowLayer(10f * density, 0f, 1f * density, text.getContext().getColor(R.color.apple_text_shadow));
    }

    /** The accent lifted towards white for small marks (indicators) that must read on dark glass. */
    static int light(int accent) {
        if (accent == 0) return TEXT;
        float[] hsv = new float[3];
        Color.colorToHSV(accent, hsv);
        hsv[1] = Math.min(hsv[1], 0.55f);
        hsv[2] = 1f;
        return Color.HSVToColor(hsv);
    }

    /** Colour of the focus shadow: black, or with auto_accent a deep shade of the accent that reads as coloured light. */
    static int shadow(int accent) {
        if (accent == 0) return 0xFF000000;
        float[] hsv = new float[3];
        Color.colorToHSV(accent, hsv);
        hsv[1] = Math.min(1f, hsv[1] * 1.1f + 0.1f);
        hsv[2] = 0.42f;
        return Color.HSVToColor(hsv);
    }
}
