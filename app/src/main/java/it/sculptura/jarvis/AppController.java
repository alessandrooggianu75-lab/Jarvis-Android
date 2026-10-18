package it.sculptura.jarvis;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
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
            return "Non trovo l'applicazione " + requested + ".";
        }

        Intent launch = pm.getLaunchIntentForPackage(best.activityInfo.packageName);
        if (launch == null) return "Non riesco ad aprire " + requested + ".";
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(launch);
        CharSequence label = best.loadLabel(pm);
        return "Apro " + (label == null ? requested : label.toString()) + ".";
    }

    public static String webSearch(Context context, String query) {
        if (query == null || query.trim().isEmpty()) return "Dimmi cosa devo cercare in rete.";
        Uri uri = Uri.parse("https://www.google.com/search?q=" + Uri.encode(query.trim()));
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
        return "Cerco in rete " + query.trim() + ".";
    }

    public static void openAccessibilitySettings(Context context) {
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }
}
