package it.sculptura.jarvis;

import android.content.Context;
import android.os.BatteryManager;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class JarvisBrain {
    private JarvisBrain() { }

    public static String handle(Context context, String rawCommand) {
        if (rawCommand == null) return "Dimmi.";
        String raw = rawCommand.trim();
        String command = normalize(raw);
        if (command.isEmpty()) return "Dimmi.";

        if (command.startsWith("apri ")) {
            return AppController.openAppByLabel(context, raw.substring(Math.min(raw.length(), 5)).trim());
        }
        if (command.startsWith("avvia ")) {
            return AppController.openAppByLabel(context, raw.substring(Math.min(raw.length(), 6)).trim());
        }
        if (command.startsWith("cerca in rete ")) {
            return AppController.webSearch(context, raw.substring(Math.min(raw.length(), 14)).trim());
        }
        if (command.startsWith("cerca su internet ")) {
            return AppController.webSearch(context, raw.substring(Math.min(raw.length(), 17)).trim());
        }
        if (command.startsWith("cerca online ")) {
            return AppController.webSearch(context, raw.substring(Math.min(raw.length(), 13)).trim());
        }
        if (command.startsWith("cerca ")) {
            return AppController.webSearch(context, raw.substring(Math.min(raw.length(), 6)).trim());
        }

        if (command.startsWith("portami a ")) {
            return AppController.navigate(context, raw.substring(Math.min(raw.length(), 10)).trim());
        }
        if (command.startsWith("naviga verso ")) {
            return AppController.navigate(context, raw.substring(Math.min(raw.length(), 13)).trim());
        }
        if (command.startsWith("naviga a ")) {
            return AppController.navigate(context, raw.substring(Math.min(raw.length(), 9)).trim());
        }

        if (command.startsWith("chiama ")) {
            return AppController.dial(context, raw.substring(Math.min(raw.length(), 7)).trim());
        }
        if (command.startsWith("manda un sms a ")) {
            return AppController.composeSms(context, raw.substring(Math.min(raw.length(), 15)).trim(), "");
        }
        if (command.startsWith("manda sms a ")) {
            return AppController.composeSms(context, raw.substring(Math.min(raw.length(), 12)).trim(), "");
        }

        Matcher timer = Pattern.compile("(?:timer|conto alla rovescia)(?: di)? (\\d+) (secondi|minuti|ore)").matcher(command);
        if (timer.find()) {
            int value = Integer.parseInt(timer.group(1));
            String unit = timer.group(2);
            int seconds = value;
            if (unit.startsWith("minut")) seconds = value * 60;
            if (unit.startsWith("or")) seconds = value * 3600;
            return AppController.setTimer(context, seconds, "JARVIS");
        }

        Matcher alarm = Pattern.compile("(?:sveglia|allarme)(?: alle| per le)? (\\d{1,2})(?:[:.](\\d{1,2}))?").matcher(command);
        if (alarm.find()) {
            int hour = Integer.parseInt(alarm.group(1));
            int minute = alarm.group(2) == null ? 0 : Integer.parseInt(alarm.group(2));
            return AppController.setAlarm(context, hour, minute, "JARVIS");
        }

        if (command.equals("indietro") || command.equals("torna indietro")) {
            return JarvisAccessibilityService.globalBack() ? "Fatto." : accessibilityNeeded();
        }
        if (command.equals("home") || command.equals("vai alla home")) {
            return JarvisAccessibilityService.globalHome() ? "Fatto." : accessibilityNeeded();
        }
        if (command.equals("app recenti") || command.equals("recenti")) {
            return JarvisAccessibilityService.globalRecents() ? "Apro le applicazioni recenti." : accessibilityNeeded();
        }
        if (command.equals("notifiche") || command.equals("apri notifiche")) {
            return JarvisAccessibilityService.globalNotifications() ? "Apro le notifiche." : accessibilityNeeded();
        }
        if (command.equals("scorri giu") || command.equals("vai giu") || command.equals("pagina giu")) {
            return JarvisAccessibilityService.scrollForward() ? "Fatto." : accessibilityNeeded();
        }
        if (command.equals("scorri su") || command.equals("vai su") || command.equals("pagina su")) {
            return JarvisAccessibilityService.scrollBackward() ? "Fatto." : accessibilityNeeded();
        }
        if (command.startsWith("tocca ") || command.startsWith("premi ")) {
            int firstSpace = raw.indexOf(' ');
            String target = firstSpace < 0 ? "" : raw.substring(firstSpace + 1).trim();
            return JarvisAccessibilityService.clickText(target) ? "Fatto." : "Non vedo ancora l'elemento " + target + ".";
        }
        if (command.startsWith("scrivi ")) {
            String text = raw.substring(Math.min(raw.length(), 7)).trim();
            return JarvisAccessibilityService.writeText(text) ? "Fatto." : "Portami prima sul campo in cui vuoi scrivere.";
        }
        if (command.equals("cosa c e sullo schermo") || command.equals("leggi lo schermo") || command.equals("cosa vedi")) {
            String screen = JarvisAccessibilityService.describeScreen();
            return screen.isEmpty() ? accessibilityNeeded() : screen;
        }

        if (command.equals("che ore sono") || command.equals("dimmi l ora")) {
            return "Sono le " + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")) + ".";
        }
        if (command.equals("che giorno e") || command.equals("che data e") || command.equals("dimmi la data")) {
            String d = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.ITALIAN));
            return "Oggi è " + d + ".";
        }
        if (command.contains("batteria") && (command.contains("quanto") || command.contains("livello") || command.equals("batteria"))) {
            BatteryManager bm = context.getSystemService(BatteryManager.class);
            int pct = bm == null ? -1 : bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
            return pct >= 0 ? "La batteria è al " + pct + " per cento." : "Apro le impostazioni batteria.";
        }

        if (command.equals("alza volume") || command.equals("volume su")) {
            return AppController.adjustMediaVolume(context, true);
        }
        if (command.equals("abbassa volume") || command.equals("volume giu")) {
            return AppController.adjustMediaVolume(context, false);
        }
        if (command.equals("impostazioni") || command.equals("apri impostazioni")) {
            return AppController.openSettings(context);
        }
        if (command.contains("wifi")) {
            return AppController.openWifiSettings(context);
        }
        if (command.contains("bluetooth")) {
            return AppController.openBluetoothSettings(context);
        }
        if (command.contains("accessibilita") || command.contains("permessi avanzati")) {
            AppController.openAccessibilitySettings(context);
            return "Apro JARVIS Accessibilità.";
        }

        if (command.equals("chi sei") || command.equals("presentati")) {
            return "Sono JARVIS SCULPTURA, il tuo assistente vocale Android. Posso controllare applicazioni, navigazione, schermo, timer, sveglie e funzioni di sistema usando la voce.";
        }

        // Nessun muro: per le richieste non coperte da un'azione locale, apro una ricerca web pertinente.
        return AppController.webSearch(context, raw);
    }

    private static String accessibilityNeeded() {
        return "Attiva JARVIS Accessibilità e ripeti il comando.";
    }

    private static String normalize(String text) {
        if (text == null) return "";
        return java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ITALIAN)
                .replaceAll("[^a-z0-9:., ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
