import UIKit
import CoreText

/// Zeichnet das Anzeigebild im Querformat (z. B. 360 x 240 für 4:3) und bringt es
/// in die native Ausrichtung des Displays.
enum LabelRenderer {

    /// Graustufenbild (0 = schwarz, 255 = weiß), zeilenweise von oben, hart auf Schwarz/Weiß gesetzt.
    static func renderGray(number: String, name: String, width: Int, height: Int) -> [UInt8] {
        var buffer = [UInt8](repeating: 255, count: width * height)
        let space = CGColorSpaceCreateDeviceGray()
        buffer.withUnsafeMutableBytes { raw in
            guard let ctx = CGContext(data: raw.baseAddress, width: width, height: height,
                                      bitsPerComponent: 8, bytesPerRow: width, space: space,
                                      bitmapInfo: CGImageAlphaInfo.none.rawValue) else { return }
            draw(ctx, number: number, name: name, w: CGFloat(width), h: CGFloat(height))
        }
        for i in 0..<buffer.count {
            buffer[i] = buffer[i] >= 128 ? 255 : 0
        }
        return buffer
    }

    /// Zeichnet in Core-Graphics-Koordinaten (Ursprung unten links).
    private static func draw(_ c: CGContext, number: String, name: String, w: CGFloat, h: CGFloat) {
        let s = h / 176
        c.setFillColor(gray: 1, alpha: 1)
        c.fill(CGRect(x: 0, y: 0, width: w, height: h))
        c.setShouldAntialias(true)

        // Rahmen
        let inset = 4.5 * s
        let frame = CGPath(roundedRect: CGRect(x: inset, y: inset, width: w - 2 * inset, height: h - 2 * inset),
                           cornerWidth: 12 * s, cornerHeight: 12 * s, transform: nil)
        c.addPath(frame)
        c.setStrokeColor(gray: 0, alpha: 1)
        c.setLineWidth(3 * s)
        c.strokePath()

        c.setFillColor(gray: 0, alpha: 1)
        c.textMatrix = .identity

        // Pferdename unten links
        let trimmed = name.trimmingCharacters(in: .whitespaces)
        if !trimmed.isEmpty {
            let font = UIFont.systemFont(ofSize: 15 * s, weight: .bold)
            var line = makeLine(trimmed, font)
            let maxWidth = Double(w - 28 * s)
            if CTLineGetTypographicBounds(line, nil, nil, nil) > maxWidth,
               let truncated = CTLineCreateTruncatedLine(line, maxWidth, .end, makeLine("…", font)) {
                line = truncated
            }
            c.textPosition = CGPoint(x: 14 * s, y: 13 * s)
            CTLineDraw(line, c)
        }

        // Kopfnummer so groß wie möglich über dem Namen
        let left = 14 * s, right = w - 14 * s, bottom = 32 * s, top = h - 12 * s
        let boxW = right - left, boxH = top - bottom
        let narrow = number.count >= 4 && boxW / boxH < 2.2
        func numberFont(_ size: CGFloat) -> UIFont {
            if narrow, let condensed = UIFont(name: "HelveticaNeue-CondensedBlack", size: size) {
                return condensed
            }
            return UIFont.systemFont(ofSize: size, weight: .black)
        }
        c.textPosition = .zero
        var line = makeLine(number, numberFont(200))
        var bounds = CTLineGetImageBounds(line, c)
        guard bounds.width > 0, bounds.height > 0 else { return }
        let scale = min(boxW / bounds.width, boxH / bounds.height)
        line = makeLine(number, numberFont(200 * scale))
        c.textPosition = .zero
        bounds = CTLineGetImageBounds(line, c)
        c.textPosition = CGPoint(x: left + (boxW - bounds.width) / 2 - bounds.minX,
                                 y: bottom + (boxH - bounds.height) / 2 - bounds.minY)
        CTLineDraw(line, c)
    }

    private static func makeLine(_ text: String, _ font: UIFont) -> CTLine {
        let attributes: [NSAttributedString.Key: Any] = [
            .font: font,
            NSAttributedString.Key(kCTForegroundColorFromContextAttributeName as String): true
        ]
        return CTLineCreateWithAttributedString(NSAttributedString(string: text, attributes: attributes))
    }

    /// Bild für ein Display mit nativer Größe devW x devH (zeilenweise, true = weiß).
    /// Hochformat-Displays bekommen das Bild um 90° gegen den Uhrzeigersinn gedreht.
    static func toDevice(number: String, name: String, devW: Int, devH: Int, rotate180: Bool, invert: Bool) -> [Bool] {
        let portrait = devH > devW
        let lw = portrait ? devH : devW
        let lh = portrait ? devW : devH
        let px = renderGray(number: number, name: name, width: lw, height: lh)
        var out = [Bool](repeating: false, count: devW * devH)
        for y in 0..<devH {
            for x in 0..<devW {
                var lx: Int
                var ly: Int
                if portrait { lx = lw - 1 - y; ly = x } else { lx = x; ly = y }
                if rotate180 { lx = lw - 1 - lx; ly = lh - 1 - ly }
                let isWhite = px[ly * lw + lx] >= 128
                out[y * devW + x] = invert != isWhite
            }
        }
        return out
    }

    /// Vorschau im 4:3-Format (360 x 240 wie das 3,52"-Display).
    static func previewImage(number: String, name: String, width: Int = 360, height: Int = 240) -> UIImage? {
        let px = renderGray(number: number, name: name, width: width, height: height)
        guard let provider = CGDataProvider(data: Data(px) as CFData),
              let image = CGImage(width: width, height: height, bitsPerComponent: 8, bitsPerPixel: 8,
                                  bytesPerRow: width, space: CGColorSpaceCreateDeviceGray(),
                                  bitmapInfo: CGBitmapInfo(rawValue: CGImageAlphaInfo.none.rawValue),
                                  provider: provider, decode: nil, shouldInterpolate: false,
                                  intent: .defaultIntent) else { return nil }
        return UIImage(cgImage: image)
    }
}
