package de.klartaste.engine

interface TextTarget {
    fun contextBefore(max: Int): String
    fun replaceComposing(text: String)
    fun commitComposingAnd(extra: String)
    fun commit(text: String)
    fun deleteBefore(count: Int)
    fun abandonComposing()
}

enum class ShiftMode { OFF, ONCE, LOCK }

enum class Board { LETTERS, SYMBOLS, MORE_SYMBOLS, EMOJI }

class TypingSession(
    val engine: CorrectionEngine,
    val target: TextTarget,
) {
    var autoCorrect: Boolean = true
    var suggestionsOn: Boolean = true
    var autoCap: Boolean = true
    var doubleSpacePeriod: Boolean = true
    var learning: Boolean = true
    var clock: () -> Long = { System.currentTimeMillis() }

    var shift: ShiftMode = ShiftMode.OFF
        private set
    var board: Board = Board.LETTERS
        private set
    var composing: String = ""
        private set

    private var pendingUndo: Undo? = null
    private var lastSpaceAt: Long = Long.MIN_VALUE

    val suggestions: List<String>
        get() = if (!suggestionsOn || composing.isEmpty()) emptyList() else engine.suggest(composing)

    fun shiftHighlighted(): Boolean {
        return shift != ShiftMode.OFF || (composing.isEmpty() && autoCap && sentenceStart())
    }

    fun reset() {
        composing = ""
        pendingUndo = null
        shift = ShiftMode.OFF
        board = Board.LETTERS
        lastSpaceAt = Long.MIN_VALUE
    }

    fun toggleShift() {
        shift = when (shift) {
            ShiftMode.OFF -> ShiftMode.ONCE
            ShiftMode.ONCE -> ShiftMode.OFF
            ShiftMode.LOCK -> ShiftMode.OFF
        }
    }

    fun capsLock() {
        shift = if (shift == ShiftMode.LOCK) ShiftMode.OFF else ShiftMode.LOCK
    }

    fun show(board: Board) {
        if (composing.isNotEmpty()) commitWord(extra = "")
        this.board = board
    }

    fun typeChar(ch: Char) {
        if (!ch.isLetter()) {
            if (composing.isNotEmpty()) commitWord(ch.toString()) else target.commit(ch.toString())
            if (shift == ShiftMode.ONCE) shift = ShiftMode.OFF
            pendingUndo = null
            return
        }
        composing += shape(ch)
        target.replaceComposing(composing)
        if (shift == ShiftMode.ONCE) shift = ShiftMode.OFF
        pendingUndo = null
    }

    fun typeText(text: String) {
        if (text.isEmpty()) return
        val wordLike = text.all { it.isLetter() || it == '\'' || it == '-' || it == 'ß' }
        if (wordLike) {
            composing += text
            target.replaceComposing(composing)
        } else {
            if (composing.isNotEmpty()) commitWord("")
            target.commit(text)
        }
        if (shift == ShiftMode.ONCE) shift = ShiftMode.OFF
        pendingUndo = null
    }

    fun space() {
        val now = clock()
        if (composing.isEmpty() && doubleSpacePeriod && now - lastSpaceAt in 0..700) {
            val before = target.contextBefore(8)
            if (before.endsWith(" ") && before.length >= 2 && before[before.length - 2].isLetterOrDigit()) {
                target.deleteBefore(1)
                target.commit(". ")
                lastSpaceAt = Long.MIN_VALUE
                pendingUndo = null
                return
            }
        }
        if (composing.isEmpty()) {
            target.commit(" ")
            lastSpaceAt = now
            pendingUndo = null
            return
        }
        val typed = composing
        val corrected = corrected(typed)
        target.replaceComposing(corrected)
        target.commitComposingAnd(" ")
        composing = ""
        pendingUndo = if (autoCorrect && corrected != typed) Undo(typed, corrected) else null
        lastSpaceAt = now
        if (shift == ShiftMode.ONCE) shift = ShiftMode.OFF
    }

    fun backspace() {
        if (composing.isNotEmpty()) {
            composing = composing.dropLast(1)
            target.replaceComposing(composing)
            pendingUndo = null
            return
        }
        val undo = pendingUndo
        val before = target.contextBefore(180)
        if (undo != null && before.endsWith(undo.committed + " ")) {
            target.deleteBefore(undo.committed.length + 1)
            composing = undo.typed
            target.replaceComposing(composing)
            if (learning) engine.learn(undo.typed)
            pendingUndo = null
            return
        }
        if (before.isNotEmpty()) target.deleteBefore(1)
        pendingUndo = null
    }

    fun newline() {
        if (composing.isNotEmpty()) commitWord("\n") else target.commit("\n")
        if (shift == ShiftMode.ONCE) shift = ShiftMode.OFF
    }

    fun commitWordOnly() {
        if (composing.isNotEmpty()) commitWord("")
    }

    fun pickSuggestion(value: String) {
        val clean = value.trim()
        if (clean.isEmpty()) return
        target.replaceComposing(clean)
        target.commitComposingAnd(" ")
        composing = ""
        if (learning) engine.learn(clean)
        pendingUndo = null
        lastSpaceAt = clock()
        if (shift == ShiftMode.ONCE) shift = ShiftMode.OFF
    }

    fun fragment(): String = Sentences.current(target.contextBefore(500) + composing)

    fun replaceTail(original: String, updated: String): Boolean {
        if (original.isBlank() || original == updated || composing.isNotEmpty()) return false
        val before = target.contextBefore(700)
        val trimmed = before.trimEnd()
        if (!trimmed.endsWith(original)) return false
        val trailing = before.substring(trimmed.length)
        target.deleteBefore(original.length + trailing.length)
        target.commit(updated + trailing)
        pendingUndo = null
        return true
    }

    fun abandonComposing() {
        if (composing.isNotEmpty()) target.abandonComposing()
        composing = ""
        pendingUndo = null
    }

    private fun commitWord(extra: String) {
        val typed = composing
        val corrected = corrected(typed)
        target.replaceComposing(corrected)
        target.commitComposingAnd(extra)
        composing = ""
        pendingUndo = null
    }

    private fun corrected(typed: String): String {
        if (!autoCorrect || !typed.any { it.isLetter() }) return typed
        return engine.correctWord(typed)
    }

    private fun shape(ch: Char): Char {
        val cap = shift == ShiftMode.LOCK || shift == ShiftMode.ONCE ||
            (composing.isEmpty() && autoCap && sentenceStart())
        if (!cap) return ch
        return if (ch == 'ß') 'ẞ' else ch.uppercaseChar()
    }

    private fun sentenceStart(): Boolean {
        val ctx = target.contextBefore(24).trimEnd()
        if (ctx.isEmpty()) return true
        return ctx.last() == '.' || ctx.last() == '!' || ctx.last() == '?' || ctx.last() == '\n'
    }

    private data class Undo(val typed: String, val committed: String)
}

object Sentences {
    fun current(text: String): String {
        val trimmed = text.trimEnd()
        if (trimmed.isEmpty()) return ""
        var start = 0
        for (i in trimmed.length - 2 downTo 0) {
            val ch = trimmed[i]
            if (ch == '.' || ch == '!' || ch == '?' || ch == '\n') {
                start = i + 1
                break
            }
        }
        return trimmed.substring(start).trim()
    }
}
