import Foundation
import CoreNFC
import UIKit

/// APDU-Kanal für ISO-7816-Tags (ISO-DEP).
final class ISO7816Channel: APDUChannel {
    private let tag: NFCISO7816Tag

    init(_ tag: NFCISO7816Tag) { self.tag = tag }

    func transceive(_ apdu: [UInt8]) throws -> [UInt8] {
        guard let command = NFCISO7816APDU(data: Data(apdu)) else { throw WriteError("err_bad_response") }
        let semaphore = DispatchSemaphore(value: 0)
        var result: [UInt8] = []
        var failure: Error?
        tag.sendCommand(apdu: command) { data, sw1, sw2, error in
            if let error = error {
                failure = error
            } else {
                result = [UInt8](data) + [sw1, sw2]
            }
            semaphore.signal()
        }
        if semaphore.wait(timeout: .now() + 30) == .timedOut { throw WriteError("err_connection") }
        if let failure = failure { throw failure }
        return result
    }
}

/// Falls iOS das Display als MIFARE-Tag meldet: ISO-7816-Befehle über die MIFARE-Schnittstelle.
final class MiFareChannel: APDUChannel {
    private let tag: NFCMiFareTag

    init(_ tag: NFCMiFareTag) { self.tag = tag }

    func transceive(_ apdu: [UInt8]) throws -> [UInt8] {
        guard let command = NFCISO7816APDU(data: Data(apdu)) else { throw WriteError("err_bad_response") }
        let semaphore = DispatchSemaphore(value: 0)
        var result: [UInt8] = []
        var failure: Error?
        tag.sendMiFareISO7816Command(command) { data, sw1, sw2, error in
            if let error = error {
                failure = error
            } else {
                result = [UInt8](data) + [sw1, sw2]
            }
            semaphore.signal()
        }
        if semaphore.wait(timeout: .now() + 30) == .timedOut { throw WriteError("err_connection") }
        if let failure = failure { throw failure }
        return result
    }
}

/// Startet die NFC-Sitzung von iOS und überträgt das Bild eines Pferdes.
final class NFCManager: NSObject, NFCTagReaderSessionDelegate {
    static let shared = NFCManager()

    private var session: NFCTagReaderSession?
    private var horse: Horse?
    private var rotate180 = false
    private var invert = false
    private var waveshare27 = false
    private var finished = true
    private var onFinish: ((WriteError?) -> Void)?
    private let work = DispatchQueue(label: "de.equitag.transfer")

    /// onFinish: nil = erfolgreich oder abgebrochen, sonst Fehler.
    func start(horse: Horse, rotate180: Bool, invert: Bool, waveshare27: Bool, onFinish: @escaping (WriteError?) -> Void) {
        guard NFCTagReaderSession.readingAvailable else {
            onFinish(WriteError("nfc_unavailable"))
            return
        }
        self.horse = horse
        self.rotate180 = rotate180
        self.invert = invert
        self.waveshare27 = waveshare27
        self.onFinish = onFinish
        finished = false

        let version = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "?"
        TransferLog.reset("EquiTag iOS \(version), iOS \(UIDevice.current.systemVersion), \(NFCManager.deviceModel())")

        guard let session = NFCTagReaderSession(pollingOption: [.iso14443], delegate: self,
                                                queue: DispatchQueue.global(qos: .userInitiated)) else {
            finish(WriteError("nfc_unavailable"))
            return
        }
        session.alertMessage = I18n.t("hold_phone")
        self.session = session
        session.begin()
    }

    // MARK: NFCTagReaderSessionDelegate

    func tagReaderSessionDidBecomeActive(_ session: NFCTagReaderSession) {}

    func tagReaderSession(_ session: NFCTagReaderSession, didInvalidateWithError error: Error) {
        TransferLog.add("session end: \(error.localizedDescription)")
        if let nfcError = error as? NFCReaderError,
           nfcError.code == .readerSessionInvalidationErrorUserCanceled
            || nfcError.code == .readerSessionInvalidationErrorSessionTimeout
            || nfcError.code == .readerSessionInvalidationErrorFirstNDEFTagRead {
            finish(nil)
        } else {
            finish(WriteError("err_generic", error.localizedDescription))
        }
        self.session = nil
    }

