package it.sculptura.jarvis;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public class JarvisTtsManager implements TextToSpeech.OnInitListener {
    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private TextToSpeech tts;
    private boolean ready;
    private Runnable pendingDone;

    public JarvisTtsManager(Context context) {
        this.context = context.getApplicationContext();
        tts = new TextToSpeech(this.context, this);
    }

    @Override
    public void onInit(int status) {
        if (status != TextToSpeech.SUCCESS) return;

        tts.setLanguage(Locale.ITALY);

        // A pitch troppo basso rende molte voci Android metalliche e poco naturali.
        // Manteniamo invece un timbro appena piu profondo del neutro.
        tts.setPitch(0.96f);
        tts.setSpeechRate(1.02f);

        selectBestItalianVoice();

        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String utteranceId) { }
            @Override public void onError(String utteranceId) { finish(); }
            @Override public void onDone(String utteranceId) { finish(); }
        });
        ready = true;
    }

    private void selectBestItalianVoice() {
        try {
            Set<Voice> voices = tts.getVoices();
            if (voices == null || voices.isEmpty()) return;

            Voice best = null;
            int bestScore = Integer.MIN_VALUE;

            for (Voice voice : voices) {
                Locale locale = voice.getLocale();
                if (locale == null || !"it".equalsIgnoreCase(locale.getLanguage())) continue;

                int score = 0;

                // La qualita dichiarata dal motore conta piu di tutto.
                score += voice.getQuality() * 4;
                score -= voice.getLatency();

                if (Locale.ITALY.getCountry().equalsIgnoreCase(locale.getCountry())) {
                    score += 200;
                }

                // Le voci di rete sono spesso le versioni neural/natural del motore TTS.
                if (voice.isNetworkConnectionRequired()) {
                    score += 250;
                }

                String name = voice.getName() == null
                        ? ""
                        : voice.getName().toLowerCase(Locale.ROOT);

                if (name.contains("neural") || name.contains("natural") ||
                        name.contains("wavenet") || name.contains("network")) {
                    score += 400;
                }

                // Preferenza leggera per una voce maschile, senza sacrificare la qualita.
                if (name.contains("male") || name.contains("masch") ||
                        name.contains("uomo") || name.contains("_m") || name.contains("-m")) {
                    score += 25;
                }

                if (score > bestScore) {
                    bestScore = score;
                    best = voice;
                }
            }

            if (best != null) {
                tts.setVoice(best);
            }
        } catch (Exception ignored) { }
    }

    public boolean isReady() {
        return ready;
    }

    public void speak(String text, Runnable onDone) {
        if (text == null || text.trim().isEmpty()) {
            if (onDone != null) onDone.run();
            return;
        }

        pendingDone = onDone;

        if (!ready) {
            main.postDelayed(() -> speak(text, onDone), 250);
            return;
        }

        String id = "jarvis-" + UUID.randomUUID();
        Bundle params = new Bundle();
        params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f);
        tts.speak(cleanForSpeech(text), TextToSpeech.QUEUE_FLUSH, params, id);
    }

    private String cleanForSpeech(String text) {
        return text
                .replace("…", "...")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private void finish() {
        Runnable r = pendingDone;
        pendingDone = null;
        if (r != null) main.post(r);
    }

    public void stop() {
        if (tts != null) tts.stop();
    }

    public void shutdown() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }
        ready = false;
    }
}
