import Foundation

/// Texte der App in 7 Sprachen. Reihenfolge: de, en, es, it, nl, fr, ar.
enum I18n {
    static let codes = ["de", "en", "es", "it", "nl", "fr", "ar"]
    static let names = ["Deutsch", "English", "Español", "Italiano", "Nederlands", "Français", "العربية"]

    private static var lang = 0

    static func setLanguage(_ code: String) { lang = index(code) }

    static func index(_ code: String) -> Int { codes.firstIndex(of: code) ?? 1 }

    /// Gespeicherte Wahl, sonst Systemsprache, sonst Englisch.
    static func resolve(_ saved: String) -> String {
        if !saved.isEmpty { return saved }
        let sys = Locale.preferredLanguages.first.map { String($0.prefix(2)) } ?? "en"
        return codes[index(sys)]
    }

    static func t(_ key: String) -> String {
        guard let v = table[key] else { return key }
        return v[lang]
    }

    static func t(_ key: String, _ args: CVarArg...) -> String {
        String(format: t(key), arguments: args)
    }

    private static let table: [String: [String]] = [
        "add_horse": ["Pferd hinzufügen", "Add horse", "Añadir caballo", "Aggiungi cavallo", "Paard toevoegen", "Ajouter un cheval", "إضافة حصان"],
        "cancel": ["Abbrechen", "Cancel", "Cancelar", "Annulla", "Annuleren", "Annuler", "إلغاء"],
        "close": ["Schließen", "Close", "Cerrar", "Chiudi", "Sluiten", "Fermer", "إغلاق"],
        "delete": ["Löschen", "Delete", "Eliminar", "Elimina", "Verwijderen", "Supprimer", "حذف"],
        "delete_confirm": ["„%@“ wirklich löschen?", "Delete \"%@\"?", "¿Eliminar «%@»?", "Eliminare «%@»?", "\"%@\" verwijderen?", "Supprimer « %@ » ?", "هل تريد حذف \"%@\"؟"],
        "display_info": ["Display: %1$d × %2$d Pixel, %3$d Farben", "Display: %1$d × %2$d pixels, %3$d colors", "Pantalla: %1$d × %2$d píxeles, %3$d colores", "Display: %1$d × %2$d pixel, %3$d colori", "Display: %1$d × %2$d pixels, %3$d kleuren", "Écran : %1$d × %2$d pixels, %3$d couleurs", "الشاشة: %1$d × %2$d بكسل، %3$d ألوان"],
        "done": ["✓  Übertragung abgeschlossen", "✓  Transfer complete", "✓  Transferencia completada", "✓  Trasferimento completato", "✓  Overdracht voltooid", "✓  Transfert terminé", "✓  اكتمل النقل"],
        "edit": ["Bearbeiten", "Edit", "Editar", "Modifica", "Bewerken", "Modifier", "تعديل"],
        "edit_horse": ["Pferd bearbeiten", "Edit horse", "Editar caballo", "Modifica cavallo", "Paard bewerken", "Modifier le cheval", "تعديل الحصان"],
        "empty": ["Noch keine Pferde angelegt.\n\nTippe unten auf  +  um ein Pferd mit Namen und Kopfnummer hinzuzufügen.", "No horses yet.\n\nTap  +  at the bottom to add a horse with its name and head number.", "Todavía no hay caballos.\n\nToca  +  abajo para añadir un caballo con su nombre y número de cabezada.", "Nessun cavallo ancora.\n\nTocca  +  in basso per aggiungere un cavallo con nome e numero di testiera.", "Nog geen paarden.\n\nTik onderaan op  +  om een paard met naam en hoofdnummer toe te voegen.", "Aucun cheval pour l'instant.\n\nAppuie sur  +  en bas pour ajouter un cheval avec son nom et son numéro de tête.", "لا توجد خيول بعد.\n\nاضغط على  +  في الأسفل لإضافة حصان باسمه ورقمه."],
        "err_bad_response": ["Unerwartete Antwort des Displays.", "Unexpected response from the display.", "Respuesta inesperada de la pantalla.", "Risposta inattesa dal display.", "Onverwacht antwoord van het display.", "Réponse inattendue de l'écran.", "استجابة غير متوقعة من الشاشة."],
        "err_connection": ["Verbindung abgebrochen. Handy ruhig an das Display halten.", "Connection lost. Hold the phone still against the display.", "Conexión perdida. Mantén el móvil quieto junto a la pantalla.", "Connessione interrotta. Tieni il telefono fermo sul display.", "Verbinding verbroken. Houd de telefoon stil tegen het display.", "Connexion perdue. Tiens le téléphone immobile contre l'écran.", "انقطع الاتصال. أبقِ الهاتف ثابتاً على الشاشة."],
        "err_generic": ["Fehler: %@", "Error: %@", "Error: %@", "Errore: %@", "Fout: %@", "Erreur : %@", "خطأ: %@"],
        "err_name": ["Bitte Pferdenamen eingeben", "Please enter a horse name", "Introduce el nombre del caballo", "Inserisci il nome del cavallo", "Voer de naam van het paard in", "Saisis le nom du cheval", "يرجى إدخال اسم الحصان"],
        "err_no_confirm": ["Das Display hat das Ende der Aktualisierung nicht bestätigt.", "The display did not confirm that the update finished.", "La pantalla no confirmó el final de la actualización.", "Il display non ha confermato la fine dell'aggiornamento.", "Het display heeft het einde van de update niet bevestigd.", "L'écran n'a pas confirmé la fin de la mise à jour.", "لم تؤكد الشاشة انتهاء التحديث."],
        "err_not_supported": ["Kein passendes Display erkannt.", "No compatible display detected.", "No se detectó ninguna pantalla compatible.", "Nessun display compatibile rilevato.", "Geen compatibel display gevonden.", "Aucun écran compatible détecté.", "لم يتم اكتشاف شاشة متوافقة."],
        "err_number": ["1 bis 4 Ziffern", "1 to 4 digits", "De 1 a 4 dígitos", "Da 1 a 4 cifre", "1 tot 4 cijfers", "1 à 4 chiffres", "من 1 إلى 4 أرقام"],
        "err_pin": ["Das Display ist mit einer PIN geschützt. Bitte beim Hersteller die PIN-Sperre entfernen lassen.", "The display is protected by a PIN. Please ask the manufacturer to remove the PIN lock.", "La pantalla está protegida con un PIN. Pide al fabricante que quite el bloqueo.", "Il display è protetto da PIN. Chiedi al produttore di rimuovere il blocco.", "Het display is beveiligd met een pincode. Vraag de fabrikant de pincode te verwijderen.", "L'écran est protégé par un code PIN. Demande au fabricant de retirer le verrouillage.", "الشاشة محمية برمز PIN. يرجى مطالبة الشركة المصنعة بإزالة القفل."],
        "err_screen": ["Das Display meldet einen Anschlussfehler des Bildschirms. Bitte prüfen und erneut versuchen.", "The display reports a screen connection error. Please check and try again.", "La pantalla indica un error de conexión. Compruébala y vuelve a intentarlo.", "Il display segnala un errore di collegamento dello schermo. Controlla e riprova.", "Het display meldt een aansluitfout van het scherm. Controleer en probeer opnieuw.", "L'écran signale une erreur de connexion. Vérifie et réessaie.", "تُبلغ الشاشة عن خطأ في التوصيل. يرجى التحقق والمحاولة مرة أخرى."],
        "err_step": ["Unerwartete Antwort des Displays (%@).", "Unexpected response from the display (%@).", "Respuesta inesperada de la pantalla (%@).", "Risposta inattesa dal display (%@).", "Onverwacht antwoord van het display (%@).", "Réponse inattendue de l'écran (%@).", "استجابة غير متوقعة من الشاشة (%@)."],
        "err_unsupported": ["Dieser Displaytyp wird noch nicht unterstützt (%@).", "This display type is not supported yet (%@).", "Este tipo de pantalla aún no es compatible (%@).", "Questo tipo di display non è ancora supportato (%@).", "Dit type display wordt nog niet ondersteund (%@).", "Ce type d'écran n'est pas encore pris en charge (%@).", "نوع الشاشة هذا غير مدعوم بعد (%@)."],
        "help": ["Hilfe", "Help", "Ayuda", "Aiuto", "Help", "Aide", "مساعدة"],
        "help_text": ["1. Mit  +  Pferde mit Name und Kopfnummer anlegen.\n\n2. Pferd antippen und das Handy flach an die Rückseite des Displays halten (der NFC-Bereich liegt meist mittig oder oben auf der Handy-Rückseite).\n\n3. Handy ruhig halten, bis „Übertragung abgeschlossen“ erscheint (ca. 10 Sekunden).\n\nPferd lange gedrückt halten = bearbeiten oder löschen.\n\nSteht die Anzeige auf dem Kopf, in den Einstellungen „Anzeige um 180° drehen“ aktivieren.", "1. Tap  +  to add horses with name and head number.\n\n2. Tap a horse and hold your phone flat against the back of the display (the NFC area is usually in the middle or top of the phone's back).\n\n3. Hold still until “Transfer complete” appears (about 10 seconds).\n\nLong-press a horse to edit or delete it.\n\nIf the image appears upside down, turn on “Rotate display 180°” in the settings.", "1. Toca  +  para añadir caballos con nombre y número de cabezada.\n\n2. Toca un caballo y sujeta el móvil plano contra la parte trasera de la pantalla (la zona NFC suele estar en el centro o arriba de la parte trasera del móvil).\n\n3. Mantén el móvil quieto hasta que aparezca «Transferencia completada» (unos 10 segundos).\n\nMantén pulsado un caballo para editarlo o eliminarlo.\n\nSi la imagen aparece al revés, activa «Girar la pantalla 180°» en los ajustes.", "1. Tocca  +  per aggiungere cavalli con nome e numero di testiera.\n\n2. Tocca un cavallo e appoggia il telefono al retro del display (l'area NFC di solito è al centro o in alto sul retro del telefono).\n\n3. Tieni fermo il telefono finché non compare «Trasferimento completato» (circa 10 secondi).\n\nTieni premuto un cavallo per modificarlo o eliminarlo.\n\nSe l'immagine è capovolta, attiva «Ruota il display di 180°» nelle impostazioni.", "1. Tik op  +  om paarden met naam en hoofdnummer toe te voegen.\n\n2. Tik op een paard en houd je telefoon plat tegen de achterkant van het display (de NFC-zone zit meestal in het midden of bovenaan op de achterkant van de telefoon).\n\n3. Houd de telefoon stil tot „Overdracht voltooid” verschijnt (ongeveer 10 seconden).\n\nHoud een paard lang ingedrukt om het te bewerken of te verwijderen.\n\nStaat de afbeelding ondersteboven, zet dan „Display 180° draaien” aan in de instellingen.", "1. Appuie sur  +  pour ajouter des chevaux avec leur nom et leur numéro de tête.\n\n2. Appuie sur un cheval et tiens ton téléphone à plat contre l'arrière de l'écran (la zone NFC se trouve généralement au milieu ou en haut du dos du téléphone).\n\n3. Ne bouge pas jusqu'à ce que « Transfert terminé » s'affiche (environ 10 secondes).\n\nAppui long sur un cheval pour le modifier ou le supprimer.\n\nSi l'image est à l'envers, active « Pivoter l'affichage de 180° » dans les paramètres.", "1. اضغط على  +  لإضافة خيول بالاسم ورقم الحصان.\n\n2. اضغط على حصان وضع الهاتف بشكل مسطح على الجهة الخلفية للشاشة (تكون منطقة NFC عادةً في منتصف أو أعلى ظهر الهاتف).\n\n3. أبقِ الهاتف ثابتاً حتى تظهر رسالة \"اكتمل النقل\" (حوالي 10 ثوانٍ).\n\nاضغط مطولاً على حصان لتعديله أو حذفه.\n\nإذا ظهرت الصورة مقلوبة، فعّل خيار \"تدوير العرض 180°\" في الإعدادات."],
        "help_title": ["So funktioniert's", "How it works", "Cómo funciona", "Come funziona", "Zo werkt het", "Comment ça marche", "طريقة الاستخدام"],
        "hold_phone": ["Halte das iPhone jetzt flach an die Rückseite des Displays.", "Now hold your iPhone flat against the back of the display.", "Sujeta ahora el iPhone plano contra la parte trasera de la pantalla.", "Ora tieni l'iPhone appoggiato al retro del display.", "Houd je iPhone nu plat tegen de achterkant van het display.", "Tiens maintenant ton iPhone à plat contre l'arrière de l'écran.", "ضع الآيفون الآن بشكل مسطح على الجهة الخلفية للشاشة."],
        "horse_name": ["Pferdename", "Horse name", "Nombre del caballo", "Nome del cavallo", "Naam van het paard", "Nom du cheval", "اسم الحصان"],
        "invert": ["Farben umkehren", "Invert colors", "Invertir colores", "Inverti colori", "Kleuren omkeren", "Inverser les couleurs", "عكس الألوان"],
        "invert_desc": ["Aktivieren, falls das Display weiße Schrift auf schwarzem Grund zeigt.", "Turn on if the display shows white text on a black background.", "Actívalo si la pantalla muestra texto blanco sobre fondo negro.", "Attivalo se il display mostra testo bianco su sfondo nero.", "Zet dit aan als het display witte tekst op een zwarte achtergrond toont.", "Active-le si l'écran affiche du texte blanc sur fond noir.", "فعّله إذا عرضت الشاشة نصاً أبيض على خلفية سوداء."],
        "language": ["Sprache", "Language", "Idioma", "Lingua", "Taal", "Langue", "اللغة"],
        "name_hint": ["z. B. Selfie", "e.g. Selfie", "p. ej. Selfie", "es. Selfie", "bijv. Selfie", "ex. Selfie", "مثال: Selfie"],
        "nfc_off_msg": ["Bitte NFC in den Einstellungen einschalten.", "Please turn on NFC in the settings.", "Activa NFC en los ajustes.", "Attiva NFC nelle impostazioni.", "Zet NFC aan in de instellingen.", "Active le NFC dans les paramètres.", "يرجى تشغيل NFC من الإعدادات."],
        "nfc_off_title": ["NFC ist ausgeschaltet", "NFC is turned off", "NFC está desactivado", "NFC è disattivato", "NFC staat uit", "Le NFC est désactivé", "NFC متوقف"],
        "nfc_unavailable": ["NFC ist auf diesem Gerät nicht verfügbar.", "NFC is not available on this device.", "NFC no está disponible en este dispositivo.", "NFC non è disponibile su questo dispositivo.", "NFC is niet beschikbaar op dit apparaat.", "Le NFC n'est pas disponible sur cet appareil.", "تقنية NFC غير متوفرة على هذا الجهاز."],
        "no_nfc_msg": ["Dieses Handy hat kein NFC und kann das Display nicht beschreiben.", "This phone has no NFC and cannot update the display.", "Este móvil no tiene NFC y no puede actualizar la pantalla.", "Questo telefono non ha NFC e non può aggiornare il display.", "Deze telefoon heeft geen NFC en kan het display niet bijwerken.", "Ce téléphone n'a pas de NFC et ne peut pas mettre à jour l'écran.", "هذا الهاتف لا يدعم NFC ولا يمكنه تحديث الشاشة."],
        "no_nfc_title": ["Kein NFC", "No NFC", "Sin NFC", "Nessun NFC", "Geen NFC", "Pas de NFC", "لا يوجد NFC"],
        "number_hint": ["z. B. 821", "e.g. 821", "p. ej. 821", "es. 821", "bijv. 821", "ex. 821", "مثال: 821"],
        "number_label": ["Kopfnummer (max. 4 Ziffern)", "Head number (max. 4 digits)", "Número de cabezada (máx. 4 dígitos)", "Numero di testiera (max. 4 cifre)", "Hoofdnummer (max. 4 cijfers)", "Numéro de tête (4 chiffres max.)", "رقم الحصان (4 أرقام كحد أقصى)"],
        "ok": ["OK", "OK", "OK", "OK", "OK", "OK", "حسناً"],
        "preview": ["Vorschau", "Preview", "Vista previa", "Anteprima", "Voorbeeld", "Aperçu", "معاينة"],
        "retry_hint": ["Zum Wiederholen das Handy erneut anhalten.", "Hold the phone against the display again to retry.", "Vuelve a acercar el móvil para reintentar.", "Riavvicina il telefono per riprovare.", "Houd de telefoon opnieuw tegen het display om het nog eens te proberen.", "Approche à nouveau le téléphone pour réessayer.", "قرّب الهاتف مرة أخرى لإعادة المحاولة."],
        "retry_tap": ["Zum Wiederholen das Pferd erneut antippen.", "Tap the horse again to retry.", "Toca el caballo de nuevo para reintentar.", "Tocca di nuovo il cavallo per riprovare.", "Tik opnieuw op het paard om het nog eens te proberen.", "Appuie à nouveau sur le cheval pour réessayer.", "اضغط على الحصان مرة أخرى لإعادة المحاولة."],
        "rotate180": ["Anzeige um 180° drehen", "Rotate display 180°", "Girar la pantalla 180°", "Ruota il display di 180°", "Display 180° draaien", "Pivoter l'affichage de 180°", "تدوير العرض 180°"],
        "rotate_desc": ["Aktivieren, falls die Nummer auf dem Display auf dem Kopf steht.", "Turn on if the number appears upside down on the display.", "Actívalo si el número aparece al revés en la pantalla.", "Attivalo se il numero appare capovolto sul display.", "Zet dit aan als het nummer ondersteboven op het display staat.", "Active-le si le numéro s'affiche à l'envers sur l'écran.", "فعّله إذا ظهر الرقم مقلوباً على الشاشة."],
        "save": ["Speichern", "Save", "Guardar", "Salva", "Opslaan", "Enregistrer", "حفظ"],
        "settings": ["Einstellungen", "Settings", "Ajustes", "Impostazioni", "Instellingen", "Paramètres", "الإعدادات"],
        "share_log": ["Protokoll senden", "Send log", "Enviar registro", "Invia registro", "Logboek versturen", "Envoyer le journal", "إرسال السجل"],
        "stage_connected": ["Verbunden – Display wird vorbereitet …", "Connected – preparing display …", "Conectado: preparando la pantalla …", "Connesso – preparazione del display …", "Verbonden – display wordt voorbereid …", "Connecté – préparation de l'écran …", "تم الاتصال – جارٍ تجهيز الشاشة …"],
        "stage_refresh": ["Display aktualisiert – Handy ruhig halten …", "Display updating – hold phone still …", "Actualizando pantalla: mantén el móvil quieto …", "Aggiornamento display – tieni fermo il telefono …", "Display wordt bijgewerkt – telefoon stilhouden …", "Mise à jour de l'écran – ne bouge pas le téléphone …", "جارٍ تحديث الشاشة – أبقِ الهاتف ثابتاً …"],
        "stage_transfer": ["Bild wird übertragen …", "Sending image …", "Enviando imagen …", "Invio immagine …", "Afbeelding wordt verzonden …", "Envoi de l'image …", "جارٍ إرسال الصورة …"],
        "tag_found": ["Display erkannt – nicht bewegen …", "Display detected – don't move …", "Pantalla detectada: no te muevas …", "Display rilevato – non muovere …", "Display herkend – niet bewegen …", "Écran détecté – ne bouge pas …", "تم اكتشاف الشاشة – لا تحرك الهاتف …"],
        "tap_to_transfer": ["Antippen zum Übertragen", "Tap to transfer", "Toca para transferir", "Tocca per trasferire", "Tik om over te zetten", "Appuie pour transférer", "اضغط للنقل"],
        "title_list": ["Pferdeliste", "Horse list", "Lista de caballos", "Elenco cavalli", "Paardenlijst", "Liste des chevaux", "قائمة الخيول"],
        "transfer": ["Übertragen", "Transfer", "Transferir", "Trasferisci", "Overzetten", "Transférer", "نقل"],
        "transfer_title": ["Übertragen: %@", "Transfer: %@", "Transferir: %@", "Trasferisci: %@", "Overzetten: %@", "Transférer : %@", "نقل: %@"],
        "version": ["Version", "Version", "Versión", "Versione", "Versie", "Version", "الإصدار"],
    ]
}
