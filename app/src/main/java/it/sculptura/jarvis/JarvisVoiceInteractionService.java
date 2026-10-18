package it.sculptura.jarvis;

import android.Manifest;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.service.voice.VoiceInteractionService;

/**
 * System-facing assistant service. When the user selects JARVIS as the Android
 * assistant, the OS keeps this lightweight service bound so assistant launches
 * are fast and background voice operation is treated as an assistant workload.
 */
public class JarvisVoiceInteractionService extends VoiceInteractionService {

    @Override
    public void onReady() {
        super.onReady();

        if (!VoiceInteractionService.isActiveService(
                this,
                new ComponentName(this, JarvisVoiceInteractionService.class))) {
            return;
        }

        // Reuse the existing wake/command engine. Permissions are still granted
        // explicitly by the user; becoming assistant does not bypass them.
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
            try {
                Intent wake = new Intent(this, VoiceWakeService.class);
                wake.setAction(VoiceWakeService.ACTION_START);
                startForegroundService(wake);
            } catch (Exception ignored) {
                // MainActivity can start the engine if the OEM blocks this path.
            }
        }
    }
}
