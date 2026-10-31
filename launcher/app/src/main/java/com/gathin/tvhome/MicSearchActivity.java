package com.gathin.tvhome;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.widget.Toast;

import java.util.ArrayList;

/**
 * Activity transparente do botão de microfone (mic_tizentube): pede a fala (pt-BR) e abre a busca no TizenTube.
 * Também responde a ACTION_ASSIST, para quem escolher o launcher como assistente (plano B no README do sistema).
 */
public final class MicSearchActivity extends Activity {
    private static final int REQUEST = 1;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) return;
        Intent speech = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
                .putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.mic_prompt));
        try {
            startActivityForResult(speech, REQUEST);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.mic_unavailable, Toast.LENGTH_LONG).show();
            RemoteKeys.launch(this, MicSearch.TIZENTUBE);
            finish();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        ArrayList<String> results = data == null ? null : data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
        if (resultCode == RESULT_OK && results != null && !results.isEmpty()) {
            MicSearch.open(this, results.get(0));
        }
        finish();
        overridePendingTransition(0, 0);
    }
}
