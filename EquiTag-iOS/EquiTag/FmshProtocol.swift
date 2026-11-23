import Foundation

// NFC-Protokoll der FMSH-E-Ink-Displays (3,52" u. a.).
// 1:1-Port der Android-Version von EquiTag (dort gegen die Hersteller-App geprüft).

struct WriteError: Error {
    let key: String
    let arg: String?

    init(_ key: String, _ arg: String? = nil) {
        self.key = key
        self.arg = arg
    }

    var message: String {
        if let arg = arg { return I18n.t(key, arg) }
        return I18n.t(key)
    }
}

/// Überträgt eine APDU und liefert Antwortdaten + SW1 SW2.
protocol APDUChannel {
    func transceive(_ apdu: [UInt8]) throws -> [UInt8]
}

/// Protokoll aller Befehle und Antworten (zum Teilen bei Fehlern).
enum TransferLog {
    private static var text = ""
    private static let lock = NSLock()

    static func reset(_ header: String) {
        lock.lock(); text = header + "\n"; lock.unlock()
    }

    static func add(_ line: String) {
        lock.lock(); text += line + "\n"; lock.unlock()
    }

    static var all: String {
        lock.lock(); defer { lock.unlock() }
        return text
    }
}

func toHex(_ bytes: [UInt8]) -> String {
    bytes.map { String(format: "%02X", $0) }.joined()
}

func hexBytes(_ s: String) -> [UInt8] {
    var out: [UInt8] = []
    var i = s.startIndex
    while i < s.endIndex {
        let j = s.index(i, offsetBy: 2)
        out.append(UInt8(s[i..<j], radix: 16) ?? 0)
        i = j
    }
    return out
}

struct FmshInfo: CustomStringConvertible {
    var width = 0
    var height = 0
    var refreshScan = 0      // 0 = vertikal, sonst horizontal
    var size = 1             // Anzahl Bildebenen
    var colorCount = 2
    var black = 0, white = 0, red = 0, yellow = 0
    var compress = false
    var pin = false
    var fourColor = false

    var description: String {
        "\(width)x\(height) c\(colorCount) s\(size) scan\(refreshScan) b\(black) w\(white)" + (compress ? " lzo" : "")
    }
}

enum Fmsh {

    // MARK: Geräteinfo (TLV)

    static func parse(_ b: [UInt8]) -> FmshInfo {
        var info = FmshInfo()
        var i = 0
        while i + 1 < b.count {
            let tag = Int(b[i])
            let len = Int(b[i + 1])
            if tag == 0xA0 && i + 8 < b.count {
                info.height = (Int(b[i + 5]) << 8) | Int(b[i + 6])
                info.width = (Int(b[i + 7]) << 8) | Int(b[i + 8])
            } else if tag == 0xA1 && i + 3 < b.count {
                info.refreshScan = Int(Int8(bitPattern: b[i + 2]))
                if b[i + 3] != 0xE0 {
                    info.size = (Int(Int8(bitPattern: b[i + 3])) >> 4) & 0xFF
                    info.colorCount = Int(b[i + 3]) & 0x0F
                    let shift = 6 - info.colorCount
                    let mask = info.colorCount == 2 ? 1 : 3
                    var k = 0
                    while k < info.colorCount && i + 4 + k < b.count {
                        let c = Int(Int8(bitPattern: b[i + 4 + k]))
                        let id = (c >> 5) & 0x07
                        let v = shift >= 0 ? (c >> shift) & mask : 0
                        setColor(&info, id, v)
                        k += 1
                    }
                }
            } else if tag == 0xD1 && i + 2 < b.count {
                info.compress = b[i + 2] != 0
            } else if tag == 0xE0 && i + 2 < b.count {
                info.size = (Int(Int8(bitPattern: b[i + 2])) >> 4) & 0x0F
                info.colorCount = Int(b[i + 2]) & 0x0F
                var k = 0
                while k < info.colorCount && i + 5 + 2 * k < b.count {
                    setColor(&info, Int(b[i + 4 + 2 * k]), Int(Int8(bitPattern: b[i + 5 + 2 * k])))
                    k += 1
                }
            }
            i += len + 2
        }
        let n = b.count
        if n >= 2 && b[n - 2] == 0x90 && b[n - 1] == 0x00 {
            if n >= 16, let desc = String(bytes: b[(n - 16)..<(n - 2)], encoding: .ascii), desc == "4_color Screen" {
                info.fourColor = true
            }
        } else if n >= 2 && b[n - 2] == 0x69 && b[n - 1] == 0x85 {
            info.pin = true
        }
        return info
    }

