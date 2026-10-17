#!/usr/bin/env python3
"""Build the offline word lists shipped with KlarTaste.

The lists are original frequency-ranked vocabularies for everyday German and
English. They are not copied from a published word-frequency corpus.
"""

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "engine" / "src" / "main" / "resources"


def casefold(word: str) -> str:
    return word.casefold().replace("ß", "ß")


class Lexicon:
    def __init__(self):
        self.rows = {}

    def add(self, form: str, freq: int):
        form = " ".join(form.split())
        if not form or " " in form:
            return
        key = form.casefold()
        prev = self.rows.get(key)
        if prev is None:
            self.rows[key] = (form, freq)
            return
        prev_form, prev_freq = prev
        # A common lowercase word (essen, the verb) beats a proper noun (Essen).
        if form[:1].islower() and prev_form[:1].isupper() and freq >= 4000:
            self.rows[key] = (form, max(freq, prev_freq))
            return
        if prev_form[:1].islower() and form[:1].isupper() and prev_freq >= 4000:
            self.rows[key] = (prev_form, max(freq, prev_freq))
            return
        if freq > prev_freq:
            self.rows[key] = (form, freq)

    def add_many(self, words, freq):
        for i, word in enumerate(words):
            self.add(word, max(20, freq - i * 3))

    def dump(self, path: Path):
        lines = sorted(self.rows.values(), key=lambda item: (-item[1], item[0].casefold()))
        path.write_text(
            "\n".join(f"{form}\t{freq}" for form, freq in lines) + "\n",
            encoding="utf-8",
        )
        return len(lines)


def regular_verb(lex: Lexicon, infinitive: str, freq: int, inseparable: bool = False):
    # Only the infinitive. Generated conjugations like "öffnst" are wrong often
    # enough that they steal real words such as "öffnet".
    del inseparable
    lex.add(infinitive, freq)


def simple_adjective(lex: Lexicon, base: str, freq: int):
    lex.add(base, freq)
    if base.endswith(("el", "er", "en")):
        return
    for suffix, penalty in (("e", 80), ("er", 90), ("es", 100), ("en", 70), ("em", 120)):
        lex.add(base + suffix, max(30, freq - penalty))


