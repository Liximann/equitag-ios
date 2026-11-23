package de.kopfnummer.app;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.TextUtils;

/**
 * Zeichnet das Anzeigebild im Querformat in beliebiger Größe (z. B. 264 x 176 für 3:2,
 * 320 x 240 für 4:3) und bringt es in die native Ausrichtung des Displays.
 */
public final class LabelRenderer {
    /** Waveshare 2,7" im Querformat. */
    public static final int WS_WIDTH = 264;
    public static final int WS_HEIGHT = 176;
    /** Vorschau, solange das Display noch nicht erkannt ist (4:3). */
    public static final int PREVIEW_WIDTH = 320;
    public static final int PREVIEW_HEIGHT = 240;

    private LabelRenderer() {}

    /** Querformat-Bild: große Kopfnummer, Pferdename klein unten links. */
    public static Bitmap render(String number, String name, int width, int height) {
        Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.drawColor(Color.WHITE);
        float s = height / 176f; // Maßstab relativ zum ursprünglichen Layout

        Paint frame = new Paint(Paint.ANTI_ALIAS_FLAG);
        frame.setStyle(Paint.Style.STROKE);
        frame.setStrokeWidth(3f * s);
        frame.setColor(Color.BLACK);
        float inset = 4.5f * s;
        c.drawRoundRect(new RectF(inset, inset, width - inset, height - inset), 12f * s, 12f * s, frame);

        TextPaint namePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        namePaint.setColor(Color.BLACK);
        namePaint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        namePaint.setTextSize(15f * s);
        float nameBaseline = height - 13f * s;
        if (name != null && name.trim().length() > 0) {
            CharSequence shown = TextUtils.ellipsize(name.trim(), namePaint, width - 28f * s, TextUtils.TruncateAt.END);
            c.drawText(shown.toString(), 14f * s, nameBaseline, namePaint);
        }

        float left = 14f * s, right = width - 14f * s, top = 12f * s, bottom = height - 32f * s;
        float boxW = right - left, boxH = bottom - top;
        Paint numPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        numPaint.setColor(Color.BLACK);
        boolean narrow = number.length() >= 4 && boxW / boxH < 2.2f;
        numPaint.setTypeface(narrow
                ? Typeface.create("sans-serif-condensed", Typeface.BOLD)
                : Typeface.create("sans-serif-black", Typeface.NORMAL));
        Rect bounds = new Rect();
        numPaint.setTextSize(200f);
        numPaint.getTextBounds(number, 0, number.length(), bounds);
        float scale = Math.min(boxW / bounds.width(), boxH / bounds.height());
        numPaint.setTextSize(200f * scale);
        numPaint.getTextBounds(number, 0, number.length(), bounds);
        float x = left + (boxW - bounds.width()) / 2f - bounds.left;
        float y = top + (boxH - bounds.height()) / 2f - bounds.top;
        c.drawText(number, x, y, numPaint);

        int[] px = new int[width * height];
        bmp.getPixels(px, 0, width, 0, 0, width, height);
        for (int i = 0; i < px.length; i++) px[i] = isWhite(px[i]) ? Color.WHITE : Color.BLACK;
        bmp.setPixels(px, 0, width, 0, 0, width, height);
        return bmp;
    }

    private static boolean isWhite(int argb) {
        int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
        return (r * 299 + g * 587 + b * 114) / 1000 >= 128;
    }

    /**
     * Bild für ein Display mit nativer Größe devW x devH (zeilenweise, true = weiß).
     * Querformat-Displays bekommen das Bild direkt, Hochformat-Displays um 90° gegen
     * den Uhrzeigersinn gedreht. Optional zusätzlich 180° drehen und/oder invertieren.
     */
    public static boolean[] toDevice(String number, String name, int devW, int devH, boolean rotate180, boolean invert) {
        boolean portrait = devH > devW;
        int lw = portrait ? devH : devW;
        int lh = portrait ? devW : devH;
        Bitmap landscape = render(number, name, lw, lh);
        int[] px = new int[lw * lh];
        landscape.getPixels(px, 0, lw, 0, 0, lw, lh);
        boolean[] out = new boolean[devW * devH];
        for (int y = 0; y < devH; y++) {
            for (int x = 0; x < devW; x++) {
                int lx, ly;
                if (portrait) { lx = lw - 1 - y; ly = x; } else { lx = x; ly = y; }
                if (rotate180) { lx = lw - 1 - lx; ly = lh - 1 - ly; }
                boolean w = isWhite(px[ly * lw + lx]);
                out[y * devW + x] = invert != w;
            }
        }
        return out;
    }
}