    private static func setColor(_ info: inout FmshInfo, _ id: Int, _ v: Int) {
        switch id {
        case 0: info.black = v
        case 1: info.white = v
        case 2: info.red = v
        case 3: info.yellow = v
        default: break
        }
    }

    // MARK: Bilddaten

    /// Schwarz/Weiß-Displays: 1 Bit pro Pixel, Farbcodes wie in der Hersteller-App.
    static func blackWhitePlane(_ info: FmshInfo, _ white: [Bool]) -> [UInt8] {
        let w = info.width, h = info.height
        let realW = (w + 7) / 8
        let stride = realW % 4 == 0 ? realW : realW - realW % 4 + 4
        let lightBit = info.black > 0 ? 1 : 0
        let darkBit = info.white > 0 ? 1 : 0
        var rows = [UInt8](repeating: 0, count: stride * h)
        let tail = w % 8
        for y in 0..<h {
            let base = y * stride
            for x in 0..<w {
                let bit = white[y * w + x] ? lightBit : darkBit
                if bit != 0 { rows[base + (x >> 3)] |= UInt8(0x80 >> (x & 7)) }
            }
            // Wie die Hersteller-App: Füllbits des letzten Teilbytes stammen aus dem vorherigen Byte
            if tail != 0 && w >= 8 {
                let last = base + (w >> 3)
                let padMask = UInt8(0xFF >> tail)
                rows[last] = (rows[last] & ~padMask) | (rows[last - 1] & padMask)
            }
        }
        let extraRows = 8 - (h % 8)
        var out = [UInt8](repeating: 0, count: stride * h + 62 + stride * extraRows)
        if info.refreshScan == 0 {
            let vRows = (h + 7) / 8
            for x in 0..<(realW * 8) {
                let byteIndex = x >> 3
                let shift = 7 - (x & 7)
                for j in 0..<vRows {
                    var v = 0
                    for k in 0..<8 {
                        let y = j * 8 + k
                        if y < h && ((Int(rows[y * stride + byteIndex]) >> shift) & 1) != 0 {
                            v |= 0x80 >> k
                        }
                    }
                    out[x * vRows + j] = UInt8(v)
                }
            }
        } else {
            for y in 0..<h {
                for i in 0..<realW {
                    out[y * realW + i] = rows[y * stride + i]
                }
            }
        }
        return out
    }

    /// Displays mit 3 Farben: zwei Ebenen, Farbcode (2 Bit) je Pixel.
    static func threeColorPlanes(_ info: FmshInfo, _ white: [Bool]) -> [[UInt8]] {
        let n = info.width * info.height
        var p0 = [UInt8](repeating: 0, count: n)
        var p1 = [UInt8](repeating: 0, count: n)
        for i in 0..<n {
            let code = white[i] ? info.white : info.black
            p0[i] = code > 1 ? 1 : 0
            p1[i] = code % 2 == 1 ? 1 : 0
        }
        return [scan(info, p0), scan(info, p1)]
    }

