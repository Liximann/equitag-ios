package de.kopfnummer.app;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Texte der App in 7 Sprachen. Reihenfolge: de, en, es, it, nl, fr, ar. */
public final class I18n {
    public static final String[] CODES = {"de", "en", "es", "it", "nl", "fr", "ar"};
    public static final String[] NAMES = {"Deutsch", "English", "Español", "Italiano", "Nederlands", "Français", "العربية"};

    private static final Map<String, String[]> T = new HashMap<String, String[]>();
    private static int lang = 0;

    private static void put(String key, String... values) { T.put(key, values); }

    public static void setLanguage(String code) {
        lang = index(code);
    }

    public static int index(String code) {
        for (int i = 0; i < CODES.length; i++) if (CODES[i].equals(code)) return i;
        return 1; // nicht unterstützt -> Englisch
    }

    /** Sprache: gespeicherte Wahl, sonst Systemsprache, sonst Englisch. */
    public static String resolve(String saved) {
        if (saved != null && saved.length() > 0) return saved;
        String sys = Locale.getDefault().getLanguage();
        return CODES[index(sys)];
    }

    public static String t(String key) {
        String[] v = T.get(key);
        if (v == null) return key;
        return v[lang];
    }

    public static String t(String key, Object... args) {
        return String.format(t(key), args);
    }

