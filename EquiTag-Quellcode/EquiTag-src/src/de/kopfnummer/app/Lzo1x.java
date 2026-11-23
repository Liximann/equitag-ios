package de.kopfnummer.app;

/**
 * Kompakter LZO1X-Kompressor (eigene Implementierung). Erzeugt einen gültigen
 * LZO1X-Datenstrom aus Literal-Läufen und M3-Treffern (Abstand bis 16384 Bytes),
 * abgeschlossen mit der Endmarke 11 00 00. Für die 2000-Byte-Blöcke der Displays
 * reicht das; weiße Flächen schrumpfen auf wenige Bytes.
 */
final class Lzo1x {
    private static final int MIN_MATCH = 3;
    /** Max. Trefferlänge ohne Längen-Erweiterungsbytes (t <= 31). */
    private static final int MAX_MATCH = 33;
    private static final int MAX_DIST = 16384;
    private static final int HASH_BITS = 12;

    private Lzo1x() {}

    static byte[] compress(byte[] src, int start, int n) {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(n + n / 16 + 64);
        int[] head = new int[1 << HASH_BITS];
        java.util.Arrays.fill(head, -1);
        int[] prev = new int[n];

        int litStart = 0;          // Beginn der noch nicht geschriebenen Literale
        boolean first = true;      // noch kein Befehl geschrieben
        int lastStatePos = -1;     // Position des Offset-Bytes des letzten Treffers (für 1–3 Literale)
        byte[] buf = null;
        int i = 0;
        while (i < n) {
            int bestLen = 0, bestDist = 0;
            if (i + MIN_MATCH <= n) {
                int h = hash(src, start + i);
                int cand = head[h];
                int tries = 0;
                while (cand >= 0 && i - cand <= MAX_DIST && tries++ < 64) {
                    int len = 0;
                    while (i + len < n && len < MAX_MATCH && src[start + cand + len] == src[start + i + len]) len++;
                    if (len > bestLen) { bestLen = len; bestDist = i - cand; }
                    cand = prev[cand];
                }
            }
            if (bestLen >= MIN_MATCH) {
                int litLen = i - litStart;
                // Literale vor dem Treffer schreiben
                if (first) {
                    writeFirstLiterals(out, src, start + litStart, litLen);
                    first = false;
                } else if (litLen > 0) {
                    if (litLen <= 3) {
                        buf = setState(out, lastStatePos, litLen);
                        out.write(src, start + litStart, litLen);
                    } else {
                        writeLiteralRun(out, src, start + litStart, litLen);
                    }
                }
                // M3-Treffer
                int t = bestLen - 2;
                if (t <= 31) {
                    out.write(32 | t);
                } else {
                    out.write(32);
                    int tt = t - 31;
                    while (tt > 255) { out.write(0); tt -= 255; }
                    out.write(tt);
                }
                int d = bestDist - 1;
                lastStatePos = out.size();
                out.write((d & 63) << 2);
                out.write(d >> 6);
                // Hashtabelle für alle Positionen im Treffer aktualisieren
                int end = i + bestLen;
                for (; i < end; i++) insert(src, start, i, n, head, prev);
                litStart = i;
            } else {
                insert(src, start, i, n, head, prev);
                i++;
            }
        }
        int litLen = n - litStart;
        if (first) {
            writeFirstLiterals(out, src, start + litStart, litLen);
        } else if (litLen > 0) {
            if (litLen <= 3) {
                setState(out, lastStatePos, litLen);
                out.write(src, start + litStart, litLen);
            } else {
                writeLiteralRun(out, src, start + litStart, litLen);
            }
        }
        out.write(0x11);
        out.write(0);
        out.write(0);
        return out.toByteArray();
    }

    private static int hash(byte[] s, int p) {
        int v = ((s[p] & 0xFF) << 16) | ((s[p + 1] & 0xFF) << 8) | (s[p + 2] & 0xFF);
        return ((v * 0x9E3779B1) >>> (32 - HASH_BITS));
    }

    private static void insert(byte[] s, int start, int i, int n, int[] head, int[] prev) {
        if (i + MIN_MATCH > n) { prev[i] = -1; return; }
        int h = hash(s, start + i);
        prev[i] = head[h];
        head[h] = i;
    }

    /** Erster Literal-Lauf am Anfang des Stroms. */
    private static void writeFirstLiterals(java.io.ByteArrayOutputStream out, byte[] s, int p, int len) {
        if (len == 0) return;
        if (len <= 238) {
            out.write(17 + len);
            out.write(s, p, len);
        } else {
            writeLiteralRun(out, s, p, len);
        }
    }

    /** Literal-Lauf mit mindestens 4 Bytes. */
    private static void writeLiteralRun(java.io.ByteArrayOutputStream out, byte[] s, int p, int len) {
        if (len - 3 <= 15) {
            out.write(len - 3);
        } else {
            int tt = len - 18;
            out.write(0);
            while (tt > 255) { out.write(0); tt -= 255; }
            out.write(tt);
        }
        out.write(s, p, len);
    }

    /** Trägt 1–3 Folgeliterale in die unteren 2 Bits des letzten Offset-Bytes ein. */
    private static byte[] setState(java.io.ByteArrayOutputStream out, int pos, int litLen) {
        byte[] all = out.toByteArray();
        all[pos] = (byte) ((all[pos] & 0xFC) | litLen);
        out.reset();
        out.write(all, 0, all.length);
        return null;
    }
}
