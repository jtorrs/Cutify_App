package com.example.online_alot;

import android.content.ContentResolver;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * On-device PDF using {@link android.graphics.pdf.PdfDocument}. Super Admin export only in this app.
 */
public final class PdfExportHelper {

    private static final String TAG = "PdfExportHelper";

    private static final int PAGE_W = 595;
    private static final int PAGE_H = 842;
    /** Generous margins for readability */
    private static final float MARGIN = 52f;
    private static final float CONTENT_RIGHT = PAGE_W - MARGIN;
    private static final float CONTENT_WIDTH = PAGE_W - 2f * MARGIN;

    /* Palette: navy + warm gold + slate (high contrast on white paper) */
    private static final int COLOR_PAGE_INK = 0xFF1E293B;
    private static final int COLOR_BODY = 0xFF334155;
    private static final int COLOR_MUTED = 0xFF64748B;
    private static final int COLOR_HEADER_BG = 0xFF0F172A;
    private static final int COLOR_HEADER_GOLD = 0xFFFBBF24;
    private static final int COLOR_HEADER_SUB = 0xFFE2E8F0;
    private static final int COLOR_SECTION = 0xFF0F766E;
    private static final int COLOR_SECTION_LINE = 0xFF14B8A6;
    private static final int COLOR_RULE = 0xFFCBD5E1;
    private static final int COLOR_STAT_VALUE = 0xFF0F172A;

    private PdfExportHelper() {}

    /** One row of content for a styled Super Admin report. */
    public static final class PdfSegment {
        public enum Kind {
            TITLE,
            SUBTITLE,
            SECTION,
            BODY,
            BULLET,
            STAT_ROW,
            HR,
            SPACER
        }

        public final Kind kind;
        public final String text;

        public PdfSegment(Kind kind, String text) {
            this.kind = kind;
            this.text = text != null ? text : "";
        }
    }

    public static boolean writeStyledSegmentsToUri(ContentResolver cr, Uri uri, List<PdfSegment> segments) {
        try {
            byte[] data = buildStyledPdf(segments);
            return writeBytesToUri(cr, uri, data);
        } catch (IOException e) {
            Log.e(TAG, "writeStyledSegmentsToUri", e);
            return false;
        }
    }

    public static boolean writeBytesToUri(ContentResolver cr, Uri uri, byte[] pdfBytes) {
        if (pdfBytes == null || pdfBytes.length == 0) {
            return false;
        }
        try (OutputStream os = cr.openOutputStream(uri)) {
            if (os == null) {
                return false;
            }
            os.write(pdfBytes);
            return true;
        } catch (IOException e) {
            Log.e(TAG, "writeBytesToUri", e);
            return false;
        }
    }

