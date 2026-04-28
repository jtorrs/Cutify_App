package com.example.online_alot;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * Vertical bars for hours 12 AM–11 PM (24 slots) with orange–yellow gradient and optional peak labels.
 */
public class PeakHoursBarChartView extends View {

    private static final int HOURS = 24;

    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint axisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    private final int[] counts = new int[HOURS];
    private int maxCount;
    private int labelHour1 = -1;
    private int labelHour2 = -1;

    public PeakHoursBarChartView(Context context) {
        super(context);
        init(context);
    }

    public PeakHoursBarChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public PeakHoursBarChartView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        labelPaint.setTextSize(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP, 8f, context.getResources().getDisplayMetrics()));
        labelPaint.setColor(0xFFFBBF24);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        axisPaint.setTextSize(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP, 7f, context.getResources().getDisplayMetrics()));
        axisPaint.setColor(0xFF6B7280);
        axisPaint.setTextAlign(Paint.Align.CENTER);
    }

    /**
     * @param hourly24 length 24, index = hour of day 0–23
     */
    public void setSeries(int[] hourly24, int maxHint) {
        if (hourly24 == null || hourly24.length < HOURS) {
            return;
        }
        System.arraycopy(hourly24, 0, counts, 0, HOURS);
        maxCount = 0;
        for (int n : counts) {
            maxCount = Math.max(maxCount, n);
        }
        if (maxHint > 0) {
            maxCount = Math.max(maxCount, maxHint);
        }
        maxCount = Math.max(1, maxCount);

        int best = 0;
        int bestH = 0;
        for (int i = 0; i < HOURS; i++) {
            if (counts[i] > best) {
                best = counts[i];
                bestH = i;
            }
        }
        labelHour1 = best > 0 ? bestH : -1;

        int secondBest = 0;
        int secondH = -1;
        for (int i = 0; i < HOURS; i++) {
            if (i == labelHour1) {
                continue;
            }
            if (counts[i] > secondBest) {
                secondBest = counts[i];
                secondH = i;
            }
        }
        labelHour2 = secondBest > 0 ? secondH : -1;

        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w > 0 && h > 0) {
            invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        if (w <= 2f || h <= 2f) {
            return;
        }
        float padBottom = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 22f, getResources().getDisplayMetrics());
        float padTop = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 26f, getResources().getDisplayMetrics());
        float chartH = h - padBottom - padTop;
        if (chartH < 4f) {
            return;
        }

        float barW = (w / HOURS) * 0.58f;
        float gap = (w / HOURS);

        for (int i = 0; i < HOURS; i++) {
            float cx = gap * i + gap / 2f;
            float bh = chartH * counts[i] / maxCount;
            float top = padTop + chartH - bh;
            float left = cx - barW / 2f;
            rect.set(left, top, left + barW, padTop + chartH);

            barPaint.setShader(new LinearGradient(
                    left, top, left, padTop + chartH,
                    0xFFF97316, 0xFFFBBF24, Shader.TileMode.CLAMP));
            canvas.drawRoundRect(rect, 4f, 4f, barPaint);
            barPaint.setShader(null);

            int hh = i;
            String ax = formatAxisHour(hh);
            canvas.drawText(ax, cx, h - 5f, axisPaint);

            if (counts[i] > 0 && (hh == labelHour1 || hh == labelHour2)) {
                canvas.drawText(String.valueOf(counts[i]), cx, Math.max(padTop * 0.4f, top - 5f), labelPaint);
            }
        }
    }

    private static String formatAxisHour(int hour24) {
        boolean pm = hour24 >= 12;
        int h = hour24 % 12;
        if (h == 0) {
            h = 12;
        }
        return h + (pm ? "p" : "a");
    }
}
