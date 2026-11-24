import Foundation

// Displays mit "YZRCOS"-Chip:
//  - Hipoink-3,52"-Karten (360 x 240, Befehle mit 0x53) – Ablauf der Hersteller-App "Hipoink"
//  - Waveshare-2,7"-Board Rev2.2 (176 x 264, Befehle mit 0x74) – nach KegInker (Apache 2.0)
// 1:1-Port der Android-Version von EquiTag.

private func isOk(_ r: [UInt8]) -> Bool {
    r.count >= 2 && r[r.count - 2] == 0x90 && r[r.count - 1] == 0x00
}

private func sw(_ r: [UInt8]) -> String {
    let h = toHex(r)
    if h.isEmpty { return "-" }
    return h.count > 8 ? String(h.suffix(8)) : h
}

private func pause(_ ms: Double) {
    Thread.sleep(forTimeInterval: ms / 1000)
}

// MARK: - Hipoink 3,52"

final class HipoTransfer {
    static let width = 360
    static let height = 240
    private static let size: UInt8 = 0x35   // 3,52" schwarz/weiß

    struct NotHipo: Error {}

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

    /// PIN prüfen (Standard-PIN 44 55 66 77). Wirft NotHipo, wenn der Befehl unbekannt ist.
    func unlock() throws {
        let r = try tx([0x53, 0xB1, 0x00, 0x00, 0x08, 0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77], "hipo-pin")
        if r.count < 2 { throw NotHipo() }
        if r[0] == 0x90 && r[1] == 0x00 { return }
        if r[0] == 0x6D || r[0] == 0x6E { throw NotHipo() }
        throw WriteError("err_pin")
    }

    private func ok(_ cmd: [UInt8], _ step: String, log: Bool = true) throws {
        let r = try tx(cmd, step, log: log)
        // Hersteller prüft die ersten beiden Antwortbytes auf 90 00
        if !(r.count >= 2 && r[0] == 0x90 && r[1] == 0x00) {
            throw WriteError("err_step", "hipo-\(step) " + sw(r))
        }
    }

    /// view: 360 x 240, zeilenweise, true = weiß.
    func write(_ view: [Bool]) throws {
        let w = HipoTransfer.width, h = HipoTransfer.height
        let colBytes = h / 8
        // Spaltenweise: je Spalte 30 Bytes von oben nach unten, MSB = oberstes Pixel, 1 = weiß
        var data = [UInt8](repeating: 0, count: w * colBytes)
        for x in 0..<w {
            for j in 0..<colBytes {
                var v = 0
                for k in 0..<8 {
                    v <<= 1
                    if view[(j * 8 + k) * w + x] { v |= 1 }
                }
                data[x * colBytes + j] = UInt8(v)
            }
        }

        pause(200)
        try ok([0x53, 0x97, 0x00, 0x08, 0x00], "rst0")
        pause(50)
        try ok([0x53, 0x97, 0x01, 0x08, 0x00], "rst1")
        pause(20)
        try ok([0x53, 0x00, HipoTransfer.size, 0x00, 0x00], "step0")
        try ok([0x53, 0x01, HipoTransfer.size, 0x01, 0x00], "step1")
        progress(5, "stage_transfer")

        let packets = data.count / h   // 45
        for i in 0..<packets {
            var cmd: [UInt8] = [0x53, 0x9A, 0x00, 0x0E, UInt8(h)]
            cmd.append(contentsOf: data[(i * h)..<(i * h + h)])
            try ok(cmd, "data\(i)", log: i == 0 || i == packets - 1)
            progress(5 + (i + 1) * 70 / packets, "stage_transfer")
        }

        pause(40)
        try ok([0x53, 0x02, HipoTransfer.size, 0x02, 0x00], "refresh")
        progress(80, "stage_refresh")
        pause(1200)
        for i in 0..<150 {   // max. ca. 15 s
            let r = try tx([0x53, 0x9B, 0x00, 0x0F, 0x01], "busy", log: i < 3 || i % 10 == 0)
            if let first = r.first, first != 0 {
                progress(100, "done")
                return
            }
            progress(min(98, 82 + i / 8), "stage_refresh")
            pause(100)
        }
        throw WriteError("err_no_confirm")
    }
}

