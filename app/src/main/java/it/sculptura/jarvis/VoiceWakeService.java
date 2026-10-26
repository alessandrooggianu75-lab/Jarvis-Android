package it.sculptura.jarvis;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;

import java.util.ArrayList;
import java.util.Locale;

public class VoiceWakeService extends Service {
    public static final String ACTION_START = "it.sculptura.jarvis.START";
    public static final String ACTION_STOP = "it.sculptura.jarvis.STOP";
    public static final String ACTION_STATE = "it.sculptura.jarvis.STATE";
    public static final String EXTRA_STATE = "state";

    private static final String CHANNEL_ID = "jarvis_voice";
    private static final int NOTIFICATION_ID = 1978;

    private final Handler main = new Handler(Looper.getMainLooper());
    private SpeechRecognizer recognizer;
    private Intent recognizerIntent;
    private JarvisTtsManager tts;
    private boolean commandMode = false;
    private boolean shuttingDown = false;
    private boolean recognitionRunning = false;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        tts = new JarvisTtsManager(this);
        setupRecognizer();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            shutdownJarvis();
            return START_NOT_STICKY;
        }
        startForeground(NOTIFICATION_ID, buildNotification());
        shuttingDown = false;
        main.postDelayed(this::startWakeListening, 450);
        return START_STICKY;
    }

    private void createNotificationChannel() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm == null) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "JARVIS in ascolto",
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Mantiene attivo il riconoscimento vocale di JARVIS.");
        nm.createNotificationChannel(channel);
    }

    private Notification buildNotification() {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(
                this,
                0,
                open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle("JARVIS SCULPTURA")
                .setContentText("In ascolto: Jarvis, Ferrovecchio o Celebro")
                .setOngoing(true)
                .setContentIntent(pi)
                .build();
    }

    private void setupRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            sendState("error");
            return;
        }

        // Usa il riconoscitore Android predefinito: sui Samsung/Google recenti
        // è normalmente più accurato del motore offline forzato.
        recognizer = SpeechRecognizer.createSpeechRecognizer(this);

        recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.ITALY.toLanguageTag());
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, Locale.ITALY.toLanguageTag());
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1100L);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 700L);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 500L);

        recognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) {
                sendState(commandMode ? "command" : "listening");
            }
            @Override public void onBeginningOfSpeech() { }
            @Override public void onRmsChanged(float rmsdB) { }
            @Override public void onBufferReceived(byte[] buffer) { }
            @Override public void onEndOfSpeech() { }

            @Override public void onError(int error) {
                recognitionRunning = false;
                if (shuttingDown) return;
                long delay = error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY ? 1000L : 500L;
                main.postDelayed(commandMode ? VoiceWakeService.this::startCommandListening : VoiceWakeService.this::startWakeListening, delay);
            }

            @Override public void onResults(Bundle results) {
                recognitionRunning = false;
                ArrayList<String> list = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                String heard = bestResult(list);

                if (commandMode) {
                    if (heard.isEmpty()) {
                        speakThenWake("Ripeti pure.");
                        return;
                    }
                    String normalized = normalize(heard);
                    if (containsWakeWord(normalized)) {
                        String stripped = stripWakeWord(normalized);
                        processCommand(stripped.isEmpty() ? heard : stripped);
                    } else {
                        processCommand(heard);
                    }
                } else {
                    handleWakeResult(heard);
                }
            }

            @Override public void onPartialResults(Bundle partialResults) {
                ArrayList<String> list = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (list == null || list.isEmpty()) return;

                String heard = normalize(list.get(0));

                // Importante: non interrompere l'ascolto appena compare solo la wake word.
                // Aspettiamo che l'utente completi "Jarvis, apri Chrome".
                if (!commandMode && containsWakeWord(heard)) {
                    String inlineCommand = stripWakeWord(heard);
                    if (inlineCommand.length() >= 2) {
                        cancelRecognition();
                        commandMode = true;
                        processCommand(inlineCommand);
                    }
                }
            }

            @Override public void onEvent(int eventType, Bundle params) { }
        });
    }

    private String bestResult(ArrayList<String> list) {
        if (list == null || list.isEmpty()) return "";
        for (String candidate : list) {
            if (candidate != null && containsWakeWord(candidate) && !stripWakeWord(candidate).isEmpty()) {
                return candidate.trim();
            }
        }
        for (String candidate : list) {
            if (candidate != null && !candidate.trim().isEmpty()) return candidate.trim();
        }
        return "";
    }

    private void startWakeListening() {
        if (shuttingDown || recognizer == null || recognitionRunning) return;
        commandMode = false;
        startRecognition();
    }

    private void startCommandListening() {
        if (shuttingDown || recognizer == null || recognitionRunning) return;
        commandMode = true;
        startRecognition();
    }

    private void startRecognition() {
        try {
            recognitionRunning = true;
            recognizer.startListening(recognizerIntent);
        } catch (Exception e) {
            recognitionRunning = false;
            sendState("error");
            main.postDelayed(commandMode ? this::startCommandListening : this::startWakeListening, 850);
        }
    }

    private void cancelRecognition() {
        if (recognizer == null) return;
        try { recognizer.cancel(); } catch (Exception ignored) { }
        recognitionRunning = false;
    }

    private void handleWakeResult(String heardRaw) {
        String heard = normalize(heardRaw);
        if (!containsWakeWord(heard)) {
            main.postDelayed(this::startWakeListening, 260);
            return;
        }
        String inlineCommand = stripWakeWord(heard);
        if (!inlineCommand.isEmpty()) {
            commandMode = true;
            processCommand(inlineCommand);
        } else {
            wakeAndAsk();
        }
    }

    private void wakeAndAsk() {
        cancelRecognition();
        commandMode = true;
        sendState("speaking");
        tts.speak("Dimmi.", () -> main.postDelayed(this::startCommandListening, 180));
    }

    private void processCommand(String rawCommand) {
        cancelRecognition();
        String command = normalize(rawCommand);
        if (command.isEmpty()) {
            speakThenWake("Ripeti pure.");
            return;
        }
        if (command.equals("smetti di ascoltare") || command.equals("disattivati") || command.equals("stop jarvis")) {
            sendState("speaking");
            tts.speak("Ricevuto. Mi disattivo.", this::shutdownJarvis);
            return;
        }

        sendState("thinking");
        String response;
        try {
            response = JarvisBrain.handle(this, rawCommand);
        } catch (Exception e) {
            response = AppController.webSearch(this, rawCommand);
        }
        speakThenWake(response);
    }

    private void speakThenWake(String response) {
        commandMode = false;
        sendState("speaking");
        tts.speak(response, () -> main.postDelayed(this::startWakeListening, 220));
    }

    private boolean containsWakeWord(String text) {
        String n = normalize(text);
        return n.matches(".*\\bjarvis\\b.*") || n.matches(".*\\bferrovecchio\\b.*") || n.matches(".*\\bcelebro\\b.*");
    }

    private String stripWakeWord(String text) {
        String n = normalize(text);
        n = n.replaceFirst("\\bjarvis\\b", "");
        n = n.replaceFirst("\\bferrovecchio\\b", "");
        n = n.replaceFirst("\\bcelebro\\b", "");
        return n.replaceAll("^[,;:.!?\\s]+", "").trim();
    }

    private String normalize(String text) {
        if (text == null) return "";
        return java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ITALIAN)
                .replaceAll("\\s+", " ")
                .trim();
    }

    private void sendState(String state) {
        Intent i = new Intent(ACTION_STATE);
        i.setPackage(getPackageName());
        i.putExtra(EXTRA_STATE, state);
        sendBroadcast(i);
    }

    private void shutdownJarvis() {
        shuttingDown = true;
        commandMode = false;
        cancelRecognition();
        sendState("idle");
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        shuttingDown = true;
        cancelRecognition();
        if (recognizer != null) {
            try { recognizer.destroy(); } catch (Exception ignored) { }
            recognizer = null;
        }
        if (tts != null) {
            tts.shutdown();
            tts = null;
        }
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
