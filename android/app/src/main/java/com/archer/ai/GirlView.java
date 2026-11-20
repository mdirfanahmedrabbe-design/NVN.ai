package com.archer.ai;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.View;

public class GirlView extends View {
    public static final int MOOD_SMILE = 0;
    public static final int MOOD_LAUGH = 1;
    public static final int MOOD_SAD = 2;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final long startMs = System.currentTimeMillis();

    private boolean talking = false;
    private int mood = MOOD_SMILE;
    private long waveUntil = 0;
    private long danceUntil = 0;
    private long jumpUntil = 0;
    private boolean running = false;

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (!running) return;
            invalidate();
            handler.postDelayed(this, 50);
        }
    };

    public GirlView(Context context) {
        super(context);
    }

    public GirlView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setTalking(boolean t) {
        talking = t;
    }

    public void setMood(int m) {
        mood = m;
    }

    public void wave() {
        waveUntil = System.currentTimeMillis() + 3000;
    }

    public void dance() {
        danceUntil = System.currentTimeMillis() + 6000;
        mood = MOOD_LAUGH;
    }

    public void jump() {
        jumpUntil = System.currentTimeMillis() + 2500;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        running = true;
        handler.post(ticker);
    }

    @Override
    protected void onDetachedFromWindow() {
        running = false;
        handler.removeCallbacks(ticker);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) return;
        float s = Math.min(w, h) / 300f;
        float cx = w / 2f;
        double t = (System.currentTimeMillis() - startMs) / 1000.0;
        float bob = (float) Math.sin(t * 2.0) * 2.5f * s;
        boolean dancing = System.currentTimeMillis() < danceUntil;
        boolean jumping = System.currentTimeMillis() < jumpUntil;
        if (dancing) bob -= (float) Math.abs(Math.sin(t * 6.0)) * 10f * s;
        if (jumping) bob -= (float) Math.abs(Math.sin(t * 5.0)) * 18f * s;
        float groundY = h * 0.97f + bob;
        float faceY = groundY - 205f * s;
        boolean waving = System.currentTimeMillis() < waveUntil;
        boolean blink = (System.currentTimeMillis() % 3400) < 130;
        if (dancing) {
            canvas.save();
            canvas.rotate((float) Math.sin(t * 3.0) * 6f, cx, groundY);
        }

        // light glow
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFA855F7);
        paint.setShadowLayer(22f * s, 0, 0, 0xFFA855F7);

        // twin tails (long hair both sides)
        paint.setColor(0xD95B21B6);
        float swayL = (float) Math.sin(t * 2.2) * 4f * s;
        float swayR = (float) Math.cos(t * 2.2) * 4f * s;
        rect.set(cx - 92 * s + swayL, faceY - 40 * s, cx - 58 * s + swayL, groundY - 8 * s);
        canvas.drawRoundRect(rect, 16 * s, 16 * s, paint);
        rect.set(cx + 58 * s + swayR, faceY - 40 * s, cx + 92 * s + swayR, groundY - 8 * s);
        canvas.drawRoundRect(rect, 16 * s, 16 * s, paint);
        // hair ties
        paint.setColor(0xFFF0ABFC);
        canvas.drawCircle(cx - 62 * s + swayL, faceY - 38 * s, 7 * s, paint);
        canvas.drawCircle(cx + 62 * s + swayR, faceY - 38 * s, 7 * s, paint);

        // back hair
        paint.setColor(0xD95B21B6);
        rect.set(cx - 66 * s, faceY - 58 * s, cx + 66 * s, groundY - 30 * s);
        canvas.drawRoundRect(rect, 55 * s, 55 * s, paint);

        // body (dress)
        paint.setColor(0xD94C1D95);
        path.reset();
        path.moveTo(cx - 52 * s, groundY);
        path.lineTo(cx - 38 * s, groundY - 108 * s);
        path.quadTo(cx, groundY - 124 * s, cx + 38 * s, groundY - 108 * s);
        path.lineTo(cx + 52 * s, groundY);
        path.close();
        canvas.drawPath(path, paint);
        // sparkle dots on dress
        paint.setColor(0xFFE9D5FF);
        canvas.drawCircle(cx - 16 * s, groundY - 60 * s, 2.2f * s, paint);
        canvas.drawCircle(cx + 12 * s, groundY - 42 * s, 1.8f * s, paint);
        canvas.drawCircle(cx + 2 * s, groundY - 84 * s, 1.6f * s, paint);
        // atom logo
        paint.setColor(0xFF22D3EE);
        canvas.drawCircle(cx, groundY - 78 * s, 5 * s, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.6f * s);
        rect.set(cx - 13 * s, groundY - 84 * s, cx + 13 * s, groundY - 72 * s);
        canvas.drawOval(rect, paint);

        // left arm (down, or up while dancing)
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xD94C1D95);
        canvas.save();
        if (dancing) {
            canvas.rotate(150f + (float) Math.sin(t * 6.0) * 25f, cx - 42 * s, groundY - 100 * s);
        } else {
            canvas.rotate(12, cx - 42 * s, groundY - 100 * s);
        }
        rect.set(cx - 52 * s, groundY - 102 * s, cx - 34 * s, groundY - 38 * s);
        canvas.drawRoundRect(rect, 9 * s, 9 * s, paint);
        canvas.restore();
        paint.setColor(0xFFFFE0BD);
        canvas.drawCircle(cx - 49 * s, groundY - 34 * s, 8 * s, paint);

        // right arm (waving or down)
        paint.setColor(0xD94C1D95);
        canvas.save();
        if (dancing) {
            canvas.rotate(-150f + (float) Math.cos(t * 6.0) * 25f, cx + 42 * s, groundY - 100 * s);
        } else if (waving) {
            float ang = -150f + (float) Math.sin(t * 10.0) * 22f;
            canvas.rotate(ang, cx + 42 * s, groundY - 100 * s);
        } else {
            canvas.rotate(-12, cx + 42 * s, groundY - 100 * s);
        }
        rect.set(cx + 34 * s, groundY - 102 * s, cx + 52 * s, groundY - 38 * s);
        canvas.drawRoundRect(rect, 9 * s, 9 * s, paint);
        paint.setColor(0xFFFFE0BD);
        canvas.drawCircle(cx + 43 * s, groundY - 34 * s, 8 * s, paint);
        canvas.restore();

        // neck + face
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFF5C99B);
        rect.set(cx - 9 * s, faceY + 38 * s, cx + 9 * s, faceY + 62 * s);
        canvas.drawRoundRect(rect, 4 * s, 4 * s, paint);
        paint.setColor(0xFFFFE0BD);
        rect.set(cx - 42 * s, faceY - 48 * s, cx + 42 * s, faceY + 48 * s);
        canvas.drawOval(rect, paint);

        // front hair (bangs)
        paint.setColor(0xE67C3AED);
        path.reset();
        path.moveTo(cx - 44 * s, faceY - 6 * s);
        path.quadTo(cx - 46 * s, faceY - 54 * s, cx, faceY - 54 * s);
        path.quadTo(cx + 46 * s, faceY - 54 * s, cx + 44 * s, faceY - 6 * s);
        path.quadTo(cx + 26 * s, faceY - 26 * s, cx + 12 * s, faceY - 14 * s);
        path.quadTo(cx, faceY - 28 * s, cx - 12 * s, faceY - 14 * s);
        path.quadTo(cx - 26 * s, faceY - 26 * s, cx - 44 * s, faceY - 6 * s);
        path.close();
        canvas.drawPath(path, paint);
        // side hair strands
        rect.set(cx - 48 * s, faceY - 20 * s, cx - 36 * s, faceY + 52 * s);
        canvas.drawRoundRect(rect, 6 * s, 6 * s, paint);
        rect.set(cx + 36 * s, faceY - 20 * s, cx + 48 * s, faceY + 52 * s);
        canvas.drawRoundRect(rect, 6 * s, 6 * s, paint);

        paint.clearShadowLayer();

        // eyes
        float eyeY = faceY + 2 * s;
        if (mood == MOOD_LAUGH) {
            paint.setColor(0xFF2A1A3E);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3f * s);
            paint.setStrokeCap(Paint.Cap.ROUND);
            rect.set(cx - 24 * s, eyeY - 6 * s, cx - 8 * s, eyeY + 6 * s);
            canvas.drawArc(rect, 180, 180, false, paint);
            rect.set(cx + 8 * s, eyeY - 6 * s, cx + 24 * s, eyeY + 6 * s);
            canvas.drawArc(rect, 180, 180, false, paint);
            paint.setStyle(Paint.Style.FILL);
        } else {
            float ry = blink ? 0.9f * s : 7.5f * s;
            paint.setColor(0xFF2A1A3E);
            rect.set(cx - 21 * s, eyeY - ry, cx - 11 * s, eyeY + ry);
            canvas.drawOval(rect, paint);
            rect.set(cx + 11 * s, eyeY - ry, cx + 21 * s, eyeY + ry);
            canvas.drawOval(rect, paint);
            if (!blink) {
                paint.setColor(Color.WHITE);
                canvas.drawCircle(cx - 14.5f * s, eyeY - 2.5f * s, 2.2f * s, paint);
                canvas.drawCircle(cx + 17.5f * s, eyeY - 2.5f * s, 2.2f * s, paint);
            }
        }

        // blush
        paint.setColor(0x59FF8FA3);
        rect.set(cx - 34 * s, faceY + 12 * s, cx - 18 * s, faceY + 20 * s);
        canvas.drawOval(rect, paint);
        rect.set(cx + 18 * s, faceY + 12 * s, cx + 34 * s, faceY + 20 * s);
        canvas.drawOval(rect, paint);

        // mouth
        float mouthY = faceY + 22 * s;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(3f * s);
        if (talking) {
            float open = (3f + 3.5f * (float) Math.abs(Math.sin(t * 16.0))) * s;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFF7A2E3F);
            rect.set(cx - 8 * s, mouthY - open, cx + 8 * s, mouthY + open);
            canvas.drawOval(rect, paint);
            paint.setColor(0xFFFF8FA3);
            rect.set(cx - 5 * s, mouthY + open * 0.2f, cx + 5 * s, mouthY + open * 0.9f);
            canvas.drawOval(rect, paint);
        } else if (mood == MOOD_LAUGH) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFF7A2E3F);
            rect.set(cx - 11 * s, mouthY - 4 * s, cx + 11 * s, mouthY + 10 * s);
            canvas.drawArc(rect, 0, 180, true, paint);
            paint.setColor(0xFFFF8FA3);
            rect.set(cx - 6 * s, mouthY + 3 * s, cx + 6 * s, mouthY + 9 * s);
            canvas.drawOval(rect, paint);
        } else if (mood == MOOD_SAD) {
            paint.setColor(0xFF7A2E3F);
            rect.set(cx - 9 * s, mouthY + 2 * s, cx + 9 * s, mouthY + 14 * s);
            canvas.drawArc(rect, 180, 180, false, paint);
        } else {
            paint.setColor(0xFF7A2E3F);
            rect.set(cx - 10 * s, mouthY - 8 * s, cx + 10 * s, mouthY + 6 * s);
            canvas.drawArc(rect, 15, 150, false, paint);
        }
        paint.setStyle(Paint.Style.FILL);

        // sparkles
        paint.setColor(0xFFC084FC);
        float tw = (float) (0.5 + 0.5 * Math.sin(t * 3.0));
        paint.setAlpha((int) (90 + 120 * tw));
        canvas.drawCircle(cx - 62 * s, faceY - 30 * s, 2.6f * s, paint);
        canvas.drawCircle(cx + 64 * s, faceY - 8 * s, 2.2f * s, paint);
        canvas.drawCircle(cx + 58 * s, faceY - 46 * s, 1.8f * s, paint);
        canvas.drawCircle(cx - 78 * s, groundY - 90 * s, 2.0f * s, paint);
        canvas.drawCircle(cx + 80 * s, groundY - 60 * s, 2.4f * s, paint);
        paint.setAlpha(255);
        if (dancing) canvas.restore();
    }
}
