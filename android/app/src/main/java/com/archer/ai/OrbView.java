package com.archer.ai;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
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

        RadialGradient g = new RadialGradient(cx, cy, R * 1.9f,
                new int[]{Color.argb(240, 186, 230, 253), Color.argb(190, 56, 189, 248),
                        Color.argb(90, 2, 132, 199), Color.argb(0, 2, 132, 199)},
                new float[]{0f, 0.35f, 0.7f, 1f}, Shader.TileMode.CLAMP);
        paint.setShader(g);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(cx, cy, R * 1.9f, paint);
        paint.setShader(null);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3f);
        paint.setColor(Color.argb(200, 125, 211, 252));
        for (int i = 0; i < 3; i++) {
            canvas.save();
            canvas.rotate((float) Math.toDegrees(t * 0.3 * speed + i), cx, cy);
            canvas.drawOval(cx - R * (1.25f + i * 0.18f), cy - R * (0.42f + i * 0.1f),
                    cx + R * (1.25f + i * 0.18f), cy + R * (0.42f + i * 0.1f), paint);
            canvas.restore();
        }

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(230, 186, 230, 253));
        for (int i = 0; i < 26; i++) {
            double a = t * (0.4 + (i % 5) * 0.13) * speed + i * 2.4;
            double r = R * (1.05 + 0.35 * Math.sin(t + i));
            canvas.drawCircle(cx + (float) (r * Math.cos(a)), cy + (float) (r * 0.55 * Math.sin(a)), 4f, paint);
        }
    }
}
