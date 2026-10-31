package com.gathin.tvhome;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.security.SecureRandom;

/**
 * Backup das configurações (config_backup). Enquanto esta tela está aberta, um servidor só da rede local mostra
 * a URL, o PIN e um QR code com a URL. A página baixa o backup ou envia um JSON de volta; o PIN vale para os dois.
 * O servidor fecha quando a tela fecha.
 */
public final class BackupActivity extends Activity implements LanServer.Handler {
    static final int PORT = 8766;

    private LanServer server;
    private PinGate gate;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        float dp = getResources().getDisplayMetrics().density;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(0xFF000000);
        root.setPadding((int) (48 * dp), (int) (32 * dp), (int) (48 * dp), (int) (32 * dp));

        String pin = String.format("%04d", new SecureRandom().nextInt(10000));
        gate = new PinGate(pin);
        server = new LanServer(this);
        String url = null;
        try {
            int port = server.start(PORT);
            String ip = LanServer.localIp();
            if (ip != null) url = "http://" + ip + ":" + port + "/";
        } catch (IOException ignored) {
        }

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        text.addView(label(getString(R.string.set_backup), 30, Color.WHITE, dp));
        if (url == null) {
            server.stop();
            text.addView(label(getString(R.string.backup_nonet), 20, 0xFFFF453A, dp));
        } else {
            text.addView(label(getString(R.string.backup_hint), 20, 0xFFA1A1A6, dp));
            text.addView(label(url, 34, Color.WHITE, dp));
            text.addView(label("PIN  " + pin, 44, 0xFF0A84FF, dp));
            text.addView(label(getString(R.string.backup_close), 16, 0xFFA1A1A6, dp));
        }
        root.addView(text, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        boolean[][] qr = url == null ? null : Qr.encode(url);
        if (qr != null) {
            ImageView img = new ImageView(this);
            img.setImageBitmap(bitmap(qr));
            img.setScaleType(ImageView.ScaleType.FIT_CENTER);
            root.addView(img, new LinearLayout.LayoutParams((int) (300 * dp), (int) (300 * dp)));
        }
        setContentView(root);
    }

    private TextView label(String s, int sp, int color, float dp) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setPadding(0, 0, 0, (int) (14 * dp));
        return t;
    }

    /** QR em preto sobre branco, com zona silenciosa de 4 módulos, 8 pixels por módulo. */
    static Bitmap bitmap(boolean[][] m) {
        int scale = 8;
        int quiet = 4;
        int n = m.length + 2 * quiet;
        Bitmap b = Bitmap.createBitmap(n * scale, n * scale, Bitmap.Config.RGB_565);
        b.eraseColor(Color.WHITE);
        for (int y = 0; y < m.length; y++) {
            for (int x = 0; x < m.length; x++) {
                if (!m[y][x]) continue;
                for (int dy = 0; dy < scale; dy++) {
                    for (int dx = 0; dx < scale; dx++) b.setPixel((x + quiet) * scale + dx, (y + quiet) * scale + dy, Color.BLACK);
                }
            }
        }
        return b;
    }

    @Override
    protected void onDestroy() {
        if (server != null) server.stop();
        super.onDestroy();
    }

    @Override
    public LanServer.Response handle(LanServer.Request r) {
        if (r.method.equals("GET") && r.path.equals("/")) return LanServer.Response.text(200, "text/html", WebPages.backup());
        if (!r.path.startsWith("/api/")) return LanServer.Response.error(404, "not found");
        long now = System.currentTimeMillis();
        if (!gate.check(r.headers.get("x-pin"), now)) return LanServer.Response.error(gate.locked(now) ? 429 : 401, "pin");
        if (r.path.equals("/api/ping")) return LanServer.Response.ok("ok");
        if (r.path.equals("/api/export") && r.method.equals("GET")) {
            return LanServer.Response.text(200, "application/json", BackupData.exportNow(this));
        }
        if (r.path.equals("/api/import") && r.method.equals("POST")) {
            try {
                BackupData.apply(this, BackupData.parse(r.bodyText()));
            } catch (IllegalArgumentException e) {
                return LanServer.Response.error(400, getString(R.string.backup_invalid) + " (" + e.getMessage() + ")");
            } catch (IOException e) {
                return LanServer.Response.error(500, getString(R.string.backup_failed));
            }
            runOnUiThread(() -> {
                setResult(RESULT_OK);
                Toast.makeText(this, R.string.backup_restored, Toast.LENGTH_LONG).show();
            });
            return LanServer.Response.ok(getString(R.string.backup_restored));
        }
        return LanServer.Response.error(404, "not found");
    }
}