    private static byte[] buildStyledPdf(@NonNull List<PdfSegment> segments) throws IOException {
        PdfDocument document = new PdfDocument();

        Paint titlePaint = textPaint(COLOR_PAGE_INK, 23f, Typeface.BOLD);
        titlePaint.setLetterSpacing(0.02f);

        Paint subPaint = textPaint(COLOR_MUTED, 12f, Typeface.NORMAL);

        Paint sectionPaint = textPaint(COLOR_SECTION, 13.5f, Typeface.BOLD);
        sectionPaint.setLetterSpacing(0.08f);

        Paint bodyPaint = textPaint(COLOR_BODY, 12.2f, Typeface.NORMAL);
        bodyPaint.setLetterSpacing(0.01f);

        Paint statLabelPaint = textPaint(COLOR_MUTED, 12f, Typeface.NORMAL);
        Paint statValuePaint = textPaint(COLOR_STAT_VALUE, 17f, Typeface.BOLD);

        Paint rulePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        rulePaint.setColor(COLOR_RULE);
        rulePaint.setStrokeWidth(1.25f);
        rulePaint.setStyle(Paint.Style.STROKE);

        Paint accentLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        accentLinePaint.setColor(COLOR_SECTION_LINE);
        accentLinePaint.setStrokeWidth(2.5f);
        accentLinePaint.setStrokeCap(Paint.Cap.ROUND);

        Paint headerBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        headerBgPaint.setColor(COLOR_HEADER_BG);

        Paint headerBrandPaint = textPaint(COLOR_HEADER_GOLD, 13f, Typeface.BOLD);
        headerBrandPaint.setLetterSpacing(0.28f);

        int nextPageNum = 1;
        PdfDocument.Page page = startPage(document, nextPageNum++);
        Canvas canvas = page.getCanvas();
        float y = MARGIN;
        boolean drewBand = false;
        final float gap = 4.5f;

        for (PdfSegment seg : segments) {
            switch (seg.kind) {
                case SPACER:
                    y += 14f;
                    break;
                case HR:
                    y += 6f;
                    PageCtx ctx = ensurePage(document, page, canvas, nextPageNum, y, 18f);
                    page = ctx.page;
                    canvas = ctx.canvas;
                    nextPageNum = ctx.nextPageNum;
                    y = ctx.y;
                    canvas.drawLine(MARGIN, y, CONTENT_RIGHT, y, rulePaint);
                    y += 16f;
                    break;
                case TITLE:
                    if (!drewBand) {
                        float bandH = 78f;
                        canvas.drawRect(0f, 0f, PAGE_W, bandH, headerBgPaint);
                        float goldLine = 4f;
                        Paint goldBar = new Paint(Paint.ANTI_ALIAS_FLAG);
                        goldBar.setColor(COLOR_HEADER_GOLD);
                        canvas.drawRect(0f, bandH - goldLine, PAGE_W, bandH, goldBar);

                        String brand = "CUTIFY";
                        float brandSize = headerBrandPaint.getTextSize();
                        float brandBaseline = bandH * 0.38f + brandSize * 0.35f;
                        drawTextCentered(canvas, brand, headerBrandPaint, brandBaseline);

                        Paint tagPaint = textPaint(COLOR_HEADER_SUB, 11f, Typeface.NORMAL);
                        tagPaint.setAlpha(220);
                        String tag = "Barbershop · Book & Style";
                        drawTextCentered(canvas, tag, tagPaint, bandH * 0.72f);

                        y = bandH + 28f;
                        drewBand = true;
                    }
                    PageCtx t = drawWrappedCenter(document, page, canvas, nextPageNum, titlePaint, seg.text, y, gap);
                    page = t.page;
                    canvas = t.canvas;
                    nextPageNum = t.nextPageNum;
                    y = t.y + 16f;
                    break;
                case SUBTITLE:
                    PageCtx s = drawWrappedCenter(document, page, canvas, nextPageNum, subPaint, seg.text, y, gap);
                    page = s.page;
                    canvas = s.canvas;
                    nextPageNum = s.nextPageNum;
                    y = s.y + 10f;
                    break;
                case SECTION:
                    y += 8f;
                    PageCtx sec = drawWrapped(document, page, canvas, nextPageNum, sectionPaint, seg.text, y, gap);
                    page = sec.page;
                    canvas = sec.canvas;
                    nextPageNum = sec.nextPageNum;
                    y = sec.y;
                    canvas.drawLine(MARGIN, y + 5f, CONTENT_RIGHT, y + 5f, accentLinePaint);
                    y += 18f;
                    break;
                case BODY:
                    PageCtx b = drawWrapped(document, page, canvas, nextPageNum, bodyPaint, seg.text, y, gap);
                    page = b.page;
                    canvas = b.canvas;
                    nextPageNum = b.nextPageNum;
                    y = b.y;
                    break;
                case BULLET:
                    PageCtx bu = drawWrapped(document, page, canvas, nextPageNum, bodyPaint, "  •  " + seg.text, y, gap);
                    page = bu.page;
                    canvas = bu.canvas;
                    nextPageNum = bu.nextPageNum;
                    y = bu.y;
                    break;
                case STAT_ROW: {
                    String raw = seg.text;
                    int tab = raw.indexOf('\t');
                    String left = tab >= 0 ? raw.substring(0, tab).trim() : raw;
                    String right = tab >= 0 ? raw.substring(tab + 1).trim() : "";
                    float rowH = Math.max(textLineHeight(statLabelPaint), textLineHeight(statValuePaint)) + 14f;
                    PageCtx r = ensurePage(document, page, canvas, nextPageNum, y, rowH);
                    page = r.page;
                    canvas = r.canvas;
                    nextPageNum = r.nextPageNum;
                    y = r.y;
                    float base = y + Math.max(textBaselineOffset(statLabelPaint), textBaselineOffset(statValuePaint));
                    canvas.drawText(left, MARGIN, base, statLabelPaint);
                    float rw = statValuePaint.measureText(right);
                    canvas.drawText(right, CONTENT_RIGHT - rw, base, statValuePaint);
                    y += rowH;
                    break;
                }
                default:
                    break;
            }
        }

        document.finishPage(page);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        document.writeTo(out);
        document.close();
        return out.toByteArray();
    }

