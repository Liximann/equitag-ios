package de.kopfnummer.app;

/** Ein Pferd mit Name und Kopfnummer (max. 4 Ziffern). */
public final class Horse {
    public final long id;
    public final String name;
    public final String number;

    public Horse(long id, String name, String number) {
        this.id = id;
        this.name = name;
        this.number = number;
    }
}
