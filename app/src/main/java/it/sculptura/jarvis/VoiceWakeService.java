package it.sculptura.jarvis;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
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
        main.postDelayed(this::startWakeListening, 500);
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
                .setContentText("In ascolto: Jarvis o Ferrovecchio")
                .setOngoing(true)
                .setContentIntent(pi)
                .build();
    }

    private void setupRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            sendState("error");
            return;
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) {
                recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(this);
            } else {
                recognizer = SpeechRecognizer.createSpeechRecognizer(this);
            }
        } catch (Exception e) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this);
        }

        recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.ITALY.toLanguageTag());
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, Locale.ITALY.toLanguageTag());
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);

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
                long delay = error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY ? 1100L : 450L;
                if (commandMode) {
                    main.postDelayed(VoiceWakeService.this::startCommandListening, delay);
                } else {
                    main.postDelayed(VoiceWakeService.this::startWakeListening, delay);
                }
            }

            @Override public void onResults(Bundle results) {
                recognitionRunning = false;
                ArrayList<String> list = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                String heard = (list == null || list.isEmpty()) ? "" : list.get(0).trim();
                if (commandMode) {
                    if (heard.isEmpty()) speakThenWake("Non ho capito.");
                    else processCommand(heard);
                } else {
                    handleWakeResult(heard);
                }
            }

            @Override public void onPartialResults(Bundle partialResults) {
                if (commandMode) return;
                ArrayList<String> list = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (list == null || list.isEmpty()) return;
                String heard = normalize(list.get(0));
                if (containsWakeWord(heard)) {
                    cancelRecognition();
                    String inlineCommand = stripWakeWord(heard);
                    if (!inlineCommand.isEmpty()) {
                        commandMode = true;
                        processCommand(inlineCommand);
                    } else {
                        wakeAndAsk();
                    }
                }
            }

            @Override public void onEvent(int eventType, Bundle params) { }
        });
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
            main.postDelayed(commandMode ? this::startCommandListening : this::startWakeListening, 900);
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
            main.postDelayed(this::startWakeListening, 250);
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
        sendState("thinking");
        String command = normalize(rawCommand);
        String response;

        if (command.startsWith("apri ")) {
            response = AppController.openAppByLabel(this, command.substring(5).trim());
        } else if (command.startsWith("avvia ")) {
            response = AppController.openAppByLabel(this, command.substring(6).trim());
        } else if (command.startsWith("cerca in rete ")) {
            response = AppController.webSearch(this, command.substring(14).trim());
        } else if (command.startsWith("cerca su internet ")) {
            response = AppController.webSearch(this, command.substring(17).trim());
        } else if (command.startsWith("cerca online ")) {
            response = AppController.webSearch(this, command.substring(13).trim());
        } else if (command.equals("indietro") || command.equals("torna indietro")) {
            response = JarvisAccessibilityService.globalBack() ? "Fatto." : accessibilityNeeded();
        } else if (command.equals("home") || command.equals("vai alla home")) {
            response = JarvisAccessibilityService.globalHome() ? "Fatto." : accessibilityNeeded();
        } else if (command.equals("app recenti") || command.equals("recenti")) {
            response = JarvisAccessibilityService.globalRecents() ? "Apro le applicazioni recenti." : accessibilityNeeded();
        } else if (command.equals("notifiche") || command.equals("apri notifiche")) {
            response = JarvisAccessibilityService.globalNotifications() ? "Apro le notifiche." : accessibilityNeeded();
        } else if (command.startsWith("tocca ")) {
            String target = rawCommand.trim().substring(Math.min(rawCommand.trim().length(), 6)).trim();
            response = JarvisAccessibilityService.clickText(target) ? "Fatto." : "Non trovo il comando " + target + " sullo schermo.";
        } else if (command.startsWith("premi ")) {
            String target = rawCommand.trim().substring(Math.min(rawCommand.trim().length(), 6)).trim();
            response = JarvisAccessibilityService.clickText(target) ? "Fatto." : "Non trovo il comando " + target + " sullo schermo.";
        } else if (command.startsWith("scrivi ")) {
            String text = rawCommand.trim().substring(Math.min(rawCommand.trim().length(), 7)).trim();
            response = JarvisAccessibilityService.writeText(text) ? "Fatto." : "Non trovo un campo di testo attivo.";
        } else if (command.contains("accessibilita") || command.contains("permessi avanzati")) {
            AppController.openAccessibilitySettings(this);
            response = "Apri JARVIS Accessibilità e attivalo.";
        } else if (command.equals("smetti di ascoltare") || command.equals("disattivati") || command.equals("stop jarvis")) {
            sendState("speaking");
            tts.speak("Ricevuto. Mi disattivo.", this::shutdownJarvis);
            return;
        } else if (command.equals("chi sei") || command.equals("presentati")) {
            response = "Sono JARVIS SCULPTURA. Posso aprire applicazioni, cercare in rete e aiutarti a controllare Android con i permessi che mi concedi.";
        } else {
            response = "Questo comando non è ancora disponibile. Posso aprire applicazioni, cercare in rete, premere elementi sullo schermo, scrivere testo, andare indietro, alla home o nelle notifiche.";
        }

        speakThenWake(response);
    }

    private String accessibilityNeeded() {
        return "Per farlo devi attivare JARVIS Accessibilità. Tieni premuto il cerchio per aprire le impostazioni.";
    }

    private void speakThenWake(String response) {
        commandMode = false;
        sendState("speaking");
        tts.speak(response, () -> main.postDelayed(this::startWakeListening, 220));
    }

    private boolean containsWakeWord(String text) {
        String n = normalize(text);
        return n.matches(".*\\bjarvis\\b.*") || n.matches(".*\\bferrovecchio\\b.*");
    }

    private String stripWakeWord(String text) {
        String n = normalize(text);
        n = n.replaceFirst("\\bjarvis\\b", "");
        n = n.replaceFirst("\\bferrovecchio\\b", "");
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

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