    private static void drawTextCentered(Canvas canvas, String text, Paint paint, float baselineY) {
        if (text == null || text.isEmpty()) {
            return;
        }
        float w = paint.measureText(text);
        canvas.drawText(text, (PAGE_W - w) / 2f, baselineY, paint);
    }

    private static final class PageCtx {
        PdfDocument.Page page;
        Canvas canvas;
        int nextPageNum;
        float y;
    }

    private static PageCtx ensurePage(PdfDocument document, PdfDocument.Page page, Canvas canvas,
                                      int nextPageNum, float y, float needHeight) {
        PageCtx c = new PageCtx();
        if (y + needHeight > PAGE_H - MARGIN) {
            document.finishPage(page);
            c.page = startPage(document, nextPageNum);
            c.canvas = c.page.getCanvas();
            c.nextPageNum = nextPageNum + 1;
            c.y = MARGIN;
        } else {
            c.page = page;
            c.canvas = canvas;
            c.nextPageNum = nextPageNum;
            c.y = y;
        }
        return c;
    }

    /** Left-aligned wrapped text (body, sections). */
    private static PageCtx drawWrapped(PdfDocument document, PdfDocument.Page page, Canvas canvas,
                                       int nextPageNum, Paint paint, String text, float y, float lineGap) {
        PageCtx c = new PageCtx();
        c.page = page;
        c.canvas = canvas;
        c.nextPageNum = nextPageNum;
        c.y = y;
        for (String line : wrapLines(paint, text, CONTENT_WIDTH)) {
            float lh = textLineHeight(paint) + lineGap;
            PageCtx room = ensurePage(document, c.page, c.canvas, c.nextPageNum, c.y, lh);
            c.page = room.page;
            c.canvas = room.canvas;
            c.nextPageNum = room.nextPageNum;
            c.y = room.y;
            c.canvas.drawText(line, MARGIN, c.y + textBaselineOffset(paint), paint);
            c.y += lh;
        }
        return c;
    }

    /** Horizontally centered wrapped text (title, subtitle). */
    private static PageCtx drawWrappedCenter(PdfDocument document, PdfDocument.Page page, Canvas canvas,
                                             int nextPageNum, Paint paint, String text, float y, float lineGap) {
        PageCtx c = new PageCtx();
        c.page = page;
        c.canvas = canvas;
        c.nextPageNum = nextPageNum;
        c.y = y;
        for (String line : wrapLines(paint, text, CONTENT_WIDTH)) {
            float lh = textLineHeight(paint) + lineGap;
            PageCtx room = ensurePage(document, c.page, c.canvas, c.nextPageNum, c.y, lh);
            c.page = room.page;
            c.canvas = room.canvas;
            c.nextPageNum = room.nextPageNum;
            c.y = room.y;
            float w = paint.measureText(line);
            float x = (PAGE_W - w) / 2f;
            c.canvas.drawText(line, x, c.y + textBaselineOffset(paint), paint);
            c.y += lh;
        }
        return c;
    }

    private static Paint textPaint(int color, float size, int style) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        p.setTextSize(size);
        p.setTypeface(Typeface.create(Typeface.SANS_SERIF, style));
        return p;
    }

    private static PdfDocument.Page startPage(PdfDocument document, int number) {
        PdfDocument.PageInfo info = new PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, number).create();
        return document.startPage(info);
    }

    private static float textLineHeight(Paint p) {
        Paint.FontMetrics fm = p.getFontMetrics();
        return fm.descent - fm.ascent;
    }

    private static float textBaselineOffset(Paint p) {
        Paint.FontMetrics fm = p.getFontMetrics();
        return -fm.ascent;
    }

    private static List<String> wrapLines(Paint paint, String text, float maxWidth) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            out.add("");
            return out;
        }
        String rest = text;
        while (!rest.isEmpty()) {
            int n = paint.breakText(rest, true, maxWidth, null);
            if (n <= 0) {
                n = 1;
            }
            out.add(rest.substring(0, n));
            rest = rest.substring(n);
        }
        return out;
    }
}
