package it.sculptura.jarvis;

import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

public class OrbView extends View {
    public enum Mode { IDLE, LISTENING, THINKING, SPEAKING, ERROR }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arc = new RectF();
    private Mode mode = Mode.IDLE;
    private long startedAt = System.currentTimeMillis();

    public OrbView(Context context) {
        super(context);
        init();
    }

    public OrbView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setClickable(true);
        setFocusable(true);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        setContentDescription("Orb JARVIS");
    }

    public void setMode(Mode mode) {
        if (this.mode != mode) {
            this.mode = mode;
            startedAt = System.currentTimeMillis();
            invalidate();
        }
    }

    private int accent() {
        switch (mode) {
            case ERROR: return Color.rgb(255, 72, 72);
            case THINKING: return Color.rgb(79, 167, 255);
            case SPEAKING: return Color.rgb(115, 225, 255);
            case LISTENING: return Color.rgb(0, 230, 255);
            default: return Color.rgb(0, 190, 235);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        float cx = w / 2f;
        float cy = h / 2f;
        float size = Math.min(w, h);
        float r = size * 0.205f;
        float t = (System.currentTimeMillis() - startedAt) / 1000f;
        int color = accent();

        canvas.drawColor(Color.BLACK);

        float pulseSpeed = mode == Mode.LISTENING ? 3.0f : mode == Mode.SPEAKING ? 4.5f : 1.4f;
        float pulse = 1f + 0.045f * (float)Math.sin(t * pulseSpeed);

        paint.setStyle(Paint.Style.FILL);
        paint.setMaskFilter(new BlurMaskFilter(size * 0.08f, BlurMaskFilter.Blur.NORMAL));
        paint.setColor(Color.argb(mode == Mode.IDLE ? 35 : 58, Color.red(color), Color.green(color), Color.blue(color)));
        canvas.drawCircle(cx, cy, r * 1.55f * pulse, paint);
        paint.setMaskFilter(null);

        paint.setShader(new RadialGradient(
                cx, cy, r,
                new int[]{Color.argb(40, 255, 255, 255), Color.argb(115, Color.red(color), Color.green(color), Color.blue(color)), Color.argb(8, 0, 0, 0)},
                new float[]{0f, 0.46f, 1f},
                Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, r * pulse, paint);
        paint.setShader(null);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);

        paint.setStrokeWidth(size * 0.006f);
        paint.setColor(Color.argb(215, Color.red(color), Color.green(color), Color.blue(color)));
        canvas.drawCircle(cx, cy, r * 1.03f, paint);

        paint.setStrokeWidth(size * 0.0042f);
        paint.setColor(Color.argb(155, Color.red(color), Color.green(color), Color.blue(color)));
        canvas.drawCircle(cx, cy, r * 1.28f, paint);

        paint.setStrokeWidth(size * 0.0026f);
        paint.setColor(Color.argb(105, Color.red(color), Color.green(color), Color.blue(color)));
        canvas.drawCircle(cx, cy, r * 1.52f, paint);

        float outer = r * 1.73f;
        arc.set(cx - outer, cy - outer, cx + outer, cy + outer);
        float rotation = (t * (mode == Mode.THINKING ? 95f : 28f)) % 360f;
        paint.setStrokeWidth(size * 0.007f);
        paint.setColor(Color.argb(205, Color.red(color), Color.green(color), Color.blue(color)));
        for (int i = 0; i < 8; i++) {
            float start = rotation + i * 45f;
            float sweep = (i % 2 == 0) ? 18f : 8f;
            canvas.drawArc(arc, start, sweep, false, paint);
        }

        float outer2 = r * 1.93f;
        arc.set(cx - outer2, cy - outer2, cx + outer2, cy + outer2);
        paint.setStrokeWidth(size * 0.003f);
        paint.setColor(Color.argb(88, Color.red(color), Color.green(color), Color.blue(color)));
        float reverse = (-t * 17f) % 360f;
        for (int i = 0; i < 12; i++) {
            canvas.drawArc(arc, reverse + i * 30f, 10f, false, paint);
        }

        if (mode == Mode.LISTENING || mode == Mode.SPEAKING) {
            paint.setStrokeWidth(size * 0.003f);
            paint.setColor(Color.argb(145, 215, 250, 255));
            for (int i = 0; i < 3; i++) {
                float wave = r * (1.10f + i * 0.15f + 0.04f * (float)Math.sin(t * 4f + i));
                canvas.drawCircle(cx, cy, wave, paint);
            }
        }

        postInvalidateOnAnimation();
    }
}
