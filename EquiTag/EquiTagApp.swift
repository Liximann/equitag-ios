import SwiftUI
import UIKit

@main
struct EquiTagApp: App {
    @StateObject private var store = HorseStore()

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(store)
                .environment(\.layoutDirection, store.language == "ar" ? .rightToLeft : .leftToRight)
                .environment(\.locale, Locale(identifier: store.language))
        }
    }
}

private let brandBlue = Color(red: 0.12, green: 0.23, blue: 0.37)

// MARK: - Pferdeliste

struct ContentView: View {
    @EnvironmentObject var store: HorseStore
    @State private var showAdd = false
    @State private var editing: Horse?
    @State private var showSettings = false
    @State private var showError = false
    @State private var errorMessage = ""
    @State private var showShare = false

    private var direction: LayoutDirection { store.language == "ar" ? .rightToLeft : .leftToRight }

    var body: some View {
        NavigationView {
            Group {
                if store.horses.isEmpty {
                    VStack {
                        Spacer()
                        Text(I18n.t("empty"))
                            .multilineTextAlignment(.center)
                            .foregroundColor(.secondary)
                            .padding(32)
                        Spacer()
                    }
                } else {
                    List {
                        ForEach(store.horses) { horse in
                            Button {
                                transfer(horse)
                            } label: {
                                HorseRow(horse: horse)
                            }
                            .contextMenu {
                                Button { transfer(horse) } label: { Label(I18n.t("transfer"), systemImage: "wave.3.right") }
                                Button { editing = horse } label: { Label(I18n.t("edit"), systemImage: "pencil") }
                                Button { delete(horse) } label: { Label(I18n.t("delete"), systemImage: "trash") }
                            }
                        }
                        .onDelete { offsets in store.horses.remove(atOffsets: offsets) }
                    }
                    .listStyle(InsetGroupedListStyle())
                }
            }
            .navigationTitle(I18n.t("title_list"))
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button { showSettings = true } label: { Image(systemName: "gearshape") }
                        .accessibilityLabel(Text(I18n.t("settings")))
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button { showAdd = true } label: { Image(systemName: "plus.circle.fill").font(.title2) }
                        .accessibilityLabel(Text(I18n.t("add_horse")))
                }
            }
        }
        .navigationViewStyle(StackNavigationViewStyle())
        .sheet(isPresented: $showAdd) {
            EditHorseView(horse: nil) { store.horses.append($0) }
                .environment(\.layoutDirection, direction)
        }
        .sheet(item: $editing) { horse in
            EditHorseView(horse: horse) { updated in
                if let i = store.horses.firstIndex(where: { $0.id == updated.id }) { store.horses[i] = updated }
            }
            .environment(\.layoutDirection, direction)
        }
        .sheet(isPresented: $showSettings) {
            SettingsView()
                .environmentObject(store)
                .environment(\.layoutDirection, direction)
        }
        .sheet(isPresented: $showShare) {
            ShareSheet(items: [errorMessage + "\n\n" + TransferLog.all])
        }
        .alert(isPresented: $showError) {
            Alert(title: Text("EquiTag"),
                  message: Text(errorMessage + "\n\n" + I18n.t("retry_tap")),
                  primaryButton: .default(Text(I18n.t("share_log"))) {
                      DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) { showShare = true }
                  },
                  secondaryButton: .cancel(Text(I18n.t("ok"))))
        }
    }

    private func transfer(_ horse: Horse) {
        NFCManager.shared.start(horse: horse, rotate180: store.rotate180, invert: store.invert) { error in
            if let error = error {
                errorMessage = error.message
                showError = true
            }
        }
    }

    private func delete(_ horse: Horse) {
        store.horses.removeAll { $0.id == horse.id }
    }
}

struct HorseRow: View {
    let horse: Horse

    var body: some View {
        HStack(spacing: 14) {
            Text(horse.number)
                .font(.system(size: 22, weight: .black))
                .foregroundColor(.white)
                .frame(width: 84, height: 56)
                .background(RoundedRectangle(cornerRadius: 10).fill(brandBlue))
            VStack(alignment: .leading, spacing: 2) {
                Text(horse.name)
                    .font(.headline)
                    .foregroundColor(.primary)
                    .lineLimit(1)
                Text(I18n.t("tap_to_transfer"))
                    .font(.footnote)
                    .foregroundColor(.secondary)
            }
            Spacer()
            Image(systemName: "wave.3.right")
                .foregroundColor(.secondary)
        }
        .padding(.vertical, 4)
    }
}

