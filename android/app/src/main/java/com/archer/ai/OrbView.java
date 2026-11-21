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

    // points on a sphere (Fibonacci)
    private static final int N = 420;
    private final float[] px = new float[N], py = new float[N], pz = new float[N];

    public OrbView(Context c) { super(c); init(); }
    public OrbView(Context c, AttributeSet a) { super(c, a); init(); }

    private void init() {
        double golden = Math.PI * (3 - Math.sqrt(5));
        for (int i = 0; i < N; i++) {
            double y = 1 - (i / (double) (N - 1)) * 2;
            double r = Math.sqrt(1 - y * y);
            double theta = golden * i;
            px[i] = (float) (Math.cos(theta) * r);
            py[i] = (float) y;
            pz[i] = (float) (Math.sin(theta) * r);
        }
        handler.post(tick);
    }

    public void setState(int s) { state = s; }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() / 2f, cy = getHeight() / 2f - getHeight() * 0.04f;
        float speed = state == SPEAKING ? 3.2f : state == LISTENING ? 2.0f : 1.0f;
        float pulse = 1f + 0.05f * (float) Math.sin(t * 2 * speed);
        float R = Math.min(getWidth(), getHeight()) * 0.30f * pulse;

        // outer glow
        RadialGradient g = new RadialGradient(cx, cy, R * 2.1f,
                new int[]{Color.argb(200, 216, 180, 254), Color.argb(120, 168, 85, 247),
                        Color.argb(50, 109, 40, 217), Color.argb(0, 109, 40, 217)},
                new float[]{0f, 0.4f, 0.75f, 1f}, Shader.TileMode.CLAMP);
        paint.setShader(g);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(cx, cy, R * 2.1f, paint);
        paint.setShader(null);

        // pedestal rings under the ball
        paint.setStyle(Paint.Style.STROKE);
        float pedY = cy + R * 1.45f;
        for (int i = 0; i < 3; i++) {
            float w = R * (1.15f + i * 0.28f);
            float h = R * (0.16f + i * 0.05f);
            paint.setStrokeWidth(3f - i * 0.6f);
            paint.setColor(Color.argb(200 - i * 45, 192, 132, 252));
            canvas.drawOval(cx - w, pedY - h + i * R * 0.10f, cx + w, pedY + h + i * R * 0.10f, paint);
        }
        // pedestal glow
        RadialGradient pg = new RadialGradient(cx, pedY, R * 1.2f,
                new int[]{Color.argb(140, 216, 180, 254), Color.argb(0, 168, 85, 247)},
                new float[]{0f, 1f}, Shader.TileMode.CLAMP);
        paint.setShader(pg);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawOval(cx - R * 1.2f, pedY - R * 0.3f, cx + R * 1.2f, pedY + R * 0.3f, paint);
        paint.setShader(null);

        // dotted sphere (rotating)
        double rot = t * 0.35 * speed;
        double cosR = Math.cos(rot), sinR = Math.sin(rot);
        double tilt = 0.42;
        double cosT = Math.cos(tilt), sinT = Math.sin(tilt);
        paint.setStyle(Paint.Style.FILL);
        for (int i = 0; i < N; i++) {
            double x = px[i], y = py[i], z = pz[i];
            double x1 = x * cosR + z * sinR;
            double z1 = -x * sinR + z * cosR;
            double y1 = y * cosT - z1 * sinT;
            double z2 = y * sinT + z1 * cosT;
            float sx = cx + (float) (x1 * R);
            float sy = cy + (float) (y1 * R);
            float depth = (float) ((z2 + 1) / 2); // 0 back .. 1 front
            int alpha = (int) (60 + depth * 195);
            float sz = 1.1f + depth * 2.3f;
            if (i % 7 == 0) paint.setColor(Color.argb(alpha, 255, 255, 255));
            else if (i % 3 == 0) paint.setColor(Color.argb(alpha, 244, 114, 182));
            else paint.setColor(Color.argb(alpha, 216, 180, 254));
            canvas.drawCircle(sx, sy, sz, paint);
        }

        // bright core
        RadialGradient core = new RadialGradient(cx, cy, R * 0.55f,
                new int[]{Color.argb(255, 255, 255, 255), Color.argb(200, 233, 213, 255),
                        Color.argb(0, 168, 85, 247)},
                new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP);
        paint.setShader(core);
        canvas.drawCircle(cx, cy, R * 0.55f, paint);
        paint.setShader(null);

        // atom orbit rings with traveling nodes
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.4f);
        float[] tilts = {-28f, 18f, 62f};
        for (int i = 0; i < 3; i++) {
            canvas.save();
            canvas.rotate(tilts[i] + (float) Math.toDegrees(t * 0.06 * speed), cx, cy);
            float rx = R * 1.65f, ry = R * 0.62f;
            paint.setColor(Color.argb(210, 216, 180, 254));
            canvas.drawOval(cx - rx, cy - ry, cx + rx, cy + ry, paint);
            double a = t * (0.9 + i * 0.35) * speed + i * 2.1;
            float nx = cx + (float) (rx * Math.cos(a));
            float ny = cy + (float) (ry * Math.sin(a));
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(255, 255, 255, 255));
            canvas.drawCircle(nx, ny, 5f, paint);
            paint.setColor(Color.argb(120, 216, 180, 254));
            canvas.drawCircle(nx, ny, 10f, paint);
            paint.setStyle(Paint.Style.STROKE);
            canvas.restore();
        }

        // sparkles
        paint.setStyle(Paint.Style.FILL);
        for (int i = 0; i < 18; i++) {
            double a = t * (0.5 + (i % 4) * 0.15) * speed + i * 2.7;
            double r = R * (1.25 + 0.3 * Math.sin(t * 1.3 + i));
            int alpha = (int) (120 + 110 * Math.abs(Math.sin(t * 2 + i)));
            paint.setColor(Color.argb(alpha, 233, 213, 255));
            canvas.drawCircle(cx + (float) (r * Math.cos(a)), cy + (float) (r * 0.7 * Math.sin(a)), 2.6f, paint);
        }
    }
}
