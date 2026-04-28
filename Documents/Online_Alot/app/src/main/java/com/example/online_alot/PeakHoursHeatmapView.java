package com.example.online_alot;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * 7×12 grid (Mon–Sun × 9 AM–8 PM) with blue / orange / red intensity by volume.
 */
public class PeakHoursHeatmapView extends View {

    private final Paint cellPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    private int[][] data = new int[7][12];
    private int maxVal;

    public PeakHoursHeatmapView(Context context) {
        super(context);
        init(context);
    }

    public PeakHoursHeatmapView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public PeakHoursHeatmapView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        textPaint.setTextSize(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP, 9f, context.getResources().getDisplayMetrics()));
        textPaint.setColor(0xFF9CA3AF);
    }

    public void setHeatmap(int[][] heat712, int maxForScale) {
        for (int r = 0; r < 7; r++) {
            for (int c = 0; c < 12; c++) {
                data[r][c] = 0;
            }
        }
        if (heat712 == null || heat712.length != 7) {
            maxVal = 1;
            invalidate();
            return;
        }
        maxVal = 0;
        for (int r = 0; r < 7; r++) {
            int len = Math.min(12, heat712[r].length);
            for (int c = 0; c < len; c++) {
                data[r][c] = heat712[r][c];
                maxVal = Math.max(maxVal, heat712[r][c]);
            }
        }
        if (maxForScale > 0) {
            maxVal = Math.max(maxVal, maxForScale);
        }
        maxVal = Math.max(1, maxVal);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        float labelW = w * 0.12f;
        float gridW = w - labelW;
        float cellH = h / 8f;
        float cellW = gridW / 12f;

        String[] dayLabels = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
        for (int row = 0; row < 7; row++) {
            float y0 = cellH * (row + 1);
            textPaint.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(dayLabels[row], labelW - 6f, y0 + cellH * 0.65f, textPaint);
            for (int col = 0; col < 12; col++) {
                int v = data[row][col];
                float intensity = maxVal > 0 ? (float) v / maxVal : 0f;
                int c = heatColor(intensity, v == 0);
                cellPaint.setColor(c);
                float x0 = labelW + col * cellW;
                rect.set(x0 + 1f, y0 + 1f, x0 + cellW - 1f, y0 + cellH - 1f);
                float rad = 3f;
                canvas.drawRoundRect(rect, rad, rad, cellPaint);
            }
        }

        textPaint.setTextAlign(Paint.Align.CENTER);
        for (int col = 0; col < 12; col++) {
            int hour = 9 + col;
            String lab = formatHourShort(hour);
            float x = labelW + col * cellW + cellW / 2f;
            canvas.drawText(lab, x, cellH * 0.7f, textPaint);
        }
    }

    private static String formatHourShort(int hour24) {
        boolean pm = hour24 >= 12;
        int h = hour24 % 12;
        if (h == 0) {
            h = 12;
        }
        return h + (pm ? "p" : "a");
    }

    /** Blue (low) → orange (mid) → red (high). */
    private static int heatColor(float t, boolean empty) {
        if (empty) {
            return 0xFF1E2836;
        }
        t = Math.max(0f, Math.min(1f, t));
        if (t < 0.34f) {
            float u = t / 0.34f;
            return blend(0xFF1E3A5F, 0xFF3B82F6, u);
        }
        if (t < 0.67f) {
            float u = (t - 0.34f) / 0.33f;
            return blend(0xFF3B82F6, 0xFFF97316, u);
        }
        float u = (t - 0.67f) / 0.33f;
        return blend(0xFFF97316, 0xFFEF4444, u);
    }

    private static int blend(int a, int b, float t) {
        int ra = (a >> 16) & 0xFF;
        int ga = (a >> 8) & 0xFF;
        int bla = a & 0xFF;
        int rb = (b >> 16) & 0xFF;
        int gb = (b >> 8) & 0xFF;
        int bb = b & 0xFF;
        int r = (int) (ra + (rb - ra) * t);
        int g = (int) (ga + (gb - ga) * t);
        int bl = (int) (bla + (bb - bla) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }
}
