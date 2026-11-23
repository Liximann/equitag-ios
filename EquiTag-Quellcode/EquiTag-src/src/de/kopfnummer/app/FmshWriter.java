/*
 * NFC-Übertragung an E-Ink-Displays mit FMSH-Chip (Fudan Microelectronics),
 * z. B. 3,52"-Displays des neuen Lieferanten. Eigene Implementierung des
 * Ablaufs, den die Hersteller-App "ELS" verwendet (zur Interoperabilität
 * analysiert): Geräteinfo lesen, Bildebene(n) senden, Aktualisieren, Status abfragen.
 */
package de.kopfnummer.app;

import android.nfc.tech.IsoDep;

import java.io.IOException;

final class FmshWriter {

    static final class Info {
        int width, height;
        int refreshScan;      // 0 = vertikal, sonst horizontal
        int size = 1;         // Anzahl Bildebenen
        int colorCount = 2;
        int black, white, red, yellow;
        boolean compress, pin, fourColor;

        @Override public String toString() {
            return width + "x" + height + " c" + colorCount + " s" + size + " scan" + refreshScan
                    + " b" + black + " w" + white + (compress ? " lzo" : "");
        }
    }

    private FmshWriter() {}

    /** Protokoll aller Befehle und Antworten (zum Teilen bei Fehlern). */
    static StringBuilder log = new StringBuilder();

    static byte[] tx(IsoDep iso, byte[] cmd, String step) throws IOException {
        String c = toHex(cmd);
        log.append(step).append(" >> ").append(c.length() > 40 ? c.substring(0, 40) + "…(" + cmd.length + " B)" : c).append('\n');
        try {
            byte[] r = iso.transceive(cmd);
            log.append(step).append(" << ").append(toHex(r)).append('\n');
            return r;
        } catch (IOException e) {
            log.append(step).append(" !! ").append(e.getClass().getSimpleName()).append(": ").append(e.getMessage()).append('\n');
            throw e;
        }
    }

    static void write(IsoDep iso, EpaperWriter.ImageSource source, EpaperWriter.Progress p)
            throws IOException, EpaperWriter.WriteException {
        if (!iso.isConnected()) iso.connect();
        iso.setTimeout(50000);
        log.append("maxTransceive=").append(iso.getMaxTransceiveLength())
                .append(" extLen=").append(iso.isExtendedLengthApduSupported())
                .append(" hist=").append(toHex(iso.getHistoricalBytes()))
                .append(" hiLayer=").append(toHex(iso.getHiLayerResponse())).append('\n');

        tx(iso, hex("00A4040007D2760000850101"), "select"); // Antwort wird (wie in der Hersteller-App) nicht geprüft
        byte[] r1 = tx(iso, hex("00D1000000"), "info1");
        byte[] r2 = tx(iso, hex("F0D8000005000000000E"), "info2");
        Info info = parse(concat(r1, r2));
        log.append("info: ").append(info).append(" pin=").append(info.pin).append('\n');
        if (info.width <= 0 || info.height <= 0) {
            throw new EpaperWriter.WriteException("err_step", "info " + sw(r1));
        }

        p.onDeviceInfo(info.width, info.height, info.colorCount);
        if (info.pin) throw new EpaperWriter.WriteException("err_pin");
        if (info.fourColor || info.colorCount < 2 || info.colorCount > 3) {
            throw new EpaperWriter.WriteException("err_unsupported", info.colorCount + " / " + info.width + "x" + info.height);
        }
        p.onProgress(4, "stage_connected");

        boolean[] white = source.render(info.width, info.height);
        int rowCount = (int) Math.ceil(((info.width * info.height) / 250) / 8.0d);

        if (info.colorCount == 2) {
            byte[] data = blackWhitePlane(info, white);
            if (info.compress) {
                // Nur echte Bilddaten senden – keine Füllbytes über die Displaygröße hinaus
                int realW = (info.width + 7) / 8;
                int frameLen = info.refreshScan == 0 ? realW * 8 * ((info.height + 7) / 8) : realW * info.height;
                data = java.util.Arrays.copyOf(data, Math.min(frameLen, data.length));
                log.append("frameLen=").append(data.length).append('\n');
            }
            sendPlane(iso, info, data, rowCount, 0, p, 5, 75);
        } else {
            byte[][] planes = threeColorPlanes(info, white);
            if (info.size >= 2) {
                sendPlane(iso, info, planes[0], rowCount, 0, p, 5, 40);
                sendPlane(iso, info, planes[1], rowCount, 1, p, 45, 35);
            } else {
                sendPlane(iso, info, planes[0], rowCount, 0, p, 5, 75);
            }
        }
        refresh(iso, p);
    }

