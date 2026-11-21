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

    // color palettes: {dotA r,g,b, dotB r,g,b, glow r,g,b}
    private static final int[][] PALETTES = {
        {216, 180, 254, 244, 114, 182, 168, 85, 247},   // purple-pink
        {125, 211, 252, 96, 165, 250, 59, 130, 246},    // blue
        {52, 211, 153, 45, 212, 191, 16, 185, 129},     // green-teal
        {252, 211, 77, 251, 146, 60, 245, 158, 11},     // gold-orange
        {240, 171, 252, 192, 132, 252, 217, 70, 239},   // magenta
    };

    private int[] currentPalette() {
        float cycle = 7f; // seconds per color
        float pos = (t * 0.03f * 30f) / cycle; // t advances 0.03 per ~33ms tick -> approx seconds = t
        pos = t / cycle;
        int idx = ((int) pos) % PALETTES.length;
        int next = (idx + 1) % PALETTES.length;
        float frac = pos - (int) pos;
        float blend = frac < 0.75f ? 0f : (frac - 0.75f) / 0.25f; // hold, then blend in last quarter
        int[] out = new int[9];
        for (int i = 0; i < 9; i++) {
            out[i] = (int) (PALETTES[idx][i] + (PALETTES[next][i] - PALETTES[idx][i]) * blend);
        }
        return out;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() / 2f, cy = getHeight() / 2f - getHeight() * 0.04f;
        float speed = state == SPEAKING ? 3.2f : state == LISTENING ? 2.0f : 1.0f;
        float pulse = 1f + 0.05f * (float) Math.sin(t * 2 * speed);
        float R = Math.min(getWidth(), getHeight()) * 0.30f * pulse;

        int[] pal = currentPalette();

        // outer glow
        RadialGradient g = new RadialGradient(cx, cy, R * 2.1f,
                new int[]{Color.argb(235, pal[0], pal[1], pal[2]), Color.argb(160, pal[6], pal[7], pal[8]),
                        Color.argb(70, pal[6], pal[7], pal[8]), Color.argb(0, pal[6], pal[7], pal[8])},
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
            paint.setColor(Color.argb(230 - i * 45, pal[0], pal[1], pal[2]));
            canvas.drawOval(cx - w, pedY - h + i * R * 0.10f, cx + w, pedY + h + i * R * 0.10f, paint);
        }
        // pedestal glow
        RadialGradient pg = new RadialGradient(cx, pedY, R * 1.2f,
                new int[]{Color.argb(180, pal[0], pal[1], pal[2]), Color.argb(0, pal[6], pal[7], pal[8])},
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
            int alpha = (int) (95 + depth * 160);
            float sz = 1.5f + depth * 2.9f;
            if (i % 7 == 0) paint.setColor(Color.argb(alpha, 255, 255, 255));
            else if (i % 3 == 0) paint.setColor(Color.argb(alpha, pal[3], pal[4], pal[5]));
            else paint.setColor(Color.argb(alpha, pal[0], pal[1], pal[2]));
            canvas.drawCircle(sx, sy, sz, paint);
        }

        // bright core
        RadialGradient core = new RadialGradient(cx, cy, R * 0.6f,
                new int[]{Color.argb(255, 255, 255, 255), Color.argb(235, pal[0], pal[1], pal[2]),
                        Color.argb(0, pal[6], pal[7], pal[8])},
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
            paint.setColor(Color.argb(240, pal[0], pal[1], pal[2]));
            canvas.drawOval(cx - rx, cy - ry, cx + rx, cy + ry, paint);
            double a = t * (0.9 + i * 0.35) * speed + i * 2.1;
            float nx = cx + (float) (rx * Math.cos(a));
            float ny = cy + (float) (ry * Math.sin(a));
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(255, 255, 255, 255));
            canvas.drawCircle(nx, ny, 5f, paint);
            paint.setColor(Color.argb(150, pal[0], pal[1], pal[2]));
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
            paint.setColor(Color.argb(alpha, pal[0], pal[1], pal[2]));
            canvas.drawCircle(cx + (float) (r * Math.cos(a)), cy + (float) (r * 0.7 * Math.sin(a)), 2.6f, paint);
        }
    }
}
