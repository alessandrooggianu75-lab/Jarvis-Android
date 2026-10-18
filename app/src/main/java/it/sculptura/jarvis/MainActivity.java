package it.sculptura.jarvis;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.service.voice.VoiceInteractionService;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    private static final int REQ_PERMISSIONS = 4001;
    private OrbView orb;
    private TextView status;

    private final BroadcastReceiver stateReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            String state = intent.getStringExtra(VoiceWakeService.EXTRA_STATE);
            updateState(state == null ? "idle" : state);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        window.setStatusBarColor(Color.BLACK);
        window.setNavigationBarColor(Color.BLACK);
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
        }
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false);
            View decor = window.getDecorView();
            decor.setOnApplyWindowInsetsListener((v, insets) -> {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                return insets;
            });
        }

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        LinearLayout center = new LinearLayout(this);
        center.setOrientation(LinearLayout.VERTICAL);
        center.setGravity(Gravity.CENTER_HORIZONTAL);

        orb = new OrbView(this);
        int orbSize = dp(330);
        center.addView(orb, new LinearLayout.LayoutParams(orbSize, orbSize));

        status = new TextView(this);
        status.setText("JARVIS PRONTO");
        status.setTextColor(Color.rgb(95, 205, 225));
        status.setTextSize(11f);
        status.setGravity(Gravity.CENTER);
        status.setLetterSpacing(0.18f);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        statusParams.topMargin = dp(10);
        center.addView(status, statusParams);

        FrameLayout.LayoutParams centerParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER);
        root.addView(center, centerParams);
        setContentView(root);

        orb.setOnClickListener(v -> activateJarvis());
        orb.setOnLongClickListener(v -> {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            return true;
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter(VoiceWakeService.ACTION_STATE);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(stateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(stateReceiver, filter);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!isSystemAssistant()) {
            orb.setMode(OrbView.Mode.IDLE);
            status.setText("TOCCA PER IMPOSTARE JARVIS COME ASSISTENTE");
        } else {
            status.setText("JARVIS PRONTO • ASSISTENTE DI SISTEMA");
        }
    }

    @Override
    protected void onStop() {
        try { unregisterReceiver(stateReceiver); } catch (Exception ignored) { }
        super.onStop();
    }

    private void activateJarvis() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            if (Build.VERSION.SDK_INT >= 33) {
                requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS}, REQ_PERMISSIONS);
            } else {
                requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_PERMISSIONS);
            }
            return;
        }

        if (!isSystemAssistant()) {
            openAssistantSettings();
            return;
        }

        startJarvisService();
    }

    private boolean isSystemAssistant() {
        try {
            ComponentName component = new ComponentName(this, JarvisVoiceInteractionService.class);
            return VoiceInteractionService.isActiveService(this, component);
        } catch (Exception ignored) {
            return false;
        }
    }

    private void openAssistantSettings() {
        status.setText("SELEZIONA JARVIS COME ASSISTENTE DIGITALE");
        try {
            Intent intent = new Intent(Settings.ACTION_VOICE_INPUT_SETTINGS);
            if (intent.resolveActivity(getPackageManager()) != null) {
                startActivity(intent);
                return;
            }
        } catch (Exception ignored) { }
        startActivity(new Intent(Settings.ACTION_SETTINGS));
    }

    private void startJarvisService() {
        Intent intent = new Intent(this, VoiceWakeService.class);
        intent.setAction(VoiceWakeService.ACTION_START);
        startForegroundService(intent);
        status.setText("AVVIO JARVIS…");
        orb.setMode(OrbView.Mode.THINKING);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_PERMISSIONS && checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            if (!isSystemAssistant()) openAssistantSettings();
            else startJarvisService();
        } else if (requestCode == REQ_PERMISSIONS) {
            status.setText("SERVE IL PERMESSO MICROFONO");
            orb.setMode(OrbView.Mode.ERROR);
        }
    }

    private void updateState(String state) {
        switch (state) {
            case "listening":
                orb.setMode(OrbView.Mode.LISTENING);
                status.setText("IN ASCOLTO • JARVIS / FERROVECCHIO");
                break;
            case "command":
                orb.setMode(OrbView.Mode.LISTENING);
                status.setText("DIMMI");
                break;
            case "thinking":
                orb.setMode(OrbView.Mode.THINKING);
                status.setText("ELABORAZIONE");
                break;
            case "speaking":
                orb.setMode(OrbView.Mode.SPEAKING);
                status.setText("RISPOSTA");
                break;
            case "error":
                orb.setMode(OrbView.Mode.ERROR);
                status.setText("CONTROLLO RICHIESTO");
                break;
            default:
                orb.setMode(OrbView.Mode.IDLE);
                status.setText(isSystemAssistant() ? "JARVIS PRONTO • ASSISTENTE DI SISTEMA" : "TOCCA PER IMPOSTARE JARVIS COME ASSISTENTE");
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