// MARK: - Waveshare 2,7" (Rev2.2)

final class WaveshareTransfer {
    static let memW = 176
    static let memH = 264
    private static let controllerRows = 296

    private let ch: APDUChannel
    private let progress: (Int, String) -> Void

    init(channel: APDUChannel, progress: @escaping (Int, String) -> Void) {
        self.ch = channel
        self.progress = progress
    }

    private func ok(_ cmd: [UInt8], _ step: String, log: Bool = true) throws {
        let c = toHex(cmd)
        if log { TransferLog.add("ws \(step) >> " + (c.count > 40 ? String(c.prefix(40)) + "…" : c)) }
        let r: [UInt8]
        do { r = try ch.transceive(cmd) } catch {
            TransferLog.add("ws \(step) !! \(error)")
            throw WriteError("err_connection")
        }
        if log { TransferLog.add("ws \(step) << \(toHex(r))") }
        if !isOk(r) { throw WriteError("err_step", "ws-\(step) " + sw(r)) }
    }

    private func select(_ reg: UInt8) throws {
        try ok([0x74, 0x99, 0x00, 0x0D, 0x01, reg], "reg")
    }

    private func setRegister(_ reg: UInt8, _ values: [UInt8]) throws {
        try select(reg)
        try ok([0x74, 0x9A, 0x00, 0x0E, UInt8(values.count)] + values, "regdata")
    }

    /// view: 176 x 264 (Hochformat), zeilenweise, true = weiß.
    func write(_ view: [Bool]) throws {
        let w = WaveshareTransfer.memW, h = WaveshareTransfer.memH
        let bytesPerRow = w / 8
        // Panelspeicher: Speicherpixel (x, y) zeigt Ansichtspixel (175 - x, 263 - y)
        var frame = [UInt8](repeating: 0xFF, count: WaveshareTransfer.controllerRows * w / 8)
        for i in 0..<(w * h / 8) { frame[i] = 0 }
        for y in 0..<h {
            for x in 0..<w where view[(h - 1 - y) * w + (w - 1 - x)] {
                frame[y * bytesPerRow + x / 8] |= UInt8(0x80 >> (x % 8))
            }
        }

        try ok([0x74, 0xB1, 0x00, 0x00, 0x08, 0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77], "unlock")
        try ok([0x74, 0x97, 0x00, 0x08, 0x00], "reset0")
        pause(150)
        try ok([0x74, 0x97, 0x01, 0x08, 0x00], "reset1")
        pause(150)

        let yMax = WaveshareTransfer.controllerRows - 1
        let yLow = UInt8(yMax & 0xFF), yHigh = UInt8(yMax >> 8)
        let xEnd = UInt8(w / 8 - 1)
        try setRegister(0x01, [yLow, yHigh, 0x01])
        try setRegister(0x11, [0x01])
        try setRegister(0x44, [0x00, xEnd])
        try setRegister(0x45, [yLow, yHigh, 0x00, 0x00])
        try setRegister(0x3C, [0x01])
        try setRegister(0x18, [0x80])
        try setRegister(0x4E, [0x00])
        try setRegister(0x4F, [yLow, yHigh])
        pause(100)

        try select(0x24)
        let chunks = (frame.count + 249) / 250
        for i in 0..<chunks {
            let start = i * 250
            let len = min(250, frame.count - start)
            try ok([0x74, 0x9A, 0x00, 0x0E, UInt8(len)] + Array(frame[start..<(start + len)]), "data\(i)", log: i == 0)
            progress(5 + (i + 1) * 75 / chunks, "stage_transfer")
        }

        try setRegister(0x22, [0xF7])
        try select(0x20)
        for s in 1...6 {
            pause(1000)
            progress(80 + s * 2, "stage_refresh")
        }
        for _ in 0..<60 {
            let r: [UInt8]
            do { r = try ch.transceive([0x74, 0x9B, 0x00, 0x0F, 0x01]) } catch { throw WriteError("err_connection") }
            if isOk(r) && r.count >= 3 && r[0] != 0x01 {
                progress(100, "done")
                return
            }
            pause(200)
        }
        throw WriteError("err_no_confirm")
    }
}
