/*
 * NFC-Übertragung an das Waveshare 2,7" NFC-e-Paper (264 x 176).
 *
 * Protokolle abgeleitet aus KegInker (https://github.com/cacack/keginker,
 * Apache License 2.0), das wiederum auf Magic ePaper
 * (https://github.com/fossasia/magic-epaper-app, Apache License 2.0) basiert.
 * Geändert: nach Java portiert, auf das 2,7"-Panel reduziert.
 */
package de.kopfnummer.app;

import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.nfc.tech.NfcA;

import java.io.IOException;
import java.util.Arrays;

public final class EpaperWriter {

    public interface Progress {
        /** 0–100 */
        void onProgress(int percent, String messageKey);
        /** Vom Display gemeldete Größe und Farbanzahl. */
        void onDeviceInfo(int width, int height, int colors);
    }

    /** Liefert das Bild in nativer Displaygröße: width x height, zeilenweise, true = weiß. */
    public interface ImageSource {
        boolean[] render(int width, int height);
    }

    public static final class WriteException extends Exception {
        public final String arg;
        public WriteException(String messageKey) { this(messageKey, null); }
        public WriteException(String messageKey, String arg) { super(messageKey); this.arg = arg; }
    }

    // Panel-Speicher: 176 Spalten x 264 Zeilen, 1 Bit pro Pixel, MSB zuerst, 1 = weiß
    private static final int MEM_W = 176;
    private static final int MEM_H = 264;
    private static final int FRAME_SIZE = MEM_W * MEM_H / 8;     // 5808
    private static final int CONTROLLER_ROWS = 296;              // Rev2.2
    private static final int CONTROLLER_FRAME = CONTROLLER_ROWS * MEM_W / 8; // 6512

    private EpaperWriter() {}

    /** Überträgt das Bild an ein FMSH-NFC-Display (3,52" u. a.). */
    public static void write(Tag tag, ImageSource source, Progress progress) throws WriteException {
        FmshWriter.log.setLength(0);
        FmshWriter.log.append("EquiTag 1.6, Android ").append(android.os.Build.VERSION.RELEASE)
                .append(", ").append(android.os.Build.MANUFACTURER).append(' ').append(android.os.Build.MODEL).append('\n');
        FmshWriter.log.append("tag ").append(FmshWriter.toHex(tag.getId())).append(' ')
                .append(Arrays.toString(tag.getTechList())).append('\n');
        IsoDep iso = IsoDep.get(tag);
        if (iso == null) {
            FmshWriter.log.append("kein IsoDep\n");
            throw new WriteException("err_not_supported");
        }
        try {
            FmshWriter.write(iso, source, progress);
        } catch (IOException e) {
            throw new WriteException("err_connection");
        } finally {
            try { iso.close(); } catch (IOException ignored) {}
        }
    }

    private static boolean isWaveshareRev22(IsoDep iso) {
        byte[] hist = iso.getHistoricalBytes();
        if (hist == null) return false;
        String s = new String(hist);
        return s.contains("YZRCOS");
    }

    /** Ansicht -> Panelspeicher: Speicherpixel (x, y) zeigt Ansichtspixel (175 - x, 263 - y). */
    static byte[] packFrame(boolean[] view) {
        int bytesPerRow = MEM_W / 8;
        byte[] frame = new byte[FRAME_SIZE];
        for (int y = 0; y < MEM_H; y++) {
            for (int x = 0; x < MEM_W; x++) {
                int viewIndex = (MEM_H - 1 - y) * MEM_W + (MEM_W - 1 - x);
                if (view[viewIndex]) {
                    int i = y * bytesPerRow + x / 8;
                    frame[i] = (byte) (frame[i] | (0x80 >>> (x % 8)));
                }
            }
        }
        return frame;
    }

    // ---------------------------------------------------------------------
    // Rev2.2 (neuer NFC-Chip "YZRCOS", ISO-DEP, CLA 0x74)
    // ---------------------------------------------------------------------

