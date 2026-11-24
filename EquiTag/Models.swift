import Foundation
import SwiftUI

/// Ein Pferd mit Name und Kopfnummer (max. 4 Ziffern).
struct Horse: Identifiable, Codable, Equatable {
    var id: Int64
    var name: String
    var number: String
}

/// Speichert Pferdeliste und Einstellungen lokal auf dem iPhone.
final class HorseStore: ObservableObject {
    @Published var horses: [Horse] {
        didSet { saveHorses() }
    }
    @Published var rotate180: Bool {
        didSet { UserDefaults.standard.set(rotate180, forKey: "rotate180") }
    }
    @Published var invert: Bool {
        didSet { UserDefaults.standard.set(invert, forKey: "invert") }
    }
    @Published var waveshare27: Bool {
        didSet { UserDefaults.standard.set(waveshare27, forKey: "ws27") }
    }
    @Published var language: String {
        didSet {
            UserDefaults.standard.set(language, forKey: "language")
            I18n.setLanguage(language)
        }
    }

    init() {
        let d = UserDefaults.standard
        var list: [Horse] = []
        if let data = d.data(forKey: "horses"), let decoded = try? JSONDecoder().decode([Horse].self, from: data) {
            list = decoded
        }
        horses = list
        rotate180 = d.bool(forKey: "rotate180")
        invert = d.bool(forKey: "invert")
        waveshare27 = d.bool(forKey: "ws27")
        let lang = I18n.resolve(d.string(forKey: "language") ?? "")
        language = lang
        I18n.setLanguage(lang)
    }

    private func saveHorses() {
        if let data = try? JSONEncoder().encode(horses) {
            UserDefaults.standard.set(data, forKey: "horses")
        }
    }
}
