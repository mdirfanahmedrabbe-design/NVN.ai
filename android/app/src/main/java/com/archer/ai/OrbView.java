package com.archer.ai;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Handler;
import android.util.AttributeSet;
import android.view.View;

public class OrbView extends View {
    public static final int IDLE = 0, LISTENING = 1, SPEAKING = 2;
    private int state = IDLE;
    private float t = 0f;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Handler handler = new Handler();
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            t += 0.03f;
            invalidate();
            handler.postDelayed(this, 33);
        }
    };

    public OrbView(Context c) { super(c); handler.post(tick); }
    public OrbView(Context c, AttributeSet a) { super(c, a); handler.post(tick); }

    public void setState(int s) { state = s; }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        float speed = state == SPEAKING ? 3.2f : state == LISTENING ? 2.0f : 1.0f;
        float pulse = 1f + 0.07f * (float) Math.sin(t * 2 * speed);
        float R = Math.min(cx, cy) * 0.55f * pulse;

        // outer glow
        RadialGradient g = new RadialGradient(cx, cy, R * 1.9f,
                new int[]{Color.argb(240, 233, 213, 255), Color.argb(190, 168, 85, 247),
                        Color.argb(90, 109, 40, 217), Color.argb(0, 109, 40, 217)},
                new float[]{0f, 0.35f, 0.7f, 1f}, Shader.TileMode.CLAMP);
        paint.setShader(g);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(cx, cy, R * 1.9f, paint);
        paint.setShader(null);

        // rotating arc segments (HUD ring)
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(5f);
        RectF ring = new RectF(cx - R * 1.55f, cy - R * 1.55f, cx + R * 1.55f, cy + R * 1.55f);
        float base = (float) Math.toDegrees(t * 0.5 * speed);
        paint.setColor(Color.argb(220, 192, 132, 252));
        canvas.drawArc(ring, base, 70f, false, paint);
        canvas.drawArc(ring, base + 180f, 70f, false, paint);
        paint.setColor(Color.argb(120, 168, 85, 247));
        canvas.drawArc(ring, base + 95f, 40f, false, paint);
        canvas.drawArc(ring, base + 275f, 40f, false, paint);

        // orbit ellipses
        paint.setStrokeWidth(3f);
        paint.setColor(Color.argb(200, 192, 132, 252));
        for (int i = 0; i < 3; i++) {
            canvas.save();
            canvas.rotate((float) Math.toDegrees(t * 0.3 * speed + i), cx, cy);
            canvas.drawOval(cx - R * (1.25f + i * 0.18f), cy - R * (0.42f + i * 0.1f),
                    cx + R * (1.25f + i * 0.18f), cy + R * (0.42f + i * 0.1f), paint);
            canvas.restore();
        }

        // core
        RadialGradient core = new RadialGradient(cx, cy, R,
                new int[]{Color.argb(255, 245, 243, 255), Color.argb(230, 168, 85, 247),
                        Color.argb(160, 76, 29, 149)},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP);
        paint.setShader(core);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(cx, cy, R, paint);
        paint.setShader(null);

        // nebula blobs (galaxy atmosphere)
        paint.setStyle(Paint.Style.FILL);
        RadialGradient neb1 = new RadialGradient(cx - R * 0.4f, cy - R * 0.3f, R * 1.1f,
                new int[]{Color.argb(90, 244, 114, 182), Color.argb(0, 244, 114, 182)},
                new float[]{0f, 1f}, Shader.TileMode.CLAMP);
        paint.setShader(neb1);
        canvas.drawCircle(cx - R * 0.4f, cy - R * 0.3f, R * 1.1f, paint);
        RadialGradient neb2 = new RadialGradient(cx + R * 0.45f, cy + R * 0.35f, R * 1.0f,
                new int[]{Color.argb(80, 96, 165, 250), Color.argb(0, 96, 165, 250)},
                new float[]{0f, 1f}, Shader.TileMode.CLAMP);
        paint.setShader(neb2);
        canvas.drawCircle(cx + R * 0.45f, cy + R * 0.35f, R * 1.0f, paint);
        paint.setShader(null);

        // spiral galaxy arms
        for (int arm = 0; arm < 2; arm++) {
            for (int i = 0; i < 42; i++) {
                double ang = arm * Math.PI + i * 0.21 + t * 0.22 * speed;
                double r = R * (0.12 + i * 0.026);
                float x = cx + (float) (r * Math.cos(ang));
                float y = cy + (float) (r * 0.62 * Math.sin(ang));
                float sz = 1.2f + (42 - i) * 0.09f;
                if (i % 3 == 0) paint.setColor(Color.argb(230, 255, 255, 255));
                else if (i % 3 == 1) paint.setColor(Color.argb(210, 244, 114, 182));
                else paint.setColor(Color.argb(210, 125, 211, 252));
                canvas.drawCircle(x, y, sz, paint);
            }
        }

        // particles
        paint.setColor(Color.argb(230, 233, 213, 255));
        for (int i = 0; i < 26; i++) {
            double a = t * (0.4 + (i % 5) * 0.13) * speed + i * 2.4;
            double r = R * (1.05 + 0.35 * Math.sin(t + i));
            canvas.drawCircle(cx + (float) (r * Math.cos(a)), cy + (float) (r * 0.55 * Math.sin(a)), 4f, paint);
        }
    }
}
