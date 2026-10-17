package de.klartaste.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineTest {
    private val engine = CorrectionEngine.load(
        germanText = resource("lexicon_de.txt"),
        englishText = resource("lexicon_en.txt"),
        aliasText = resource("aliases.txt"),
    )

    @Test
    fun keepsKnownWordsAndCapitalizesNouns() {
        assertEquals("das", engine.correctWord("das"))
        assertEquals("dass", engine.correctWord("dass"))
        assertEquals("schon", engine.correctWord("schon"))
        assertEquals("schön", engine.correctWord("schön"))
        assertEquals("Tastatur", engine.correctWord("tastatur"))
        assertEquals("Tastatur", engine.correctWord("Tastatur"))
        assertEquals("ich", engine.correctWord("ich"))
        assertEquals("hello", engine.correctWord("hello"))
        assertEquals("the", engine.correctWord("the"))
        assertEquals("ok", engine.correctWord("ok"))
        assertEquals("SMS", engine.correctWord("SMS"))
    }

    @Test
    fun fixesTypos() {
        val cases = listOf(
            "Tastaur" to "Tastatur",
            "undd" to "und",
            "seperat" to "separat",
            "Empfelung" to "Empfehlung",
            "wier" to "wir",
            "ihc" to "ich",
            "recieve" to "receive",
            "teh" to "the",
            "helllo" to "hello",
            "standart" to "Standard",
            "daß" to "dass",
            "garnicht" to "gar nicht",
            "villeicht" to "vielleicht",
            "feher" to "Fehler",
            "TASTAUR" to "TASTATUR",
        )
        val failures = cases.mapNotNull { (from, to) ->
            val got = engine.correctWord(from)
            if (got == to) null else "$from -> $got (wanted $to)"
        }
        assertEquals(emptyList<String>(), failures)
    }

    @Test
    fun doesNotInventACorrection() {
        assertEquals("ne", engine.correctWord("ne"))
        assertEquals("öffnet", engine.correctWord("öffnet"))
        assertEquals("im", engine.correctWord("im"))
        assertEquals("ABCD", engine.correctWord("ABCD"))
        assertEquals("Xyqq", engine.correctWord("Xyqq"))
        assertEquals("a", engine.correctWord("a"))
        assertEquals("user@mail.de", engine.correctWord("user@mail.de"))
    }

    @Test
    fun unfinishedPrefixStaysUntilItIsUnambiguous() {
        assertEquals("tast", engine.correctWord("tast"))
        val suggestions = engine.suggest("tast")
        assertTrue(suggestions.any { it == "Tastatur" })
        assertEquals("Tastatur", engine.correctWord("tastatu"))
    }

    @Test
    fun undoIsRemembered() {
        engine.learn("tastatur")
        assertEquals("tastatur", engine.correctWord("tastatur"))
    }

    @Test
    fun suggestionsLeadWithWhatSpaceWillInsert() {
        assertEquals("Tastatur", engine.suggest("Tastaur").first())
        assertEquals("hello", engine.suggest("helllo").first())
    }

    @Test
    fun tinyLexiconDoesNotStripGermanEndings() {
        val small = CorrectionEngine(
            german = Lexicon(listOf(Lexicon.Entry("schön", 1000), Lexicon.Entry("und", 5000))),
            english = Lexicon(emptyList()),
        )
        assertEquals("schöne", small.correctWord("schöne"))
        assertEquals("und", small.correctWord("undd"))
    }

    private fun resource(name: String): String {
        val stream = javaClass.classLoader.getResourceAsStream(name)
            ?: error("missing resource $name")
        return stream.bufferedReader(Charsets.UTF_8).readText()
    }
}

class TypingSessionTest {
    @Test
    fun spaceCorrectsAndBackspaceRestores() {
        val editor = EditorModel()
        val engine = CorrectionEngine.load(
            resource("lexicon_de.txt"),
            resource("lexicon_en.txt"),
            resource("aliases.txt"),
        )
        val session = TypingSession(engine, editor)
        session.autoCap = false
        "tastatur".forEach { session.typeChar(it) }
        session.space()
        assertEquals("Tastatur ", editor.text())
        session.backspace()
        assertEquals("tastatur", editor.text())
        session.space()
        assertEquals("tastatur ", editor.text())
    }

    @Test
    fun sentenceStartAndDoubleSpace() {
        val editor = EditorModel()
        val engine = CorrectionEngine.load(
            resource("lexicon_de.txt"),
            resource("lexicon_en.txt"),
            resource("aliases.txt"),
        )
        val session = TypingSession(engine, editor)
        var now = 1_000L
        session.clock = { now }
        "hallo".forEach { session.typeChar(it) }
        assertEquals("Hallo", editor.text())
        session.space()
        now += 200
        session.space()
        assertEquals("Hallo. ", editor.text())
    }

    @Test
    fun suggestionInsertAndAiReplacement() {
        val editor = EditorModel()
        val engine = CorrectionEngine.load(
            resource("lexicon_de.txt"),
            resource("lexicon_en.txt"),
            resource("aliases.txt"),
        )
        val session = TypingSession(engine, editor)
        session.pickSuggestion("Tastatur")
        assertEquals("Tastatur ", editor.text())
        session.typeText("ist")
        session.space()
        assertEquals("Tastatur ist ", editor.text())
        val fragment = session.fragment()
        assertEquals("Tastatur ist", fragment)
        assertTrue(session.replaceTail(fragment, "Die Tastatur ist gut"))
        assertEquals("Die Tastatur ist gut ", editor.text())
    }

    @Test
    fun sentenceFragmentStopsAtPunctuation() {
        assertEquals("das ist falsch", Sentences.current("Hallo. das ist falsch "))
        assertEquals("Hallo.", Sentences.current("Hallo."))
    }

    private fun resource(name: String): String {
        val stream = javaClass.classLoader.getResourceAsStream(name)
            ?: error("missing resource $name")
        return stream.bufferedReader(Charsets.UTF_8).readText()
    }
}

class ChatJsonTest {
    @Test
    fun readsEscapedContent() {
        val json = """{"choices":[{"message":{"role":"assistant","content":"Das ist ein Satz.\nWirklich."}}]}"""
        assertEquals("Das ist ein Satz.\nWirklich.", ChatJson.assistantContent(json))
        assertEquals("Das ist ein Satz.", ChatJson.clean("\"Das ist ein Satz.\""))
    }

    @Test
    fun ignoresNonStringContent() {
        val json = """{"message":{"content":[{"type":"text"}]}}"""
        assertEquals(null, ChatJson.assistantContent(json))
    }
}

private class EditorModel : TextTarget {
    private val committed = StringBuilder()
    private var composing = ""

    fun text(): String = committed.toString() + composing

    override fun contextBefore(max: Int): String = committed.toString().takeLast(max)

    override fun replaceComposing(text: String) {
        composing = text
    }

    override fun commitComposingAnd(extra: String) {
        committed.append(composing).append(extra)
        composing = ""
    }

    override fun commit(text: String) {
        committed.append(composing).append(text)
        composing = ""
    }

    override fun deleteBefore(count: Int) {
        check(composing.isEmpty())
        val next = (committed.length - count).coerceAtLeast(0)
        committed.setLength(next)
    }

    override fun abandonComposing() {
        committed.append(composing)
        composing = ""
    }
}
