package it.sculptura.jarvis;

import android.content.Context;
import android.graphics.Color;
import android.service.voice.VoiceInteractionSession;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Lightweight assistant overlay shown when Android invokes JARVIS as the
 * selected assistant. It deliberately reuses the same Iron-Man-inspired orb
 * language as the main app instead of presenting a separate dashboard.
 */
public class JarvisVoiceInteractionSession extends VoiceInteractionSession {

    public JarvisVoiceInteractionSession(Context context) {
        super(context);
    }

    @Override
    public View onCreateContentView() {
        Context context = getContext();

        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(Color.TRANSPARENT);

        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER_HORIZONTAL);
        panel.setPadding(dp(18), dp(18), dp(18), dp(24));

        OrbView orb = new OrbView(context);
        orb.setMode(OrbView.Mode.LISTENING);
        int orbSize = dp(230);
        panel.addView(orb, new LinearLayout.LayoutParams(orbSize, orbSize));

        TextView label = new TextView(context);
        label.setText("JARVIS IN ASCOLTO");
        label.setTextColor(Color.rgb(95, 205, 225));
        label.setTextSize(11f);
        label.setGravity(Gravity.CENTER);
        label.setLetterSpacing(0.18f);
        panel.addView(label);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        params.bottomMargin = dp(26);
        root.addView(panel, params);

        return root;
    }

    private int dp(int value) {
        return Math.round(value * getContext().getResources().getDisplayMetrics().density);
    }
}