def build_german() -> Lexicon:
    lex = Lexicon()
    nouns = """
    Tastatur Autokorrektur Vorschlag Rechtschreibung Grammatik Zeichensetzung
    Satz Wort Buchstabe Taste Handy Telefon App Nachricht Chat E-Mail Mail
    Fehler Korrektur Sprache Deutsch Englisch Text Feld Einstellung Schlüssel
    Modell Server Verbindung Internet WLAN Passwort Konto Nutzer Mensch Person
    Freund Freundin Familie Mutter Vater Eltern Kind Kinder Sohn Tochter Bruder
    Schwester Oma Opa Baby Haus Wohnung Zimmer Küche Bad Schlafzimmer Tür Fenster
    Tisch Stuhl Bett Sofa Lampe Garten Balkon Straße Weg Platz Stadt Dorf Land
    Berlin Hamburg München Köln Frankfurt Stuttgart Düsseldorf Leipzig Dortmund
    Essen Bremen Dresden Hannover Nürnberg Duisburg Bochum Wuppertal Bielefeld
    Bonn Münster Karlsruhe Mannheim Augsburg Wiesbaden Kiel Aachen Mainz Lübeck
    Erfurt Rostock Kassel Freiburg Heidelberg Darmstadt Regensburg Würzburg Ulm
    Göttingen Potsdam Saarbrücken Wien Zürich Bern Basel Graz Linz Innsbruck
    Deutschland Österreich Schweiz Frankreich Italien Spanien Polen Niederlande
    Belgien Dänemark Schweden Norwegen Finnland Portugal Griechenland Türkei
    Ukraine Russland Europa Asien Afrika Amerika China Japan Indien Brasilien
    Kanada Mexiko Australien England Welt Norden Süden Osten Westen Mitte
    Montag Dienstag Mittwoch Donnerstag Freitag Samstag Sonntag Wochenende
    Januar Februar März April Mai Juni Juli August September Oktober November
    Dezember Woche Monat Jahr Stunde Minute Sekunde Uhr Zeit Morgen Abend Nacht
    Frühling Sommer Herbst Winter Wetter Regen Schnee Sonne Wind Wolke Himmel
    Arbeit Büro Job Beruf Chef Kollege Kollegin Meeting Termin Kalender Projekt
    Aufgabe Frage Antwort Idee Plan Ziel Problem Lösung Grund Beispiel Nummer
    Name Adresse Geburtstag Alter Schule Universität Studium Lehrer Lehrerin
    Schüler Student Prüfung Hausaufgabe Buch Heft Stift Papier Computer Laptop
    Bildschirm Maus Drucker Datei Ordner Foto Bild Video Musik Lied Film Serie
    Spiel Spieler Mannschaft Ball Tor Punkt Preis Geld Euro Konto Rechnung
    Angebot Vertrag Versicherung Steuer Gehalt Bank Karte Bargeld Geschäft
    Laden Markt Supermarkt Brot Milch Käse Butter Ei Fleisch Fisch Gemüse Obst
    Apfel Banane Kartoffel Reis Nudel Suppe Salat Kaffee Tee Wasser Saft Bier
    Wein Kuchen Schokolade Zucker Salz Pfeffer Öl Pfanne Topf Teller Tasse
    Glas Messer Gabel Löffel Frühstück Mittagessen Abendessen Hunger Durst
    Restaurant Café Küche Rezept Geschmack Auto Bus Bahn Zug Fahrrad Flugzeug
    Schiff U-Bahn Straßenbahn Ticket Fahrkarte Bahnhof Flughafen Haltestelle
    Straße Ampel Stau Unfall Werkstatt Benzin Motor Reifen Lenkung Sitz
    Körper Kopf Gesicht Auge Augen Ohr Nase Mund Zahn Zähne Haar Hals Arm Hand
    Finger Bein Fuß Knie Rücken Bauch Herz Lunge Blut Haut Arzt Ärztin
    Krankenhaus Apotheke Medizin Tablette Schmerz Fieber Erkältung Termin
    Gesundheit Sport Training Laufen Schwimmen Yoga Verein Natur Baum Blume
    Gras Wald Berg See Fluss Meer Strand Park Tier Hund Katze Vogel Pferd
    Kuh Schwein Schaf Huhn Fisch Insekt Farbe Rot Blau Grün Gelb Schwarz Weiß
    Grau Braun Orange Lila Pink Kleidung Hose Hemd Kleid Rock Jacke Schuh
    Schuhe Mantel Mütze Schal Tasche Rucksack Größe Farbe Stil Mode Geschenk
    Fest Party Geburtstag Hochzeit Urlaub Reise Hotel Koffer Pass Visum
    Karte Ticket Foto Erinnerung Freude Liebe Glück Angst Ärger Ruhe
    Musik Konzert Theater Museum Kirche Geschichte Politik Regierung
    Präsident Minister Wahl Gesetz Recht Polizei Feuerwehr Hilfe Notruf
    Nachricht Zeitung Radio Fernsehen Internet Seite Link Suche Ergebnis
    Benutzer Passwort Profil Bild Kommentar Beitrag Kanal Gruppe
    Morgen Mittag Nachmittag Abend Nacht heute gestern morgen übermorgen
    Anfang Ende Seite Teil Stück Art Weise Form Inhalt Titel Thema
    Grund Folge Wirkung Ursache Zweck Sinn Bedeutung Unterschied Vergleich
    Vorteil Nachteil Chance Risiko Erfahrung Meinung Ansicht Gefühl
    Stimmung Laune Moment Augenblick Leben Alltag Freizeit Hobby
    Interesse Lust Kraft Energie Licht Schatten Luft Feuer Erde
    Wasser Strom Gas Heizung Müll Recycling Umwelt Klima
    Wohnung Miete Nachbar Nachbarin Schlüssel Briefkasten Treppe Aufzug
    Keller Dach Garten Zaun Tor Einfahrt Garage
    Firma Kunde Kundin Produkt Dienstleistung Qualität Menge Anzahl
    Liste Tabelle Spalte Zeile Wert Prozent Summe Durchschnitt
    System Programm Software Update Version Fehlercode Hinweis Warnung
    Tastaturbelegung Autokorrektur Wortvorschlag Großschreibung
    """.split()
    # "heute" etc. are not nouns; they get overwritten later by function words if needed.
    lex.add_many([w for w in nouns if w[:1].isupper()], 24000)

    adjectives = """
    gut schlecht groß klein neu alt jung schnell langsam schön hässlich
    leicht schwer lang kurz hoch tief weit nah warm kalt heiß kühl
    hell dunkel laut leise stark schwach wichtig möglich richtig falsch
    wahr frei fertig offen geschlossen voll leer teuer billig reich arm
    glücklich traurig müde wach krank gesund hungrig durstig satt
    freundlich nett böse lieb gemein ehrlich vorsichtig gefährlich sicher
    einfach schwierig kompliziert klar deutlich leise laut modern altmodisch
    praktisch nützlich sauber schmutzig trocken nass frisch alt lecker
    süß sauer salzig bitter scharf mild interessant langweilig spannend
    ruhig nervös wütend überrascht bekannt unbekannt öffentlich privat
    gemeinsam eigen fremd lokal international national digital analog
    automatisch manuell kostenlos gratis bezahlt pünktlich verspätet
    vollständig ganz halb doppelt einzeln normal speziell gewöhnlich
    ähnlich gleich unterschiedlich besser schlechter größer kleiner
    früher später heute aktuell bisher zukünftig vergangen gegenwärtig
    positiv negativ neutral ernst lustig komisch seltsam komisch
    rund eckig weich hart glatt rau dick dünn breit schmal
    """.split()
    for i, word in enumerate(dict.fromkeys(adjectives)):
        simple_adjective(lex, word, 9000 - i * 12)

    regular = """
    machen spielen kaufen fragen lernen arbeiten wohnen leben lieben
    suchen brauchen meinen glauben hoffen zeigen öffnen schließen stellen
    legen setzen holen zahlen kosten sparen schicken
    erklären erzählen antworten benutzen prüfen testen starten
    stoppen drücken tippen schreiben lesen hören schauen kochen backen
    waschen putzen reparieren bauen planen feiern lachen weinen warten
    dauern passieren fehlen stimmen passen klingen schmecken riechen
    regnen schneien scheinen scheinen funktionieren klappen reichen
    gehören verbessern besuchen bestellen bezahlen rechnen zählen
    vergleichen wiederholen vorbereiten übersetzen markieren speichern
    löschen kopieren teilen drucken laden schicken erinnern vergessen
    """.split()
    # vergessen/erinnern/backen/bringen are irregular; remove them from regular generation.
    irregular_skip = {"vergessen", "erinnern", "backen", "bringen", "waschen", "empfehlen"}
    for i, verb in enumerate(dict.fromkeys(regular)):
        if verb in irregular_skip or not verb.endswith("en"):
            lex.add(verb, 5000)
            continue
        inseparable = verb.startswith((
            "ver", "be", "ent", "emp", "zer", "miss", "über", "unter",
            "erklär", "erzähl", "erleb", "erreich", "erfüll", "erlaub", "erwart",
        ))
        regular_verb(lex, verb, 7000 - i * 20, inseparable=inseparable)

    irregulars = {
        "sein": "bin bist ist sind seid war warst waren wart gewesen sei seist",
        "haben": "habe hast hat haben habt hatte hattest hatten hattet gehabt",
        "werden": "werde wirst wird werden werdet wurde wurdest wurden wurdet geworden",
        "können": "kann kannst können könnt konnte konntest konnten konntet gekonnt könnte könnten",
        "müssen": "muss musst müssen müsst musste musstest mussten musstet gemusst müsste müssten",
        "dürfen": "darf darfst dürfen dürft durfte durftest durften gedurft dürfte dürften",
        "sollen": "soll sollst sollen sollt sollte solltest sollten solltet gesollt",
        "wollen": "will willst wollen wollt wollte wolltest wollten gewollt möchte möchtest möchten",
        "mögen": "mag magst mögen mögt mochte mochtest mochten gemocht möchte",
        "wissen": "weiß weißt wisst wusste wusstest wussten gewusst wüsste",
        "gehen": "gehe gehst geht ging gingst gingen gegangen",
        "kommen": "komme kommst kommt kam kamst kamen gekommen",
        "sehen": "sehe siehst sieht seht sah sahst sahen gesehen",
        "geben": "gebe gibst gibt gebt gab gabst gaben gegeben",
        "nehmen": "nehme nimmst nimmt nehmt nahm nahmst nahmen genommen",
        "sprechen": "spreche sprichst spricht sprecht sprach sprachst sprachen gesprochen",
        "finden": "finde findest findet fand fandst fanden gefunden",
        "bleiben": "bleibe bleibst bleibt blieb bliebst blieben geblieben",
        "heißen": "heiße heißt heißen hieß hießen geheißen",
        "denken": "denke denkst denkt dachte dachtest dachten gedacht",
        "bringen": "bringe bringst bringt brachte brachtest brachten gebracht",
        "kennen": "kenne kennst kennt kannte kanntest kannten gekannt",
        "stehen": "stehe stehst steht stand standst standen gestanden",
        "liegen": "liege liegst liegt lag lagst lagen gelegen",
        "sitzen": "sitze sitzt saß saßen gesessen",
        "laufen": "laufe läufst läuft lauft lief liefst liefen gelaufen",
        "fahren": "fahre fährst fährt fahrt fuhr fuhrst fuhren gefahren",
        "tragen": "trage trägst trägt tragt trug trugst trugen getragen",
        "schlagen": "schlage schlägst schlägt schlagt schlug schlugen geschlagen",
        "fallen": "falle fällst fällt fallt fiel fielen gefallen",
        "halten": "halte hältst hält haltet hielt hielten gehalten",
        "lassen": "lasse lässt lasst ließ ließen gelassen",
        "rufen": "rufe rufst ruft rief riefen gerufen",
        "schlafen": "schlafe schläfst schläft schlaft schlief geschlafen",
        "essen": "esse isst esst aß aßen gegessen",
        "trinken": "trinke trinkst trinkt trank tranken getrunken",
        "helfen": "helfe hilfst hilft helft half halfen geholfen",
        "treffen": "treffe triffst trifft trefft traf trafen getroffen",
        "vergessen": "vergesse vergisst vergisst vergesst vergaß vergaßen vergessen",
        "beginnen": "beginne beginnst beginnt begann begannen begonnen",
        "gewinnen": "gewinne gewinnst gewinnt gewann gewonnen",
        "verlieren": "verliere verlierst verliert verlor verloren",
        "ziehen": "ziehe ziehst zieht zog zogen gezogen",
        "fliegen": "fliege fliegst fliegt flog flogen geflogen",
        "schreiben": "schreibe schreibst schreibt schrieb geschrieben",
        "lesen": "lese liest lest las lasen gelesen",
        "tun": "tue tust tut tat tatst taten getan",
        "werdenlassen": "",
    }
    for i, (inf, forms) in enumerate(irregulars.items()):
        if not forms:
            continue
        lex.add(inf, 14000 - i * 30)
        for form in forms.split():
            lex.add(form, 8000 - i * 10)

    extras = """
    Tastatur Tasse Tasche Taste Task Taskleiste Tastaturkurzbefehl
    Empfehlung empfohlen empfehlen empfehle empfiehlst empfiehlt empfohlen
    separat separate separaten separat getrennte getrennt
    vielleicht Wahrscheinlichkeit wahrscheinlich
    bisschen bissehen
    Standard Standards standardisieren
    Maschine Maschinen maschinell
    Adresse Adressen adressieren
    nämlich deshalb trotzdem überhaupt ausversehen
    interessant interessante interessanten Interesse
    definitiv Definition definiert
    Entwickler Entwicklung entwickeln entwickelt
    Benutzer Benutzername benutzen benutzt
    Nachricht Nachrichten benachrichtigen
    korrigieren korrigiert Korrektur korrigiere
    automatisch automatische automatischen Automatik
    Grammatik grammatisch grammatische
    Zeichensetzung Zeichen Satzzeichen
    Rechtschreibung rechtschreiben
    Vorschlag Vorschläge vorschlagen
    Einstellung Einstellungen einstellen
    Schlüssel Schlüsselbund
    Verbindung verbinden verbunden
    Straße Straßen Straßenseite
    Grüße Gruß begrüßen begrüßt
    Danke Dank danken dankbar
    Bitte bitten bittet bat gebeten
    Hallo hallo Hi hey
    Tschüss Wiedersehen Auf Wiedersehen
    Guten Morgen Guten Abend Gute Nacht
    Freundschaft freundlich
    Entscheidung entscheiden entschieden
    Möglichkeit Möglichkeiten ermöglicht
    Information Informationen informieren informiert
    Situation Situationen
    Diskussion diskutieren diskutiert
    Erfahrung Erfahrungen erleben erlebt
    Erinnerung erinnern erinnert
    Vorstellung vorstellen vorgestellt
    Bedeutung bedeuten bedeutet
    Erklärung erklären erklärt
    Beschreibung beschreiben beschrieben
    Anleitung anleiten
    Beispiel Beispiele beispielsweise
    eigentlich ursprünglich schließlich außerdem außerdem
    jedenfalls irgendwie irgendwas irgendetwas irgendwann irgendwo
    niemand jemand etwas nichts alles jeder jede jedes jeden jedem
    man mann Männer Frau Frauen Leute Menschen Person Personen
    Herr Frau Doktor Professor
    links rechts geradeaus zurück vorwärts oben unten drinnen draußen
    heute gestern morgen bald schon noch immer nie oft selten manchmal
    jetzt gerade eben sofort später früh spät pünktlich
    sehr mehr weniger viel viele allem allemal ganz fast etwa ungefähr
    nur sogar auch noch nicht nichts nie niemand
    bitte danke sorry entschuldigung Entschuldigung
    ok okay super toll klasse schade leider hoffentlich
    App Apps SMS PDF WLAN GPS USB HDMI WLAN-Passwort
    iPhone Android Windows Linux
    Berlin München Köln Hamburg Frankfurt Wien Zürich
    Euro Cent Dollar
    Montag Dienstag Mittwoch Donnerstag Freitag Samstag Sonntag
    Ja Nein doch Doch
    """.split()
    lex.add_many(extras, 6000)

    # Everyday phrases stored as single tokens are useless; real phrases go to aliases.
    function = """
    der die das und in den von zu mit sich des auf für ist im dem nicht ein eine
    als auch es an werden aus er hat dass sie nach wird bei einer um am sind noch
    wie einem über einen so zum war haben nur oder aber vor zur bis mehr durch man
    sein wurde sei hatte kann gegen vom können schon wenn habe seine ihre dann unter
    wir soll ich eines diese dieser wieder keine seiner worden will zwischen immer
    was sagte hier mir dir uns ihnen ihm ihr eure euer dein meine meinem meinen
    meiner meines deiner deinem deinen ihres ihrem ihren ja nein doch bitte danke
    mal eben halt wohl eigentlich natürlich leider vielleicht schon noch immer
    gar sehr ganz etwa nur sogar auch sowie sowie sowie
    am im zum zur vom beim ins ans aufs übers unters vors hinterm
    """.split()
    for i, word in enumerate(dict.fromkeys(function)):
        lex.add(word, 1_000_000 - i * 80)

    # Force canonical forms that must beat other readings.
    for form, freq in {
        "Tastatur": 90000,
        "Empfehlung": 40000,
        "separat": 30000,
        "vielleicht": 200000,
        "wahrscheinlich": 80000,
        "bisschen": 70000,
        "Standard": 50000,
        "Maschine": 40000,
        "Adresse": 40000,
        "nämlich": 60000,
        "deshalb": 70000,
        "trotzdem": 70000,
        "überhaupt": 80000,
        "interessant": 40000,
        "definitiv": 30000,
        "dass": 900000,
        "das": 980000,
        "wir": 900000,
        "wie": 880000,
        "ich": 990000,
        "und": 995000,
        "nicht": 970000,
        "schon": 400000,
        "schön": 80000,
        "hello": 1000,
        "Fehler": 50000,
        "Satz": 40000,
        "Straße": 45000,
        "muss": 250000,
        "lässt": 80000,
        "Auto": 60000,
        "Berlin": 70000,
        "OK": 60000,
        "App": 50000,
        "SMS": 20000,
        "öffnen": 12000,
        "öffne": 6000,
        "öffnest": 4000,
        "öffnet": 8000,
        "öffnete": 4000,
        "geöffnet": 7000,
    }.items():
        lex.add(form, freq)
    return lex