    private static func scan(_ info: FmshInfo, _ src: [UInt8]) -> [UInt8] {
        let w = info.width, h = info.height
        if info.refreshScan == 0 {
            let destH = (h + 7) / 8
            var out = [UInt8](repeating: 0, count: destH * w)
            for x in 0..<w {
                for j in 0..<destH {
                    var v = 0
                    for k in 0..<8 {
                        let y = j * 8 + k
                        if y < h && src[y * w + x] != 0 { v |= 0x80 >> k }
                    }
                    out[x * destH + j] = UInt8(v)
                }
            }
            return out
        }
        // Horizontal: Zeile für Zeile, Pixel von rechts nach links (Füllbits nach dem ersten Pixel)
        let rem = w % 8 == 0 ? 0 : 8 - w % 8
        let destW = (w + rem) / 8
        var out = [UInt8](repeating: 0, count: destW * h)
        var seq = [UInt8](repeating: 0, count: w + rem)
        for y in 0..<h {
            var p = 0
            var x = w - 1
            while x >= 0 {
                seq[p] = src[y * w + x]; p += 1
                if x == w - 1 {
                    for _ in 0..<rem { seq[p] = 0; p += 1 }
                }
                x -= 1
            }
            for b in 0..<destW {
                var v = 0
                for k in 0..<8 where seq[b * 8 + k] != 0 { v |= 0x80 >> k }
                out[y * destW + b] = UInt8(v)
            }
        }
        return out
    }
}

/// Kompakter LZO1X-Kompressor: Literal-Läufe und kurze M3-Treffer, Endmarke 11 00 00.
enum Lzo1x {
    private static let minMatch = 3
    private static let maxMatch = 33     // ohne Längen-Erweiterungsbytes
    private static let maxDist = 16384
    private static let hashBits = 12

    static func compress(_ src: [UInt8], _ start: Int, _ n: Int) -> [UInt8] {
        var out: [UInt8] = []
        out.reserveCapacity(n + n / 16 + 64)
        if n == 0 {
            out.append(contentsOf: [0x11, 0x00, 0x00])
            return out
        }
        var head = [Int](repeating: -1, count: 1 << hashBits)
        var prev = [Int](repeating: -1, count: n)

        func hash(_ p: Int) -> Int {
            let v = (UInt32(src[p]) << 16) | (UInt32(src[p + 1]) << 8) | UInt32(src[p + 2])
            return Int((v &* 0x9E3779B1) >> UInt32(32 - hashBits))
        }
        func insert(_ pos: Int) {
            if pos + minMatch > n { prev[pos] = -1; return }
            let h = hash(start + pos)
            prev[pos] = head[h]
            head[h] = pos
        }

        var litStart = 0
        var first = true
        var lastStatePos = -1
        var i = 0
        while i < n {
            var bestLen = 0
            var bestDist = 0
            if i + minMatch <= n {
                var cand = head[hash(start + i)]
                var tries = 0
                while cand >= 0 && i - cand <= maxDist && tries < 64 {
                    tries += 1
                    var len = 0
                    while i + len < n && len < maxMatch && src[start + cand + len] == src[start + i + len] {
                        len += 1
                    }
                    if len > bestLen { bestLen = len; bestDist = i - cand }
                    cand = prev[cand]
                }
            }
            if bestLen >= minMatch {
                let litLen = i - litStart
                if first {
                    writeFirstLiterals(&out, src, start + litStart, litLen)
                    first = false
                } else if litLen > 0 {
                    if litLen <= 3 {
                        out[lastStatePos] = (out[lastStatePos] & 0xFC) | UInt8(litLen)
                        out.append(contentsOf: src[(start + litStart)..<(start + litStart + litLen)])
                    } else {
                        writeLiteralRun(&out, src, start + litStart, litLen)
                    }
                }
                out.append(UInt8(32 | (bestLen - 2)))   // M3, Länge 3…33
                let d = bestDist - 1
                lastStatePos = out.count
                out.append(UInt8((d & 63) << 2))
                out.append(UInt8(d >> 6))
                let end = i + bestLen
                while i < end { insert(i); i += 1 }
                litStart = i
            } else {
                insert(i)
                i += 1
            }
        }
        let litLen = n - litStart
        if first {
            writeFirstLiterals(&out, src, start + litStart, litLen)
        } else if litLen > 0 {
            if litLen <= 3 {
                out[lastStatePos] = (out[lastStatePos] & 0xFC) | UInt8(litLen)
                out.append(contentsOf: src[(start + litStart)..<(start + litStart + litLen)])
            } else {
                writeLiteralRun(&out, src, start + litStart, litLen)
            }
        }
        out.append(contentsOf: [0x11, 0x00, 0x00])
        return out
    }

