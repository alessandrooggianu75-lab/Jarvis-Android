package it.sculptura.jarvis;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.media.AudioManager;
import android.net.Uri;
import android.provider.AlarmClock;
import android.provider.Settings;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

public final class AppController {
    private AppController() { }

    private static String normalize(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ITALIAN)
                .trim();
        return n.replaceAll("\\s+", " ");
    }

    public static String openAppByLabel(Context context, String requested) {
        String target = normalize(requested);
        if (target.isEmpty()) return "Dimmi quale applicazione devo aprire.";

        PackageManager pm = context.getPackageManager();
        Intent query = new Intent(Intent.ACTION_MAIN);
        query.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> apps = pm.queryIntentActivities(query, PackageManager.MATCH_ALL);

        ResolveInfo best = null;
        int bestScore = Integer.MIN_VALUE;
        for (ResolveInfo info : apps) {
            CharSequence labelSeq = info.loadLabel(pm);
            String label = normalize(labelSeq == null ? "" : labelSeq.toString());
            int score = -1000;
            if (label.equals(target)) score = 100;
            else if (label.startsWith(target) || target.startsWith(label)) score = 70;
            else if (label.contains(target) || target.contains(label)) score = 45;
            if (score > bestScore) {
                bestScore = score;
                best = info;
            }
        }

        if (best == null || bestScore < 0) {
            return webSearch(context, requested);
        }

        Intent launch = pm.getLaunchIntentForPackage(best.activityInfo.packageName);
        if (launch == null) return webSearch(context, requested);
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(launch);
        CharSequence label = best.loadLabel(pm);
        return "Apro " + (label == null ? requested : label.toString()) + ".";
    }

    public static String webSearch(Context context, String query) {
        if (query == null || query.trim().isEmpty()) return "Dimmi cosa vuoi sapere.";
        Uri uri = Uri.parse("https://www.google.com/search?q=" + Uri.encode(query.trim()));
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
        return "Cerco " + query.trim() + ".";
    }

    public static String navigate(Context context, String destination) {
        if (destination == null || destination.trim().isEmpty()) return "Dimmi la destinazione.";
        Uri uri = Uri.parse("google.navigation:q=" + Uri.encode(destination.trim()));
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        intent.setPackage("com.google.android.apps.maps");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(intent);
        } catch (Exception e) {
            Intent fallback = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://www.google.com/maps/dir/?api=1&destination=" + Uri.encode(destination.trim())));
            fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(fallback);
        }
        return "Avvio la navigazione verso " + destination.trim() + ".";
    }

    public static String dial(Context context, String target) {
        if (target == null || target.trim().isEmpty()) return "Dimmi chi devo chiamare.";
        String cleaned = target.replaceAll("[^0-9+]", "");
        Intent intent;
        if (!cleaned.isEmpty() && cleaned.length() >= 3) {
            intent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(cleaned)));
        } else {
            intent = new Intent(Intent.ACTION_DIAL);
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(intent);
            return cleaned.isEmpty() ? "Apro il telefono per chiamare " + target + "." : "Preparo la chiamata a " + target + ".";
        } catch (Exception e) {
            return webSearch(context, target);
        }
    }

    public static String composeSms(Context context, String target, String body) {
        String cleaned = target == null ? "" : target.replaceAll("[^0-9+]", "");
        Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(cleaned)));
        if (body != null && !body.isEmpty()) intent.putExtra("sms_body", body);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(intent);
            return "Apro i messaggi.";
        } catch (Exception e) {
            return "Apro una ricerca per " + target + ".";
        }
    }

    public static String setTimer(Context context, int seconds, String label) {
        Intent intent = new Intent(AlarmClock.ACTION_SET_TIMER)
                .putExtra(AlarmClock.EXTRA_LENGTH, Math.max(1, seconds))
                .putExtra(AlarmClock.EXTRA_MESSAGE, label)
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(intent);
            return "Timer impostato.";
        } catch (Exception e) {
            return "Apro l'orologio.";
        }
    }

    public static String setAlarm(Context context, int hour, int minute, String label) {
        hour = Math.max(0, Math.min(23, hour));
        minute = Math.max(0, Math.min(59, minute));
        Intent intent = new Intent(AlarmClock.ACTION_SET_ALARM)
                .putExtra(AlarmClock.EXTRA_HOUR, hour)
                .putExtra(AlarmClock.EXTRA_MINUTES, minute)
                .putExtra(AlarmClock.EXTRA_MESSAGE, label)
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(intent);
            return String.format(Locale.ITALIAN, "Sveglia impostata alle %02d:%02d.", hour, minute);
        } catch (Exception e) {
            return "Apro l'orologio.";
        }
    }

    public static String adjustMediaVolume(Context context, boolean up) {
        AudioManager audio = context.getSystemService(AudioManager.class);
        if (audio == null) return "Apro le impostazioni audio.";
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                up ? AudioManager.ADJUST_RAISE : AudioManager.ADJUST_LOWER,
                AudioManager.FLAG_SHOW_UI);
        return up ? "Alzo il volume." : "Abbasso il volume.";
    }

    public static String openSettings(Context context) {
        open(context, Settings.ACTION_SETTINGS);
        return "Apro le impostazioni.";
    }

    public static String openWifiSettings(Context context) {
        open(context, Settings.ACTION_WIFI_SETTINGS);
        return "Apro le impostazioni Wi-Fi.";
    }

    public static String openBluetoothSettings(Context context) {
        open(context, Settings.ACTION_BLUETOOTH_SETTINGS);
        return "Apro le impostazioni Bluetooth.";
    }

    public static void openAccessibilitySettings(Context context) {
        open(context, Settings.ACTION_ACCESSIBILITY_SETTINGS);
    }

    private static void open(Context context, String action) {
        Intent intent = new Intent(action);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }
}