    // ------------------------------------------------------------------ Geräteinfo (TLV)

    static Info parse(byte[] b) {
        Info info = new Info();
        int i = 0;
        while (i + 1 < b.length) {
            int tag = b[i] & 0xFF;
            int len = b[i + 1] & 0xFF;
            if (tag == 0xA0 && i + 8 < b.length) {
                info.height = ((b[i + 5] & 0xFF) << 8) | (b[i + 6] & 0xFF);
                info.width = ((b[i + 7] & 0xFF) << 8) | (b[i + 8] & 0xFF);
            } else if (tag == 0xA1 && i + 3 < b.length) {
                info.refreshScan = b[i + 2];
                if ((b[i + 3] & 0xFF) != 0xE0) {
                    info.size = (b[i + 3] >> 4) & 0xFF;
                    info.colorCount = b[i + 3] & 0x0F;
                    int shift = 6 - info.colorCount;
                    int mask = info.colorCount == 2 ? 1 : 3;
                    for (int k = 0; k < info.colorCount && i + 4 + k < b.length; k++) {
                        int c = b[i + 4 + k];
                        int id = (c >> 5) & 0x07;
                        int v = shift >= 0 ? (c >> shift) & mask : 0;
                        setColor(info, id, v);
                    }
                }
            } else if (tag == 0xD1 && i + 2 < b.length) {
                info.compress = b[i + 2] != 0;
            } else if (tag == 0xE0 && i + 2 < b.length) {
                info.size = (b[i + 2] >> 4) & 0x0F;
                info.colorCount = b[i + 2] & 0x0F;
                for (int k = 0; k < info.colorCount && i + 5 + 2 * k < b.length; k++) {
                    setColor(info, b[i + 4 + 2 * k] & 0xFF, b[i + 5 + 2 * k]);
                }
            }
            i += len + 2;
        }
        int n = b.length;
        if (n >= 2 && (b[n - 2] & 0xFF) == 0x90 && b[n - 1] == 0x00) {
            if (n >= 16) {
                String desc = new String(b, n - 16, 14);
                if ("4_color Screen".equals(desc)) info.fourColor = true;
            }
        } else if (n >= 2 && (b[n - 2] & 0xFF) == 0x69 && (b[n - 1] & 0xFF) == 0x85) {
            info.pin = true;
        }
        return info;
    }

    private static void setColor(Info info, int id, int v) {
        switch (id) {
            case 0: info.black = v; break;
            case 1: info.white = v; break;
            case 2: info.red = v; break;
            case 3: info.yellow = v; break;
            default: break;
        }
    }

    // ------------------------------------------------------------------ Bilddaten