// MARK: - Hinzufügen / Bearbeiten

struct EditHorseView: View {
    @Environment(\.presentationMode) private var presentationMode
    let horse: Horse?
    let onSave: (Horse) -> Void
    @State private var name: String
    @State private var number: String

    init(horse: Horse?, onSave: @escaping (Horse) -> Void) {
        self.horse = horse
        self.onSave = onSave
        _name = State(initialValue: horse?.name ?? "")
        _number = State(initialValue: horse?.number ?? "")
    }

    private var trimmedName: String { name.trimmingCharacters(in: .whitespaces) }
    private var isValid: Bool { !trimmedName.isEmpty && (1...4).contains(number.count) }

    var body: some View {
        NavigationView {
            Form {
                Section(header: Text(I18n.t("horse_name"))) {
                    TextField(I18n.t("name_hint"), text: $name)
                        .autocapitalization(.words)
                        .disableAutocorrection(true)
                }
                Section(header: Text(I18n.t("number_label"))) {
                    TextField(I18n.t("number_hint"), text: $number)
                        .keyboardType(.numberPad)
                        .onChange(of: number) { value in
                            // Nur Ziffern (auch arabisch-indische) als 0–9, höchstens 4
                            let digits = value.compactMap { $0.wholeNumberValue }.map { String($0) }.joined()
                            let limited = String(digits.prefix(4))
                            if limited != value { number = limited }
                        }
                }
                if isValid, let image = LabelRenderer.previewImage(number: number, name: trimmedName) {
                    Section(header: Text(I18n.t("preview"))) {
                        Image(uiImage: image)
                            .interpolation(.none)
                            .resizable()
                            .aspectRatio(contentMode: .fit)
                            .overlay(RoundedRectangle(cornerRadius: 4).stroke(Color.gray.opacity(0.3)))
                    }
                }
            }
            .navigationTitle(horse == nil ? I18n.t("add_horse") : I18n.t("edit_horse"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(I18n.t("cancel")) { presentationMode.wrappedValue.dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(I18n.t("save")) {
                        let id = horse?.id ?? Int64(Date().timeIntervalSince1970 * 1000)
                        onSave(Horse(id: id, name: trimmedName, number: number))
                        presentationMode.wrappedValue.dismiss()
                    }
                    .disabled(!isValid)
                }
            }
        }
    }
}

// MARK: - Einstellungen

struct SettingsView: View {
    @EnvironmentObject var store: HorseStore
    @Environment(\.presentationMode) private var presentationMode

    private var version: String {
        let v = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "?"
        let b = Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String ?? "?"
        return "\(v) (\(b))"
    }

    var body: some View {
        NavigationView {
            Form {
                Section(header: Text(I18n.t("language"))) {
                    Picker(I18n.t("language"), selection: $store.language) {
                        ForEach(0..<I18n.codes.count, id: \.self) { i in
                            Text(I18n.names[i]).tag(I18n.codes[i])
                        }
                    }
                }
                Section(footer: Text(I18n.t("rotate_desc"))) {
                    Toggle(I18n.t("rotate180"), isOn: $store.rotate180)
                }
                Section(footer: Text(I18n.t("invert_desc"))) {
                    Toggle(I18n.t("invert"), isOn: $store.invert)
                }
                Section(header: Text(I18n.t("help_title"))) {
                    Text(I18n.t("help_text")).font(.footnote)
                }
                Section {
                    HStack {
                        Text(I18n.t("version"))
                        Spacer()
                        Text(version).foregroundColor(.secondary)
                    }
                }
            }
            .navigationTitle(I18n.t("settings"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button(I18n.t("ok")) { presentationMode.wrappedValue.dismiss() }
                }
            }
        }
    }
}

// MARK: - Teilen

struct ShareSheet: UIViewControllerRepresentable {
    let items: [Any]

    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: items, applicationActivities: nil)
    }

    func updateUIViewController(_ controller: UIActivityViewController, context: Context) {}
}