    private static func writeFirstLiterals(_ out: inout [UInt8], _ s: [UInt8], _ p: Int, _ len: Int) {
        if len == 0 { return }
        if len <= 238 {
            out.append(UInt8(17 + len))
            out.append(contentsOf: s[p..<(p + len)])
        } else {
            writeLiteralRun(&out, s, p, len)
        }
    }

    private static func writeLiteralRun(_ out: inout [UInt8], _ s: [UInt8], _ p: Int, _ len: Int) {
        if len - 3 <= 15 {
            out.append(UInt8(len - 3))
        } else {
            var tt = len - 18
            out.append(0)
            while tt > 255 { out.append(0); tt -= 255 }
            out.append(UInt8(tt))
        }
        out.append(contentsOf: s[p..<(p + len)])
    }
}

/// Ablauf einer Übertragung: Info lesen, Bild senden, aktualisieren, Status abfragen.
final class FmshTransfer {
    private let ch: APDUChannel
    private let progress: (Int, String) -> Void

    init(channel: APDUChannel, progress: @escaping (Int, String) -> Void) {
        self.ch = channel
        self.progress = progress
    }

    private func tx(_ cmd: [UInt8], _ step: String, log: Bool = true) throws -> [UInt8] {
        if log {
            let c = toHex(cmd)
            TransferLog.add("\(step) >> " + (c.count > 40 ? String(c.prefix(40)) + "…(\(cmd.count) B)" : c))
        }
        do {
            let r = try ch.transceive(cmd)
            if log { TransferLog.add("\(step) << \(toHex(r))") }
            return r
        } catch {
            TransferLog.add("\(step) !! \(error)")
            throw WriteError("err_connection")
        }
    }

    func run(source: (Int, Int) -> [Bool]) throws {
        _ = try tx(hexBytes("00A4040007D2760000850101"), "select") // Antwort wird wie beim Hersteller nicht geprüft
        let r1 = try tx(hexBytes("00D1000000"), "info1")
        let r2 = try tx(hexBytes("F0D8000005000000000E"), "info2")
        let info = Fmsh.parse(r1 + r2)
        TransferLog.add("info: \(info) pin=\(info.pin)")
        guard info.width > 0, info.height > 0 else { throw WriteError("err_step", "info " + sw(r1)) }
        if info.pin { throw WriteError("err_pin") }
        if info.fourColor || info.colorCount < 2 || info.colorCount > 3 {
            throw WriteError("err_unsupported", "\(info.colorCount) / \(info.width)x\(info.height)")
        }
        progress(4, "stage_connected")

        let white = source(info.width, info.height)
        let rowCount = Int(ceil(Double((info.width * info.height) / 250) / 8.0))

        if info.colorCount == 2 {
            var data = Fmsh.blackWhitePlane(info, white)
            if info.compress {
                // Nur echte Bilddaten senden – keine Füllbytes über die Displaygröße hinaus
                let realW = (info.width + 7) / 8
                let frameLen = info.refreshScan == 0 ? realW * 8 * ((info.height + 7) / 8) : realW * info.height
                data = Array(data.prefix(min(frameLen, data.count)))
                TransferLog.add("frameLen=\(data.count)")
            }
            try sendPlane(info, data, rowCount, 0, 5, 75)
        } else {
            let planes = Fmsh.threeColorPlanes(info, white)
            if info.size >= 2 {
                try sendPlane(info, planes[0], rowCount, 0, 5, 40)
                try sendPlane(info, planes[1], rowCount, 1, 45, 35)
            } else {
                try sendPlane(info, planes[0], rowCount, 0, 5, 75)
            }
        }
        try refresh()
    }