def build_english() -> Lexicon:
    lex = Lexicon()
    lemmas = """
    the be to of and a in that have I it for not on with he as you do at this
    but his by from they we say her she or an will my one all would there their
    what so up out if about who get which go me when make can like time no just
    him know take people into year your good some could them see other than then
    now look only come its over think also back after use two how our work first
    well way even new want because any these give day most us hello thanks please
    sorry yes okay hey hi bye today tomorrow yesterday morning evening night week
    month year hour minute second today always never sometimes often rarely soon
    later early late now here there home house room door window table chair bed
    food water coffee tea bread milk friend family mother father child parents
    brother sister school work office job meeting message email phone computer
    keyboard screen photo video music song movie game money price street city
    country world weather rain sun book idea question answer problem solution
    name address number password account user person people thing place time
    life love help need want try start stop open close write read listen watch
    talk speak call text send receive buy pay eat drink sleep walk run drive
    live stay leave arrive happen seem feel become keep let put mean change
    learn remember forget understand believe hope show find give take make
    correct correction spelling grammar sentence word letter error mistake
    automatic suggestion language english german setting model server connection
    internet really actually maybe probably definitely something anything
    nothing everything someone anyone everyone important possible different
    same small large little big good bad new old right wrong true false free
    full empty easy hard fast slow happy sad tired sick healthy hungry ready
    sure clear simple special normal local public private digital
    receive separate interesting definitely address machine standard already
    because before after again another around available awesome beautiful
    because friend friends message please thanks thank welcome
    keyboard autocorrect suggestion
    """.split()
    # Contractions and common forms are added explicitly.
    forms = {
        "be": "am is are was were been being",
        "have": "has had having",
        "do": "does did done doing",
        "go": "goes went gone going",
        "get": "gets got gotten getting",
        "make": "makes made making",
        "know": "knows knew known knowing",
        "think": "thinks thought thinking",
        "take": "takes took taken taking",
        "see": "sees saw seen seeing",
        "come": "comes came coming",
        "want": "wants wanted wanting",
        "look": "looks looked looking",
        "use": "uses used using",
        "work": "works worked working",
        "call": "calls called calling",
        "try": "tries tried trying",
        "need": "needs needed needing",
        "feel": "feels felt feeling",
        "become": "becomes became becoming",
        "leave": "leaves left leaving",
        "put": "puts putting",
        "mean": "means meant meaning",
        "keep": "keeps kept keeping",
        "let": "lets letting",
        "begin": "begins began begun beginning",
        "write": "writes wrote written writing",
        "read": "reads reading",
        "speak": "speaks spoke spoken speaking",
        "send": "sends sent sending",
        "receive": "receives received receiving",
        "buy": "buys bought buying",
        "pay": "pays paid paying",
        "eat": "eats ate eaten eating",
        "drink": "drinks drank drunk drinking",
        "sleep": "sleeps slept sleeping",
        "run": "runs ran running",
        "drive": "drives drove driven driving",
        "live": "lives lived living",
        "stay": "stays stayed staying",
        "find": "finds found finding",
        "give": "gives gave given giving",
        "tell": "tells told telling",
        "ask": "asks asked asking",
        "seem": "seems seemed seeming",
        "help": "helps helped helping",
        "change": "changes changed changing",
        "learn": "learns learned learning",
        "remember": "remembers remembered remembering",
        "forget": "forgets forgot forgotten forgetting",
        "understand": "understands understood understanding",
        "believe": "believes believed believing",
        "hope": "hopes hoped hoping",
        "show": "shows showed shown showing",
        "start": "starts started starting",
        "stop": "stops stopped stopping",
        "open": "opens opened opening",
        "close": "closes closed closing",
        "watch": "watches watched watching",
        "listen": "listens listened listening",
        "talk": "talks talked talking",
        "move": "moves moved moving",
        "play": "plays played playing",
        "turn": "turns turned turning",
        "correct": "corrects corrected correcting correction",
    }
    for i, lemma in enumerate(dict.fromkeys(lemmas)):
        if lemma == "I":
            continue
        lex.add(lemma, 40000 - i * 15)
        extra = forms.get(lemma)
        if extra:
            for form in extra.split():
                lex.add(form, 20000 - i * 8)
        elif lemma.endswith("e"):
            lex.add(lemma + "d", 8000)
            lex.add(lemma[:-1] + "ing", 8000)
        elif lemma.endswith("y") and len(lemma) > 3 and lemma[-2] not in "aeiou":
            lex.add(lemma[:-1] + "ies", 7000)
            lex.add(lemma[:-1] + "ied", 7000)
        elif len(lemma) > 3 and lemma.isalpha():
            lex.add(lemma + "s", 7000)
            lex.add(lemma + "ed", 6000)
            lex.add(lemma + "ing", 6000)

    for form, freq in {
        "the": 1_000_000,
        "be": 900000,
        "to": 890000,
        "of": 880000,
        "and": 870000,
        "a": 860000,
        "in": 850000,
        "that": 840000,
        "have": 830000,
        "it": 820000,
        "for": 810000,
        "not": 800000,
        "on": 790000,
        "with": 780000,
        "he": 770000,
        "as": 760000,
        "you": 750000,
        "do": 740000,
        "at": 730000,
        "this": 720000,
        "but": 710000,
        "his": 700000,
        "by": 690000,
        "from": 680000,
        "they": 670000,
        "we": 660000,
        "she": 650000,
        "or": 640000,
        "an": 630000,
        "will": 620000,
        "my": 610000,
        "one": 600000,
        "all": 590000,
        "would": 580000,
        "there": 570000,
        "their": 560000,
        "what": 550000,
        "so": 540000,
        "up": 530000,
        "out": 520000,
        "if": 510000,
        "about": 500000,
        "who": 490000,
        "get": 480000,
        "which": 470000,
        "go": 460000,
        "me": 450000,
        "when": 440000,
        "make": 430000,
        "can": 420000,
        "like": 410000,
        "time": 400000,
        "no": 390000,
        "just": 380000,
        "him": 370000,
        "know": 360000,
        "take": 350000,
        "people": 340000,
        "into": 330000,
        "year": 320000,
        "your": 310000,
        "good": 300000,
        "some": 290000,
        "could": 280000,
        "them": 270000,
        "see": 260000,
        "other": 250000,
        "than": 240000,
        "then": 230000,
        "now": 220000,
        "look": 210000,
        "only": 200000,
        "come": 190000,
        "its": 180000,
        "over": 170000,
        "think": 160000,
        "also": 150000,
        "back": 140000,
        "after": 130000,
        "use": 120000,
        "two": 110000,
        "how": 100000,
        "our": 90000,
        "work": 80000,
        "first": 70000,
        "well": 60000,
        "because": 90000,
        "hello": 80000,
        "thanks": 50000,
        "please": 50000,
        "sorry": 40000,
        "yes": 70000,
        "okay": 40000,
        "receive": 30000,
        "separate": 30000,
        "definitely": 25000,
        "interesting": 20000,
        "address": 30000,
        "machine": 20000,
        "standard": 20000,
        "keyboard": 20000,
        "don't": 200000,
        "can't": 120000,
        "won't": 80000,
        "I'm": 180000,
        "I've": 60000,
        "it's": 160000,
        "that's": 80000,
        "there's": 40000,
        "they're": 50000,
        "we're": 40000,
        "you're": 70000,
        "didn't": 60000,
        "doesn't": 50000,
        "isn't": 50000,
        "aren't": 30000,
        "wasn't": 25000,
        "weren't": 20000,
        "haven't": 25000,
        "hasn't": 20000,
        "wouldn't": 25000,
        "couldn't": 25000,
        "shouldn't": 25000,
        "let's": 20000,
        "OK": 40000,
        "app": 20000,
    }.items():
        lex.add(form, freq)
    return lex


