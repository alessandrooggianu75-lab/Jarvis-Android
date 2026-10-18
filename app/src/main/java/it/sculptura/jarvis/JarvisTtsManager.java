package it.sculptura.jarvis;

import android.content.Context;
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
        tts.setPitch(0.72f);
        tts.setSpeechRate(0.92f);
        selectBestItalianLocalVoice();
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String utteranceId) { }
            @Override public void onError(String utteranceId) { finish(); }
            @Override public void onDone(String utteranceId) { finish(); }
        });
        ready = true;
    }

    private void selectBestItalianLocalVoice() {
        try {
            Set<Voice> voices = tts.getVoices();
            if (voices == null) return;
            Voice best = null;
            int bestScore = Integer.MIN_VALUE;
            for (Voice voice : voices) {
                Locale locale = voice.getLocale();
                if (locale == null || !"it".equalsIgnoreCase(locale.getLanguage())) continue;
                int score = 0;
                if (!voice.isNetworkConnectionRequired()) score += 20;
                if (Locale.ITALY.getCountry().equalsIgnoreCase(locale.getCountry())) score += 10;
                String n = voice.getName() == null ? "" : voice.getName().toLowerCase(Locale.ROOT);
                if (n.contains("male") || n.contains("masch") || n.contains("uomo")) score += 50;
                if (n.contains("_m") || n.contains("-m")) score += 8;
                if (score > bestScore) {
                    bestScore = score;
                    best = voice;
                }
            }
            if (best != null) tts.setVoice(best);
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
            main.postDelayed(() -> speak(text, onDone), 400);
            return;
        }
        String id = "jarvis-" + UUID.randomUUID();
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id);
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