    private func sendPlane(_ info: FmshInfo, _ data: [UInt8], _ rowCount: Int, _ screen: Int,
                           _ pStart: Int, _ pSpan: Int) throws {
        if info.compress {
            try sendCompressed(data, screen, pStart, pSpan)
            return
        }
        var padded = data
        if padded.count < rowCount * 250 {
            padded.append(contentsOf: [UInt8](repeating: 0, count: rowCount * 250 - padded.count))
        }
        for i in 0..<rowCount {
            var apdu: [UInt8] = [0xF0, 0xD2, UInt8(screen), UInt8(i & 0xFF), 0xFA]
            apdu.append(contentsOf: padded[(i * 250)..<(i * 250 + 250)])
            try expect9000(try tx(apdu, "data\(screen).\(i)"), "data\(screen).\(i)")
            progress(pStart + (i + 1) * pSpan / max(rowCount, 1), "stage_transfer")
        }
    }

    /// Komprimierte Übertragung (F0 D3): jeder 2000-Byte-Block LZO1X-komprimiert.
    private func sendCompressed(_ data: [UInt8], _ screen: Int, _ pStart: Int, _ pSpan: Int) throws {
        let blocks = (data.count + 1999) / 2000
        for blk in 0..<blocks {
            let start = blk * 2000
            let len = min(2000, data.count - start)
            let lzo = Lzo1x.compress(data, start, len)
            let chunks = (lzo.count + 249) / 250
            for c in 0..<chunks {
                let off = c * 250
                let n = min(250, lzo.count - off)
                var apdu: [UInt8] = [0xF0, 0xD3, UInt8(screen), c == chunks - 1 ? 1 : 0,
                                     UInt8(n + 2), UInt8(blk & 0xFF), UInt8(c & 0xFF)]
                apdu.append(contentsOf: lzo[off..<(off + n)])
                let step = "lzo\(screen).\(blk).\(c)"
                try expect9000(try tx(apdu, step), "lzo\(blk).\(c)")
            }
            progress(pStart + (blk + 1) * pSpan / max(blocks, 1), "stage_transfer")
        }
    }

    private func refresh() throws {
        progress(82, "stage_refresh")
        let r = toHex(try tx([0xF0, 0xD4, 0x05, 0x80, 0x00], "refresh"))
        if r == "698A" { throw WriteError("err_step", "refresh " + r) }
        if r == "6986" || r == "68C6" {
            for _ in 0..<5 {
                Thread.sleep(forTimeInterval: 0.3)
                let rr = toHex(try tx(hexBytes("F0D4850000"), "refresh-retry"))
                if rr == "9000" || rr == "009000" {
                    try pollUntilDone(lenient: true)
                    return
                }
            }
            throw WriteError("err_screen")
        }
        if r == "019000" {
            try pollUntilDone(lenient: true)
            return
        }
        if r != "009000" && r != "9000" {
            let rr = toHex(try tx([0xF0, 0xD4, 0x05, 0x00, 0x00], "refresh2"))
            if !rr.hasSuffix("9000") { throw WriteError("err_step", "refresh \(r)/\(rr)") }
        }
        try pollUntilDone(lenient: false)
    }

    /// Fragt F0 DE ab: 00 9000 = fertig, 01 9000 = noch beschäftigt.
    private func pollUntilDone(lenient: Bool) throws {
        let cmd: [UInt8] = [0xF0, 0xDE, 0x00, 0x00, 0x01]
        for i in 0..<1000 {
            let r: String
            do {
                r = toHex(try tx(cmd, "status", log: i < 3 || i % 20 == 0))
            } catch {
                if lenient { return }
                throw error
            }
            if r == "009000" { progress(100, "done"); return }
            if r == "019000" {
                if i % 10 == 0 { progress(min(98, 84 + i / 10), "stage_refresh") }
                Thread.sleep(forTimeInterval: 0.1)
                continue
            }
            if lenient { return }
            if r == "6986" || r == "68C6" { throw WriteError("err_screen") }
            throw WriteError("err_step", "status " + r)
        }
        throw WriteError("err_no_confirm")
    }

    private func expect9000(_ r: [UInt8], _ step: String) throws {
        if !(r.count >= 2 && r[r.count - 2] == 0x90 && r[r.count - 1] == 0x00) {
            throw WriteError("err_step", step + " " + sw(r))
        }
    }

    private func sw(_ r: [UInt8]) -> String {
        let h = toHex(r)
        if h.isEmpty { return "-" }
        return h.count > 8 ? String(h.suffix(8)) : h
    }
}