ALIASES = """
daß	dass
dasss	dass
muß	muss
mußte	musste
müßte	müsste
läßt	lässt
paßt	passt
küßt	küsst
Schluß	Schluss
schluß	Schluss
standart	Standard
standart	Standard
bischen	bisschen
bischen	bisschen
wiederspiegeln	widerspiegeln
Maschiene	Maschine
Empfelung	Empfehlung
emphelung	Empfehlung
seperat	separat
seperate	separate
definitv	definitiv
viellecht	vielleicht
villeicht	vielleicht
vielliecht	vielleicht
warscheinlich	wahrscheinlich
wahscheinlich	wahrscheinlich
ineressant	interessant
interressant	interessant
Addresse	Adresse
zuviel	zu viel
zuwenig	zu wenig
garnicht	gar nicht
immernoch	immer noch
desshalb	deshalb
deshalbt	deshalb
ausversehen	aus Versehen
trozdem	trotzdem
nähmlich	nämlich
namlich	nämlich
überhaubt	überhaupt
überhaubpt	überhaupt
wier	wir
ihc	ich
nichtt	nicht
shcon	schon
helllo	hello
recieve	receive
recieved	received
seperate	separate
definately	definitely
wierd	weird
becuase	because
becaus	because
teh	the
adn	and
dont	don't
cant	can't
wont	won't
doesnt	doesn't
didnt	didn't
isnt	isn't
wasnt	wasn't
couldnt	couldn't
wouldnt	wouldn't
shouldnt	shouldn't
im	I'm
ive	I've
Tastaur	Tastatur
taststur	Tastatur
tasttur	Tastatur
feher	Fehler
""".strip()


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    de = build_german().dump(OUT / "lexicon_de.txt")
    en = build_english().dump(OUT / "lexicon_en.txt")
    (OUT / "aliases.txt").write_text(ALIASES.strip() + "\n", encoding="utf-8")
    print(f"de={de} en={en} aliases={len(ALIASES.splitlines())}")


if __name__ == "__main__":
    main()
