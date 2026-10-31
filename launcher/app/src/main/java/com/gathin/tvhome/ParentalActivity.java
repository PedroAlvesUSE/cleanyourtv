package com.gathin.tvhome;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Tela de PIN do controle parental, por cima do app protegido. Voltar leva à tela inicial. */
public final class ParentalActivity extends Activity {
    static final String EXTRA_PACKAGE = "package";
    static final String EXTRA_LIMIT = "limit";

    private String target;
    private boolean limit;
    private boolean done;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        target = getIntent().getStringExtra(EXTRA_PACKAGE);
        limit = getIntent().getBooleanExtra(EXTRA_LIMIT, false);
        Parental.setShowing(true);
        float dp = getResources().getDisplayMetrics().density;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(0xF2000000);
        ImageView lock = new ImageView(this);
        lock.setImageResource(R.drawable.ic_lock);
        lock.setColorFilter(Color.WHITE);
        root.addView(lock, new LinearLayout.LayoutParams((int) (56 * dp), (int) (56 * dp)));
        TextView title = new TextView(this);
        title.setTextColor(Color.WHITE);
        title.setTextSize(26);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, (int) (16 * dp), 0, (int) (6 * dp));
        title.setText(limit ? getString(R.string.parental_limit_reached) : getString(R.string.parental_locked, label()));
        root.addView(title);
        TextView hint = new TextView(this);
        hint.setTextColor(0xFFA1A1A6);
        hint.setTextSize(18);
        hint.setText(R.string.parental_enter);
        root.addView(hint);
        EditText pin = new EditText(this);
        pin.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        pin.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(4)});
        pin.setGravity(Gravity.CENTER);
        pin.setTextColor(Color.WHITE);
        pin.setTextSize(32);
        pin.setLetterSpacing(0.4f);
        root.addView(pin, new LinearLayout.LayoutParams((int) (220 * dp), LinearLayout.LayoutParams.WRAP_CONTENT));
        pin.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }

            @Override
            public void afterTextChanged(Editable e) {
                if (e.length() != 4) return;
                if (Parental.verify(ParentalActivity.this, e.toString())) {
                    done = true;
                    Parental.unlock(target == null ? "" : target, limit, System.currentTimeMillis());
                    finish();
                } else {
                    e.clear();
                    hint.setText(R.string.parental_wrong);
                    hint.setTextColor(0xFFFF453A);
                }
            }
        });
        setContentView(root);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        pin.requestFocus();
        pin.post(() -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(pin, 0);
        });
    }

    private String label() {
        try {
            PackageManager pm = getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(target, 0)).toString();
        } catch (Exception e) {
            return String.valueOf(target);
        }
    }

    @Override
    protected void onDestroy() {
        Parental.setShowing(false);
        if (!done) Parental.goHome(this);
        super.onDestroy();
    }

    /** Qualquer outra coisa que tome a tela (Home, outro app) cancela o PIN. */
    @Override
    protected void onStop() {
        super.onStop();
        if (!isFinishing()) finish();
    }

    static Intent intent(android.content.Context ctx, String pkg, boolean limit) {
        return new Intent(ctx, ParentalActivity.class).putExtra(EXTRA_PACKAGE, pkg).putExtra(EXTRA_LIMIT, limit)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_HISTORY);
    }
}