    /** Schwarz/Weiß-Displays: 1 Bit pro Pixel, Farbcodes wie in der Hersteller-App. */
    static byte[] blackWhitePlane(Info info, boolean[] white) {
        int w = info.width, h = info.height;
        int realW = (w + 7) / 8;                       // Bytes mit Bilddaten pro Zeile
        int stride = realW % 4 == 0 ? realW : realW - realW % 4 + 4;
        int lightBit = info.black > 0 ? 1 : 0;         // so ordnet die Hersteller-App die Codes zu
        int darkBit = info.white > 0 ? 1 : 0;
        byte[][] rows = new byte[h][stride];
        int tail = w % 8;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int bit = white[y * w + x] ? lightBit : darkBit;
                if (bit != 0) rows[y][x >> 3] |= (byte) (0x80 >>> (x & 7));
            }
            // Wie die Hersteller-App: Füllbits des letzten Teilbytes stammen aus dem vorherigen Byte
            if (tail != 0 && w >= 8) {
                int last = w >> 3;
                int padMask = 0xFF >>> tail;
                rows[y][last] = (byte) ((rows[y][last] & ~padMask) | (rows[y][last - 1] & padMask));
            }
        }
        int extraRows = 8 - (h % 8);
        int total = stride * h + 62 + stride * extraRows;
        byte[] out = new byte[total];
        if (info.refreshScan == 0) {
            int vRows = (h + 7) / 8;
            for (int x = 0; x < realW * 8; x++) {
                for (int j = 0; j < vRows; j++) {
                    int v = 0;
                    for (int k = 0; k < 8; k++) {
                        int y = j * 8 + k;
                        if (y < h && ((rows[y][x >> 3] >> (7 - (x & 7))) & 1) != 0) v |= 0x80 >>> k;
                    }
                    out[x * vRows + j] = (byte) v;
                }
            }
        } else {
            for (int y = 0; y < h; y++) System.arraycopy(rows[y], 0, out, y * realW, realW);
        }
        return out;
    }

    /** Displays mit 3 Farben: zwei Ebenen, Farbcode (2 Bit) je Pixel. */
    static byte[][] threeColorPlanes(Info info, boolean[] white) {
        int w = info.width, h = info.height;
        byte[] p0 = new byte[w * h], p1 = new byte[w * h];
        for (int i = 0; i < w * h; i++) {
            int code = white[i] ? info.white : info.black;
            p0[i] = (byte) (code > 1 ? 1 : 0);
            p1[i] = (byte) (code % 2 == 1 ? 1 : 0);
        }
        return new byte[][]{scan(info, p0), scan(info, p1)};
    }

    private static byte[] scan(Info info, byte[] src) {
        int w = info.width, h = info.height;
        if (info.refreshScan == 0) {
            int destH = (h + 7) / 8;
            byte[] out = new byte[destH * w];
            for (int x = 0; x < w; x++) {
                for (int j = 0; j < destH; j++) {
                    int v = 0;
                    for (int k = 0; k < 8; k++) {
                        int y = j * 8 + k;
                        if (y < h && src[y * w + x] != 0) v |= 0x80 >>> k;
                    }
                    out[x * destH + j] = (byte) v;
                }
            }
            return out;
        }
        // Horizontal: Zeile für Zeile, Pixel von rechts nach links (Füllbits nach dem ersten Pixel)
        int rem = w % 8 == 0 ? 0 : 8 - w % 8;
        int destW = (w + rem) / 8;
        byte[] out = new byte[destW * h];
        byte[] seq = new byte[w + rem];
        for (int y = 0; y < h; y++) {
            int p = 0;
            for (int x = w - 1; x >= 0; x--) {
                seq[p++] = src[y * w + x];
                if (x == w - 1) for (int k = 0; k < rem; k++) seq[p++] = 0;
            }
            for (int bIdx = 0; bIdx < destW; bIdx++) {
                int v = 0;
                for (int k = 0; k < 8; k++) if (seq[bIdx * 8 + k] != 0) v |= 0x80 >>> k;
                out[y * destW + bIdx] = (byte) v;
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ Senden

    private static void sendPlane(IsoDep iso, Info info, byte[] data, int rowCount, int screen,
                                  EpaperWriter.Progress p, int pStart, int pSpan)
            throws IOException, EpaperWriter.WriteException {
        if (info.compress) {
            sendCompressed(iso, data, screen, p, pStart, pSpan);
            return;
        }
        byte[] padded = new byte[Math.max(data.length, rowCount * 250)];
        System.arraycopy(data, 0, padded, 0, data.length);
        byte[] apdu = new byte[255];
        apdu[0] = (byte) 0xF0;
        apdu[1] = (byte) 0xD2;
        apdu[2] = (byte) screen;
        apdu[4] = (byte) 0xFA;
        for (int i = 0; i < rowCount; i++) {
            apdu[3] = (byte) i;
            System.arraycopy(padded, i * 250, apdu, 5, 250);
            expect9000(tx(iso, apdu, "data" + screen + "." + i), "data" + screen + "." + i);
            p.onProgress(pStart + (i + 1) * pSpan / rowCount, "stage_transfer");
        }
    }

    /**
     * Komprimierte Übertragung (F0 D3): jeder 2000-Byte-Block LZO1X-komprimiert.
     */
    private static void sendCompressed(IsoDep iso, byte[] data, int screen,
                                       EpaperWriter.Progress p, int pStart, int pSpan)
            throws IOException, EpaperWriter.WriteException {
        int blocks = (data.length + 1999) / 2000;
        for (int blk = 0; blk < blocks; blk++) {
            int start = blk * 2000;
            int len = Math.min(2000, data.length - start);
            byte[] lzo = Lzo1x.compress(data, start, len);
            int chunks = (lzo.length + 249) / 250;
            for (int c = 0; c < chunks; c++) {
                int off = c * 250;
                int n = Math.min(250, lzo.length - off);
                byte[] apdu = new byte[7 + n];
                apdu[0] = (byte) 0xF0;
                apdu[1] = (byte) 0xD3;
                apdu[2] = (byte) screen;
                apdu[3] = (byte) (c == chunks - 1 ? 1 : 0);
                apdu[4] = (byte) (n + 2);
                apdu[5] = (byte) blk;
                apdu[6] = (byte) c;
                System.arraycopy(lzo, off, apdu, 7, n);
                expect9000(tx(iso, apdu, "lzo" + screen + "." + blk + "." + c), "lzo" + blk + "." + c);
            }
            p.onProgress(pStart + (blk + 1) * pSpan / blocks, "stage_transfer");
        }
    }

    static byte[] lzoLiteral(byte[] src, int start, int n) {
        byte[] head;
        if (n <= 238) {
            head = new byte[]{(byte) (17 + n)};
        } else {
            int tt = n - 18;
            int zeros = 0;
            while (tt > 255) { tt -= 255; zeros++; }
            head = new byte[2 + zeros];
            head[head.length - 1] = (byte) tt; // head[0..zeros] = 0
        }
        byte[] out = new byte[head.length + n + 3];
        System.arraycopy(head, 0, out, 0, head.length);
        System.arraycopy(src, start, out, head.length, n);
        out[out.length - 3] = 0x11; // Endmarke
        return out;
    }

    // ------------------------------------------------------------------ Aktualisieren

    private static void refresh(IsoDep iso, EpaperWriter.Progress p) throws IOException, EpaperWriter.WriteException {
        p.onProgress(82, "stage_refresh");
        String r = toHex(tx(iso, new byte[]{(byte) 0xF0, (byte) 0xD4, 0x05, (byte) 0x80, 0x00}, "refresh"));
        if (r.equals("698A")) throw new EpaperWriter.WriteException("err_step", "refresh " + r);
        if (r.equals("6986") || r.equals("68C6")) {
            for (int attempt = 0; attempt < 5; attempt++) {
                sleep(300);
                String rr = toHex(tx(iso, hex("F0D4850000"), "refresh-retry"));
                if (rr.equals("9000") || rr.equals("009000")) { pollUntilDone(iso, p, true); return; }
            }
            throw new EpaperWriter.WriteException("err_screen");
        }
        if (r.equals("019000")) { pollUntilDone(iso, p, true); return; }
        if (!r.equals("009000") && !r.equals("9000")) {
            String rr = toHex(tx(iso, new byte[]{(byte) 0xF0, (byte) 0xD4, 0x05, 0x00, 0x00}, "refresh2"));
            if (!rr.endsWith("9000")) throw new EpaperWriter.WriteException("err_step", "refresh " + r + "/" + rr);
        }
        pollUntilDone(iso, p, false);
    }

    /** Fragt F0 DE ab: 00 9000 = fertig, 01 9000 = noch beschäftigt. */
    private static void pollUntilDone(IsoDep iso, EpaperWriter.Progress p, boolean lenient)
            throws IOException, EpaperWriter.WriteException {
        byte[] cmd = {(byte) 0xF0, (byte) 0xDE, 0x00, 0x00, 0x01};
        for (int i = 0; i < 1000; i++) {
            String r;
            try {
                r = toHex(i < 3 || i % 20 == 0 ? tx(iso, cmd, "status") : iso.transceive(cmd));
            } catch (IOException e) {
                if (lenient) return;
                throw e;
            }
            if (r.equals("009000")) { p.onProgress(100, "done"); return; }
            if (r.equals("019000")) {
                if (i % 10 == 0) p.onProgress(Math.min(98, 84 + i / 10), "stage_refresh");
                sleep(100);
                continue;
            }
            if (lenient) return;
            if (r.equals("6986") || r.equals("68C6")) throw new EpaperWriter.WriteException("err_screen");
            throw new EpaperWriter.WriteException("err_step", "status " + r);
        }
        throw new EpaperWriter.WriteException("err_no_confirm");
    }

    // ------------------------------------------------------------------ Helfer

    private static void expect9000(byte[] r, String step) throws EpaperWriter.WriteException {
        if (!endsWith9000(r)) throw new EpaperWriter.WriteException("err_step", step + " " + sw(r));
    }

    private static String sw(byte[] r) {
        String h = toHex(r);
        return h.length() == 0 ? "-" : (h.length() > 8 ? h.substring(h.length() - 8) : h);
    }

    private static boolean endsWith9000(byte[] r) {
        return r != null && r.length >= 2 && (r[r.length - 2] & 0xFF) == 0x90 && r[r.length - 1] == 0x00;
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    static byte[] hex(String s) {
        byte[] out = new byte[s.length() / 2];
        for (int i = 0; i < out.length; i++) out[i] = (byte) Integer.parseInt(s.substring(2 * i, 2 * i + 2), 16);
        return out;
    }

    static String toHex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        if (b != null) for (byte x : b) sb.append(String.format("%02X", x & 0xFF));
        return sb.toString();
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
    }
}
