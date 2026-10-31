package com.gathin.tvhome;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;

/**
 * "Protetor de tela agora" do painel rápido: o mesmo relógio do ClockDream, em tela cheia. O Android não
 * deixa um app iniciar o DreamService do sistema, então o launcher mostra o relógio numa activity.
 * Qualquer tecla fecha.
 */
public final class SaverNowActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.dream_clock);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        View content = findViewById(R.id.dreamContent);
        content.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_UP) finish();
        return true;
    }

    @Override
    public boolean onTouchEvent(android.view.MotionEvent event) {
        finish();
        return true;
    }
}