    private static void writeRev22(IsoDep iso, byte[] classicFrame, Progress p) throws WriteException {
        byte[] frame = new byte[CONTROLLER_FRAME];
        System.arraycopy(classicFrame, 0, frame, 0, classicFrame.length);
        Arrays.fill(frame, classicFrame.length, frame.length, (byte) 0xFF); // verdeckte Zeilen weiß

        try {
            if (!iso.isConnected()) iso.connect();
            iso.setTimeout(3000);
            int chunkSize = 250;
            int max = iso.getMaxTransceiveLength();
            if (max > 0 && max - 5 < chunkSize) chunkSize = max - 5;

            p.onProgress(2, "stage_connected");
            isoOk(iso, apdu(0xB1, 0x00, 0x00, 0x08, 0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77), "Entsperren");
            isoOk(iso, apdu(0x97, 0x00, 0x08, 0x00), "Reset");
            sleep(150);
            isoOk(iso, apdu(0x97, 0x01, 0x08, 0x00), "Reset");
            sleep(150);

            int yMax = CONTROLLER_ROWS - 1;
            int yLow = yMax & 0xFF, yHigh = yMax >> 8;
            int xEnd = MEM_W / 8 - 1;
            setRegister(iso, 0x01, yLow, yHigh, 0x01);       // Gate-Zeilen
            setRegister(iso, 0x11, 0x01);                    // X steigend, Y fallend
            setRegister(iso, 0x44, 0x00, xEnd);              // RAM-Fenster X
            setRegister(iso, 0x45, yLow, yHigh, 0x00, 0x00); // RAM-Fenster Y
            setRegister(iso, 0x3C, 0x01);                    // Rand
            setRegister(iso, 0x18, 0x80);                    // interner Temperatursensor
            setRegister(iso, 0x4E, 0x00);                    // RAM-Cursor X
            setRegister(iso, 0x4F, yLow, yHigh);             // RAM-Cursor Y
            sleep(100);

            selectRegister(iso, 0x24); // Schwarz/Weiß-RAM
            int chunks = (frame.length + chunkSize - 1) / chunkSize;
            for (int i = 0; i < chunks; i++) {
                int start = i * chunkSize;
                int len = Math.min(chunkSize, frame.length - start);
                isoOk(iso, writeData(frame, start, len), "Bilddaten");
                p.onProgress(5 + (i + 1) * 75 / chunks, "stage_transfer");
            }

            setRegister(iso, 0x22, 0xF7);
            selectRegister(iso, 0x20); // Aktualisierung starten
            for (int s = 1; s <= 6; s++) {
                sleep(1000);
                p.onProgress(80 + s * 2, "stage_refresh");
            }
            boolean done = false;
            for (int i = 0; i < 60 && !done; i++) {
                byte[] r = iso.transceive(apdu(0x9B, 0x00, 0x0F, 0x01));
                if (isIsoOk(r) && r.length >= 3 && r[0] != 0x01) {
                    done = true;
                } else {
                    sleep(200);
                }
            }
            if (!done) throw new WriteException("err_no_confirm");
            p.onProgress(100, "done");
        } catch (IOException e) {
            throw new WriteException("err_connection");
        } finally {
            try { iso.close(); } catch (IOException ignored) {}
        }
    }

    private static void selectRegister(IsoDep iso, int reg) throws IOException, WriteException {
        isoOk(iso, apdu(0x99, 0x00, 0x0D, 0x01, reg), "Register");
    }

    private static void setRegister(IsoDep iso, int reg, int... values) throws IOException, WriteException {
        selectRegister(iso, reg);
        byte[] data = new byte[values.length];
        for (int i = 0; i < values.length; i++) data[i] = (byte) values[i];
        isoOk(iso, writeData(data, 0, data.length), "Register");
    }

    private static byte[] apdu(int ins, int p1, int p2, int... rest) {
        byte[] out = new byte[4 + rest.length];
        out[0] = 0x74;
        out[1] = (byte) ins;
        out[2] = (byte) p1;
        out[3] = (byte) p2;
        for (int i = 0; i < rest.length; i++) out[4 + i] = (byte) rest[i];
        return out;
    }