    static {
        put("title_list", "Pferdeliste", "Horse list", "Lista de caballos", "Elenco cavalli", "Paardenlijst", "Liste des chevaux", "قائمة الخيول");
        put("empty",
                "Noch keine Pferde angelegt.\n\nTippe unten auf  +  um ein Pferd mit Namen und Kopfnummer hinzuzufügen.",
                "No horses yet.\n\nTap  +  at the bottom to add a horse with its name and head number.",
                "Todavía no hay caballos.\n\nToca  +  abajo para añadir un caballo con su nombre y número de cabezada.",
                "Nessun cavallo ancora.\n\nTocca  +  in basso per aggiungere un cavallo con nome e numero di testiera.",
                "Nog geen paarden.\n\nTik onderaan op  +  om een paard met naam en hoofdnummer toe te voegen.",
                "Aucun cheval pour l'instant.\n\nAppuie sur  +  en bas pour ajouter un cheval avec son nom et son numéro de tête.",
                "لا توجد خيول بعد.\n\nاضغط على  +  في الأسفل لإضافة حصان باسمه ورقمه.");
        put("add_horse", "Pferd hinzufügen", "Add horse", "Añadir caballo", "Aggiungi cavallo", "Paard toevoegen", "Ajouter un cheval", "إضافة حصان");
        put("edit_horse", "Pferd bearbeiten", "Edit horse", "Editar caballo", "Modifica cavallo", "Paard bewerken", "Modifier le cheval", "تعديل الحصان");
        put("horse_name", "Pferdename", "Horse name", "Nombre del caballo", "Nome del cavallo", "Naam van het paard", "Nom du cheval", "اسم الحصان");
        put("name_hint", "z. B. Selfie", "e.g. Selfie", "p. ej. Selfie", "es. Selfie", "bijv. Selfie", "ex. Selfie", "مثال: Selfie");
        put("number_label", "Kopfnummer (max. 4 Ziffern)", "Head number (max. 4 digits)", "Número de cabezada (máx. 4 dígitos)",
                "Numero di testiera (max. 4 cifre)", "Hoofdnummer (max. 4 cijfers)", "Numéro de tête (4 chiffres max.)", "رقم الحصان (4 أرقام كحد أقصى)");
        put("number_hint", "z. B. 821", "e.g. 821", "p. ej. 821", "es. 821", "bijv. 821", "ex. 821", "مثال: 821");
        put("save", "Speichern", "Save", "Guardar", "Salva", "Opslaan", "Enregistrer", "حفظ");
        put("cancel", "Abbrechen", "Cancel", "Cancelar", "Annulla", "Annuleren", "Annuler", "إلغاء");
        put("err_name", "Bitte Pferdenamen eingeben", "Please enter a horse name", "Introduce el nombre del caballo",
                "Inserisci il nome del cavallo", "Voer de naam van het paard in", "Saisis le nom du cheval", "يرجى إدخال اسم الحصان");
        put("err_number", "1 bis 4 Ziffern", "1 to 4 digits", "De 1 a 4 dígitos", "Da 1 a 4 cifre", "1 tot 4 cijfers", "1 à 4 chiffres", "من 1 إلى 4 أرقام");
        put("transfer", "Übertragen", "Transfer", "Transferir", "Trasferisci", "Overzetten", "Transférer", "نقل");
        put("edit", "Bearbeiten", "Edit", "Editar", "Modifica", "Bewerken", "Modifier", "تعديل");
        put("delete", "Löschen", "Delete", "Eliminar", "Elimina", "Verwijderen", "Supprimer", "حذف");
        put("delete_confirm", "„%s“ wirklich löschen?", "Delete \"%s\"?", "¿Eliminar «%s»?", "Eliminare «%s»?",
                "\"%s\" verwijderen?", "Supprimer « %s » ?", "هل تريد حذف \"%s\"؟");
        put("tap_to_transfer", "Antippen zum Übertragen", "Tap to transfer", "Toca para transferir", "Tocca per trasferire",
                "Tik om over te zetten", "Appuie pour transférer", "اضغط للنقل");
        put("no_nfc_title", "Kein NFC", "No NFC", "Sin NFC", "Nessun NFC", "Geen NFC", "Pas de NFC", "لا يوجد NFC");
        put("no_nfc_msg",
                "Dieses Handy hat kein NFC und kann das Display nicht beschreiben.",
                "This phone has no NFC and cannot update the display.",
                "Este móvil no tiene NFC y no puede actualizar la pantalla.",
                "Questo telefono non ha NFC e non può aggiornare il display.",
                "Deze telefoon heeft geen NFC en kan het display niet bijwerken.",
                "Ce téléphone n'a pas de NFC et ne peut pas mettre à jour l'écran.",
                "هذا الهاتف لا يدعم NFC ولا يمكنه تحديث الشاشة.");
        put("nfc_off_title", "NFC ist ausgeschaltet", "NFC is turned off", "NFC está desactivado", "NFC è disattivato",
                "NFC staat uit", "Le NFC est désactivé", "NFC متوقف");
        put("nfc_off_msg", "Bitte NFC in den Einstellungen einschalten.", "Please turn on NFC in the settings.",
                "Activa NFC en los ajustes.", "Attiva NFC nelle impostazioni.", "Zet NFC aan in de instellingen.",
                "Active le NFC dans les paramètres.", "يرجى تشغيل NFC من الإعدادات.");
        put("settings", "Einstellungen", "Settings", "Ajustes", "Impostazioni", "Instellingen", "Paramètres", "الإعدادات");
        put("ok", "OK", "OK", "OK", "OK", "OK", "OK", "حسناً");
        put("close", "Schließen", "Close", "Cerrar", "Chiudi", "Sluiten", "Fermer", "إغلاق");
        put("transfer_title", "Übertragen: %s", "Transfer: %s", "Transferir: %s", "Trasferisci: %s", "Overzetten: %s",
                "Transférer : %s", "نقل: %s");
        put("hold_phone",
                "Halte das Handy jetzt flach an die Rückseite des Displays.",
                "Now hold your phone flat against the back of the display.",
                "Sujeta ahora el móvil plano contra la parte trasera de la pantalla.",
                "Ora tieni il telefono appoggiato al retro del display.",
                "Houd je telefoon nu plat tegen de achterkant van het display.",
                "Tiens maintenant ton téléphone à plat contre l'arrière de l'écran.",
                "ضع الهاتف الآن بشكل مسطح على الجهة الخلفية للشاشة.");
        put("tag_found", "Display erkannt – nicht bewegen …", "Display detected – don't move …",
                "Pantalla detectada: no te muevas …", "Display rilevato – non muovere …", "Display herkend – niet bewegen …",
                "Écran détecté – ne bouge pas …", "تم اكتشاف الشاشة – لا تحرك الهاتف …");
        put("stage_connected", "Verbunden – Display wird vorbereitet …", "Connected – preparing display …",
                "Conectado: preparando la pantalla …", "Connesso – preparazione del display …",
                "Verbonden – display wordt voorbereid …", "Connecté – préparation de l'écran …",
                "تم الاتصال – جارٍ تجهيز الشاشة …");
        put("stage_transfer", "Bild wird übertragen …", "Sending image …", "Enviando imagen …", "Invio immagine …",
                "Afbeelding wordt verzonden …", "Envoi de l'image …", "جارٍ إرسال الصورة …");
        put("stage_refresh", "Display aktualisiert – Handy ruhig halten …", "Display updating – hold phone still …",
                "Actualizando pantalla: mantén el móvil quieto …", "Aggiornamento display – tieni fermo il telefono …",
                "Display wordt bijgewerkt – telefoon stilhouden …", "Mise à jour de l'écran – ne bouge pas le téléphone …",
                "جارٍ تحديث الشاشة – أبقِ الهاتف ثابتاً …");
        put("done", "✓  Übertragung abgeschlossen", "✓  Transfer complete", "✓  Transferencia completada",
                "✓  Trasferimento completato", "✓  Overdracht voltooid", "✓  Transfert terminé", "✓  اكتمل النقل");
        put("retry_hint", "Zum Wiederholen das Handy erneut anhalten.", "Hold the phone against the display again to retry.",
                "Vuelve a acercar el móvil para reintentar.", "Riavvicina il telefono per riprovare.",
                "Houd de telefoon opnieuw tegen het display om het nog eens te proberen.",
                "Approche à nouveau le téléphone pour réessayer.", "قرّب الهاتف مرة أخرى لإعادة المحاولة.");
        put("err_not_supported", "Kein passendes Display erkannt.", "No compatible display detected.",
                "No se detectó ninguna pantalla compatible.", "Nessun display compatibile rilevato.",
                "Geen compatibel display gevonden.", "Aucun écran compatible détecté.", "لم يتم اكتشاف شاشة متوافقة.");
        put("err_bad_response", "Unerwartete Antwort des Displays.", "Unexpected response from the display.",
                "Respuesta inesperada de la pantalla.", "Risposta inattesa dal display.", "Onverwacht antwoord van het display.",
                "Réponse inattendue de l'écran.", "استجابة غير متوقعة من الشاشة.");
        put("err_no_confirm", "Das Display hat das Ende der Aktualisierung nicht bestätigt.",
                "The display did not confirm that the update finished.",
                "La pantalla no confirmó el final de la actualización.",
                "Il display non ha confermato la fine dell'aggiornamento.",
                "Het display heeft het einde van de update niet bevestigd.",
                "L'écran n'a pas confirmé la fin de la mise à jour.", "لم تؤكد الشاشة انتهاء التحديث.");
        put("err_connection", "Verbindung abgebrochen. Handy ruhig an das Display halten.",
                "Connection lost. Hold the phone still against the display.",
                "Conexión perdida. Mantén el móvil quieto junto a la pantalla.",
                "Connessione interrotta. Tieni il telefono fermo sul display.",
                "Verbinding verbroken. Houd de telefoon stil tegen het display.",
                "Connexion perdue. Tiens le téléphone immobile contre l'écran.",
                "انقطع الاتصال. أبقِ الهاتف ثابتاً على الشاشة.");
        put("err_generic", "Fehler: %s", "Error: %s", "Error: %s", "Errore: %s", "Fout: %s", "Erreur : %s", "خطأ: %s");
        put("help", "Hilfe", "Help", "Ayuda", "Aiuto", "Help", "Aide", "مساعدة");
        put("help_title", "So funktioniert's", "How it works", "Cómo funciona", "Come funziona", "Zo werkt het",
                "Comment ça marche", "طريقة الاستخدام");
        put("help_text",
                "1. Mit  +  Pferde mit Name und Kopfnummer anlegen.\n\n"
                        + "2. Pferd antippen und das Handy flach an die Rückseite des Displays halten (der NFC-Bereich liegt meist mittig oder oben auf der Handy-Rückseite).\n\n"
                        + "3. Handy ruhig halten, bis „Übertragung abgeschlossen“ erscheint (ca. 10 Sekunden).\n\n"
                        + "Pferd lange gedrückt halten = bearbeiten oder löschen.\n\n"
                        + "Steht die Anzeige auf dem Kopf, in den Einstellungen „Anzeige um 180° drehen“ aktivieren.",
                "1. Tap  +  to add horses with name and head number.\n\n"
                        + "2. Tap a horse and hold your phone flat against the back of the display (the NFC area is usually in the middle or top of the phone's back).\n\n"
                        + "3. Hold still until “Transfer complete” appears (about 10 seconds).\n\n"
                        + "Long-press a horse to edit or delete it.\n\n"
                        + "If the image appears upside down, turn on “Rotate display 180°” in the settings.",
                "1. Toca  +  para añadir caballos con nombre y número de cabezada.\n\n"
                        + "2. Toca un caballo y sujeta el móvil plano contra la parte trasera de la pantalla (la zona NFC suele estar en el centro o arriba de la parte trasera del móvil).\n\n"
                        + "3. Mantén el móvil quieto hasta que aparezca «Transferencia completada» (unos 10 segundos).\n\n"
                        + "Mantén pulsado un caballo para editarlo o eliminarlo.\n\n"
                        + "Si la imagen aparece al revés, activa «Girar la pantalla 180°» en los ajustes.",
                "1. Tocca  +  per aggiungere cavalli con nome e numero di testiera.\n\n"
                        + "2. Tocca un cavallo e appoggia il telefono al retro del display (l'area NFC di solito è al centro o in alto sul retro del telefono).\n\n"
                        + "3. Tieni fermo il telefono finché non compare «Trasferimento completato» (circa 10 secondi).\n\n"
                        + "Tieni premuto un cavallo per modificarlo o eliminarlo.\n\n"
                        + "Se l'immagine è capovolta, attiva «Ruota il display di 180°» nelle impostazioni.",
                "1. Tik op  +  om paarden met naam en hoofdnummer toe te voegen.\n\n"
                        + "2. Tik op een paard en houd je telefoon plat tegen de achterkant van het display (de NFC-zone zit meestal in het midden of bovenaan op de achterkant van de telefoon).\n\n"
                        + "3. Houd de telefoon stil tot „Overdracht voltooid” verschijnt (ongeveer 10 seconden).\n\n"
                        + "Houd een paard lang ingedrukt om het te bewerken of te verwijderen.\n\n"
                        + "Staat de afbeelding ondersteboven, zet dan „Display 180° draaien” aan in de instellingen.",
                "1. Appuie sur  +  pour ajouter des chevaux avec leur nom et leur numéro de tête.\n\n"
                        + "2. Appuie sur un cheval et tiens ton téléphone à plat contre l'arrière de l'écran (la zone NFC se trouve généralement au milieu ou en haut du dos du téléphone).\n\n"
                        + "3. Ne bouge pas jusqu'à ce que « Transfert terminé » s'affiche (environ 10 secondes).\n\n"
                        + "Appui long sur un cheval pour le modifier ou le supprimer.\n\n"
                        + "Si l'image est à l'envers, active « Pivoter l'affichage de 180° » dans les paramètres.",
                "1. اضغط على  +  لإضافة خيول بالاسم ورقم الحصان.\n\n"
                        + "2. اضغط على حصان وضع الهاتف بشكل مسطح على الجهة الخلفية للشاشة (تكون منطقة NFC عادةً في منتصف أو أعلى ظهر الهاتف).\n\n"
                        + "3. أبقِ الهاتف ثابتاً حتى تظهر رسالة \"اكتمل النقل\" (حوالي 10 ثوانٍ).\n\n"
                        + "اضغط مطولاً على حصان لتعديله أو حذفه.\n\n"
                        + "إذا ظهرت الصورة مقلوبة، فعّل خيار \"تدوير العرض 180°\" في الإعدادات.");
        put("invert", "Farben umkehren", "Invert colors", "Invertir colores", "Inverti colori", "Kleuren omkeren",
                "Inverser les couleurs", "عكس الألوان");
        put("invert_desc",
                "Aktivieren, falls das Display weiße Schrift auf schwarzem Grund zeigt.",
                "Turn on if the display shows white text on a black background.",
                "Actívalo si la pantalla muestra texto blanco sobre fondo negro.",
                "Attivalo se il display mostra testo bianco su sfondo nero.",
                "Zet dit aan als het display witte tekst op een zwarte achtergrond toont.",
                "Active-le si l'écran affiche du texte blanc sur fond noir.",
                "فعّله إذا عرضت الشاشة نصاً أبيض على خلفية سوداء.");
        put("display_info", "Display: %1$d × %2$d Pixel, %3$d Farben", "Display: %1$d × %2$d pixels, %3$d colors",
                "Pantalla: %1$d × %2$d píxeles, %3$d colores", "Display: %1$d × %2$d pixel, %3$d colori",
                "Display: %1$d × %2$d pixels, %3$d kleuren", "Écran : %1$d × %2$d pixels, %3$d couleurs",
                "الشاشة: %1$d × %2$d بكسل، %3$d ألوان");
        put("err_pin", "Das Display ist mit einer PIN geschützt. Bitte beim Hersteller die PIN-Sperre entfernen lassen.",
                "The display is protected by a PIN. Please ask the manufacturer to remove the PIN lock.",
                "La pantalla está protegida con un PIN. Pide al fabricante que quite el bloqueo.",
                "Il display è protetto da PIN. Chiedi al produttore di rimuovere il blocco.",
                "Het display is beveiligd met een pincode. Vraag de fabrikant de pincode te verwijderen.",
                "L'écran est protégé par un code PIN. Demande au fabricant de retirer le verrouillage.",
                "الشاشة محمية برمز PIN. يرجى مطالبة الشركة المصنعة بإزالة القفل.");
        put("err_unsupported", "Dieser Displaytyp wird noch nicht unterstützt (%s).", "This display type is not supported yet (%s).",
                "Este tipo de pantalla aún no es compatible (%s).", "Questo tipo di display non è ancora supportato (%s).",
                "Dit type display wordt nog niet ondersteund (%s).", "Ce type d'écran n'est pas encore pris en charge (%s).",
                "نوع الشاشة هذا غير مدعوم بعد (%s).");
        put("err_screen", "Das Display meldet einen Anschlussfehler des Bildschirms. Bitte prüfen und erneut versuchen.",
                "The display reports a screen connection error. Please check and try again.",
                "La pantalla indica un error de conexión. Compruébala y vuelve a intentarlo.",
                "Il display segnala un errore di collegamento dello schermo. Controlla e riprova.",
                "Het display meldt een aansluitfout van het scherm. Controleer en probeer opnieuw.",
                "L'écran signale une erreur de connexion. Vérifie et réessaie.",
                "تُبلغ الشاشة عن خطأ في التوصيل. يرجى التحقق والمحاولة مرة أخرى.");
        put("err_step", "Unerwartete Antwort des Displays (%s).", "Unexpected response from the display (%s).",
                "Respuesta inesperada de la pantalla (%s).", "Risposta inattesa dal display (%s).",
                "Onverwacht antwoord van het display (%s).", "Réponse inattendue de l'écran (%s).",
                "استجابة غير متوقعة من الشاشة (%s).");
        put("share_log", "Protokoll senden", "Send log", "Enviar registro", "Invia registro", "Logboek versturen",
                "Envoyer le journal", "إرسال السجل");
        put("language", "Sprache", "Language", "Idioma", "Lingua", "Taal", "Langue", "اللغة");
        put("rotate180", "Anzeige um 180° drehen", "Rotate display 180°", "Girar la pantalla 180°", "Ruota il display di 180°",
                "Display 180° draaien", "Pivoter l'affichage de 180°", "تدوير العرض 180°");
        put("rotate_desc",
                "Aktivieren, falls die Nummer auf dem Display auf dem Kopf steht.",
                "Turn on if the number appears upside down on the display.",
                "Actívalo si el número aparece al revés en la pantalla.",
                "Attivalo se il numero appare capovolto sul display.",
                "Zet dit aan als het nummer ondersteboven op het display staat.",
                "Active-le si le numéro s'affiche à l'envers sur l'écran.",
                "فعّله إذا ظهر الرقم مقلوباً على الشاشة.");
    }

    private I18n() {}
}