    func tagReaderSession(_ session: NFCTagReaderSession, didDetect tags: [NFCTag]) {
        guard let tag = tags.first else { return }
        let channel: APDUChannel
        let hist: [UInt8]
        switch tag {
        case .iso7816(let t):
            channel = ISO7816Channel(t)
            hist = [UInt8](t.historicalBytes ?? Data())
            TransferLog.add("tag iso7816 id=\(toHex([UInt8](t.identifier))) aid=\(t.initialSelectedAID)"
                + " hist=\(toHex([UInt8](t.historicalBytes ?? Data())))")
        case .miFare(let t):
            channel = MiFareChannel(t)
            hist = [UInt8](t.historicalBytes ?? Data())
            TransferLog.add("tag mifare family=\(t.mifareFamily.rawValue) id=\(toHex([UInt8](t.identifier)))"
                + " hist=\(toHex([UInt8](t.historicalBytes ?? Data())))")
        default:
            TransferLog.add("tag: anderer Typ")
            fail(session, WriteError("err_not_supported"))
            return
        }
        session.connect(to: tag) { [weak self] error in
            guard let self = self else { return }
            if let error = error {
                TransferLog.add("connect error: \(error.localizedDescription)")
                self.fail(session, WriteError("err_connection"))
                return
            }
            session.alertMessage = I18n.t("tag_found")
            self.work.async { self.transfer(session, channel, hist) }
        }
    }

    // MARK: Übertragung

    private func transfer(_ session: NFCTagReaderSession, _ channel: APDUChannel, _ hist: [UInt8]) {
        guard let horse = horse else { return }
        let rotate = rotate180
        let inv = invert
        let progress: (Int, String) -> Void = { percent, key in
            session.alertMessage = "\(I18n.t(key))  \(percent) %"
        }
        func image(_ width: Int, _ height: Int) -> [Bool] {
            LabelRenderer.toDevice(number: horse.number, name: horse.name, devW: width, devH: height,
                                   rotate180: rotate, invert: inv)
        }
        let isYzrcos = String(decoding: hist, as: UTF8.self).contains("YZRCOS")
        do {
            if isYzrcos {
                let waveshare = WaveshareTransfer(channel: channel, progress: progress)
                if waveshare27 {
                    TransferLog.add("YZRCOS, Einstellung: Waveshare 2.7in")
                    try waveshare.write(image(WaveshareTransfer.memW, WaveshareTransfer.memH))
                } else {
                    let hipo = HipoTransfer(channel: channel, progress: progress)
                    do {
                        try hipo.unlock()
                        TransferLog.add("Hipoink 3.52in 360x240")
                        progress(2, "stage_connected")
                        try hipo.write(image(HipoTransfer.width, HipoTransfer.height))
                    } catch is HipoTransfer.NotHipo {
                        TransferLog.add("kein Hipoink -> Waveshare 2.7in")
                        try waveshare.write(image(WaveshareTransfer.memW, WaveshareTransfer.memH))
                    }
                }
            } else {
                let transfer = FmshTransfer(channel: channel, progress: progress)
                try transfer.run { width, height in image(width, height) }
            }
            session.alertMessage = I18n.t("done")
            finish(nil)
            session.invalidate()
        } catch let error as WriteError {
            fail(session, error)
        } catch {
            fail(session, WriteError("err_generic", "\(error)"))
        }
    }

    private func fail(_ session: NFCTagReaderSession, _ error: WriteError) {
        finish(error)
        session.invalidate(errorMessage: error.message)
    }

    private func finish(_ error: WriteError?) {
        if finished { return }
        finished = true
        let callback = onFinish
        DispatchQueue.main.async { callback?(error) }
    }

    static func deviceModel() -> String {
        var info = utsname()
        uname(&info)
        let machine = withUnsafePointer(to: &info.machine) {
            $0.withMemoryRebound(to: CChar.self, capacity: 1) { String(cString: $0) }
        }
        return machine
    }
}