    private static byte[] writeData(byte[] src, int start, int len) {
        byte[] out = new byte[5 + len];
        out[0] = 0x74;
        out[1] = (byte) 0x9A;
        out[2] = 0x00;
        out[3] = 0x0E;
        out[4] = (byte) len;
        System.arraycopy(src, start, out, 5, len);
        return out;
    }

    private static boolean isIsoOk(byte[] r) {
        return r != null && r.length >= 2 && r[r.length - 2] == (byte) 0x90 && r[r.length - 1] == 0x00;
    }

    private static void isoOk(IsoDep iso, byte[] cmd, String step) throws IOException, WriteException {
        byte[] r = iso.transceive(cmd);
        if (!isIsoOk(r)) throw new WriteException("err_bad_response");
    }

    // ---------------------------------------------------------------------
    // Klassische Boards (NFC-A, Befehle "CD xx")
    // ---------------------------------------------------------------------

    private static void writeClassic(NfcA nfc, byte[] frame, Progress p) throws WriteException {
        final int chunk = 121;
        try {
            nfc.connect();
            nfc.setTimeout(3000);
            p.onProgress(2, "stage_connected");
            cdOk(nfc, "Init", 0xCD, 0x0D);
            cdOk(nfc, "Displaytyp", 0xCD, 0x00, 0x10);
            sleep(50);
            int[][] power = {{0x01, 20}, {0x02, 20}, {0x03, 20}, {0x05, 20}, {0x06, 10}};
            for (int[] step : power) {
                cdOk(nfc, "Einschalten", 0xCD, step[0]);
                sleep(step[1]);
            }
            cdOk(nfc, "Vorbereiten", 0xCD, 0x07, 0x00);
            sleep(96);
            cdOk(nfc, "Datenstart", 0xCD, 0x18);
            sleep(100);
            int chunks = frame.length / chunk; // 48
            for (int i = 0; i < chunks; i++) {
                byte[] cmd = new byte[3 + chunk];
                cmd[0] = (byte) 0xCD;
                cmd[1] = 0x19;
                cmd[2] = (byte) chunk;
                System.arraycopy(frame, i * chunk, cmd, 3, chunk);
                byte[] r = nfc.transceive(cmd);
                if (!isCdOk(r)) throw new WriteException("err_bad_response");
                p.onProgress(5 + (i + 1) * 75 / chunks, "stage_transfer");
                sleep(2);
            }
            sleep(300);
            cdOk(nfc, "Aktualisieren", 0xCD, 0x09);
            p.onProgress(82, "stage_refresh");
            sleep(2000);
            boolean done = false;
            for (int i = 0; i < 600 && !done; i++) {
                byte[] r = nfc.transceive(new byte[]{(byte) 0xCD, 0x0A});
                if (r != null && r.length >= 2 && r[0] == (byte) 0xFF && r[1] == 0x00) {
                    done = true;
                } else {
                    sleep(25);
                    if (i % 40 == 0) p.onProgress(Math.min(98, 84 + i / 40), "stage_refresh");
                }
            }
            if (!done) throw new WriteException("err_no_confirm");
            cdOk(nfc, "Ausschalten", 0xCD, 0x04);
            p.onProgress(100, "done");
        } catch (IOException e) {
            throw new WriteException("err_connection");
        } finally {
            try { nfc.close(); } catch (IOException ignored) {}
        }
    }

    private static boolean isCdOk(byte[] r) {
        return r != null && r.length >= 2 && r[0] == 0x00 && r[1] == 0x00;
    }

    private static void cdOk(NfcA nfc, String step, int... bytes) throws IOException, WriteException {
        byte[] cmd = new byte[bytes.length];
        for (int i = 0; i < bytes.length; i++) cmd[i] = (byte) bytes[i];
        if (!isCdOk(nfc.transceive(cmd))) throw new WriteException("err_bad_response");
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
    }
}
