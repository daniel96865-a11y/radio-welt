package de.klartaste

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import de.klartaste.engine.Board
import de.klartaste.engine.CorrectionEngine
import de.klartaste.engine.TextTarget
import de.klartaste.engine.TypingSession
import java.util.concurrent.Executors

class ImeService : InputMethodService(), KeyboardView.Listener {
    private lateinit var prefs: Prefs
    private lateinit var engine: CorrectionEngine
    private lateinit var session: TypingSession
    private var boardView: KeyboardView? = null
    private var target: ImeTarget? = null
    private var sensitive = false
    private var noLearn = false
    private var enterLabel = "⏎"
    private var aiRequest = 0
    private var ignoreSelection = 0
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private val aiTask = Runnable { runAi(force = false) }

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        engine = prefs.newEngine()
        session = TypingSession(engine, IdleTarget)
        window.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }

    override fun onDestroy() {
        io.shutdownNow()
        main.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onCreateInputView(): View {
        return KeyboardView(this, null).also {
            it.listener = this
            boardView = it
        }
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onStartInput(info: EditorInfo, restarting: Boolean) {
        super.onStartInput(info, restarting)
        sensitive = isSensitive(info)
        noLearn = sensitive || (info.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0
        enterLabel = enterLabel(info)
        val connection = currentInputConnection
        if (connection != null) {
            target = ImeTarget(connection) { noteEdit() }
            session = TypingSession(engine, target!!)
            applyPrefs()
            session.reset()
            val klass = info.inputType and InputType.TYPE_MASK_CLASS
            if (klass == InputType.TYPE_CLASS_NUMBER || klass == InputType.TYPE_CLASS_PHONE) {
                session.show(Board.SYMBOLS)
            }
        }
        main.removeCallbacks(aiTask)
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        engine.language = prefs.language
        if (::session.isInitialized) applyPrefs()
        render()
    }

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int,
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        if (ignoreSelection > 0) {
            ignoreSelection--
            return
        }
        if (::session.isInitialized && session.composing.isNotEmpty()) {
            session.abandonComposing()
            render()
        }
    }

    override fun onKey(action: KeyAction) {
        if (!::session.isInitialized) return
        feedback()
        when (action) {
            KeyAction.Backspace -> session.backspace()
            KeyAction.Shift -> session.toggleShift()
            KeyAction.CapsLock -> session.capsLock()
            KeyAction.Space -> session.space()
            KeyAction.Enter -> onEnter()
            KeyAction.Symbols -> session.show(Board.SYMBOLS)
            KeyAction.MoreSymbols -> session.show(Board.MORE_SYMBOLS)
            KeyAction.Letters -> session.show(Board.LETTERS)
            KeyAction.Emoji -> session.show(Board.EMOJI)
            KeyAction.Language -> cycleLanguage()
            KeyAction.Ai -> runAi(force = true)
            KeyAction.Hide -> requestHideSelf(0)
            else -> Unit
        }
        if (action != KeyAction.Ai && action != KeyAction.Hide) afterEdit()
    }

    override fun onText(text: String) {
        if (!::session.isInitialized) return
        feedback()
        if (text.length == 1 && text[0].isLetter() && text[0].isLowerCase()) session.typeChar(text[0])
        else session.typeText(text)
        afterEdit()
    }

    override fun onSuggestion(text: String) {
        if (!::session.isInitialized) return
        feedback()
        session.pickSuggestion(text)
        afterEdit()
    }

    private fun onEnter() {
        val info = currentInputEditorInfo
        val connection = currentInputConnection
        val action = info?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: EditorInfo.IME_ACTION_NONE
        val multiline = info?.inputType?.and(InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0
        val noEnter = info?.imeOptions?.and(EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
        session.commitWordOnly()
        if (!noEnter && !multiline && connection != null &&
            action != EditorInfo.IME_ACTION_NONE &&
            action != EditorInfo.IME_ACTION_UNSPECIFIED
        ) {
            noteEdit()
            connection.performEditorAction(action)
        } else {
            session.newline()
        }
    }

    private fun cycleLanguage() {
        prefs.language = when (prefs.language) {
            de.klartaste.engine.LanguageMode.GERMAN -> de.klartaste.engine.LanguageMode.ENGLISH
            de.klartaste.engine.LanguageMode.ENGLISH -> de.klartaste.engine.LanguageMode.BOTH
            de.klartaste.engine.LanguageMode.BOTH -> de.klartaste.engine.LanguageMode.GERMAN
        }
        engine.language = prefs.language
    }

    private fun afterEdit() {
        prefs.saveLearned(engine)
        render()
        scheduleAi()
    }

    private fun applyPrefs() {
        session.autoCorrect = prefs.autocorrect && !sensitive
        session.suggestionsOn = prefs.suggestions && !sensitive
        session.autoCap = prefs.autoCap
        session.doubleSpacePeriod = prefs.doubleSpace
        session.learning = !noLearn
        engine.language = prefs.language
    }

    private fun render() {
        val view = boardView ?: return
        val hint = when {
            sensitive -> "Ohne Korrektur"
            prefs.aiReady() -> "KI an"
            else -> "KlarTaste"
        }
        view.model = KeyboardModel(
            suggestions = if (::session.isInitialized) session.suggestions else emptyList(),
            shift = if (::session.isInitialized) session.shift else de.klartaste.engine.ShiftMode.OFF,
            shiftOn = if (::session.isInitialized) session.shiftHighlighted() else false,
            board = if (::session.isInitialized) session.board else Board.LETTERS,
            qwertz = prefs.qwertz,
            languageLabel = prefs.languageLabel(),
            enterLabel = enterLabel,
            dark = prefs.darkKeys,
            hint = hint,
        )
        val board = if (prefs.darkKeys) 0xFF17191E.toInt() else 0xFFD9D3C7.toInt()
        window.window?.setBackgroundDrawable(ColorDrawable(board))
    }

    private fun scheduleAi() {
        main.removeCallbacks(aiTask)
        if (sensitive || noLearn || !prefs.aiReady() || session.composing.isNotEmpty()) return
        main.postDelayed(aiTask, 1100L)
    }

    private fun runAi(force: Boolean) {
        if (!::session.isInitialized || session.composing.isNotEmpty() || sensitive || noLearn) return
        if (force) main.removeCallbacks(aiTask)
        if (!force && !prefs.aiReady()) return
        if (prefs.aiBase.isBlank() || prefs.aiModel.isBlank()) return
        if (prefs.aiKey.isBlank() && !prefs.aiBase.lowercase().startsWith("http://")) return
        val fragment = session.fragment()
        if (fragment.length < 8 || !fragment.contains(' ')) return
        if (fragment.contains("http") || fragment.contains("@")) return
        val request = ++aiRequest
        val base = prefs.aiBase
        val model = prefs.aiModel
        val key = prefs.aiKey
        io.execute {
            val outcome = AiClient.correct(base, model, key, fragment)
            main.post {
                if (request != aiRequest || session.composing.isNotEmpty()) return@post
                if (outcome.text != null && outcome.text != fragment) {
                    session.replaceTail(fragment, outcome.text)
                    prefs.lastAiError = ""
                    render()
                } else if (outcome.error != null) {
                    prefs.lastAiError = outcome.error
                }
            }
        }
    }

    private fun feedback() {
        val view = boardView ?: return
        if (prefs.haptic) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        if (prefs.sound) {
            val audio = getSystemService(AUDIO_SERVICE) as AudioManager
            audio.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD, 0.35f)
        }
    }

    private fun noteEdit() {
        ignoreSelection += 2
        main.postDelayed({
            if (ignoreSelection > 0) ignoreSelection--
        }, 150L)
    }

    private fun isSensitive(info: EditorInfo): Boolean {
        val klass = info.inputType and InputType.TYPE_MASK_CLASS
        val variation = info.inputType and InputType.TYPE_MASK_VARIATION
        if (klass == InputType.TYPE_CLASS_NUMBER || klass == InputType.TYPE_CLASS_PHONE) return true
        return variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
            variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
    }

    private fun enterLabel(info: EditorInfo): String {
        return when (info.imeOptions and EditorInfo.IME_MASK_ACTION) {
            EditorInfo.IME_ACTION_GO -> "Los"
            EditorInfo.IME_ACTION_SEARCH -> "Suche"
            EditorInfo.IME_ACTION_SEND -> "Senden"
            EditorInfo.IME_ACTION_NEXT -> "Weiter"
            EditorInfo.IME_ACTION_DONE -> "Fertig"
            else -> "⏎"
        }
    }
}

private object IdleTarget : TextTarget {
    override fun contextBefore(max: Int) = ""
    override fun replaceComposing(text: String) = Unit
    override fun commitComposingAnd(extra: String) = Unit
    override fun commit(text: String) = Unit
    override fun deleteBefore(count: Int) = Unit
    override fun abandonComposing() = Unit
}

private class ImeTarget(
    private val connection: InputConnection,
    private val onEdit: () -> Unit,
) : TextTarget {
    private var local = ""

    override fun contextBefore(max: Int): String {
        val raw = connection.getTextBeforeCursor(max + local.length + 16, 0)?.toString().orEmpty()
        val base = if (local.isNotEmpty() && raw.endsWith(local)) raw.dropLast(local.length) else raw
        return base.takeLast(max)
    }

    override fun replaceComposing(text: String) {
        onEdit()
        local = text
        connection.setComposingText(text, 1)
    }

    override fun commitComposingAnd(extra: String) {
        onEdit()
        if (local.isNotEmpty()) {
            connection.setComposingText(local, 1)
            connection.finishComposingText()
            local = ""
        }
        if (extra.isNotEmpty()) connection.commitText(extra, 1)
    }

    override fun commit(text: String) {
        onEdit()
        if (local.isNotEmpty()) {
            connection.finishComposingText()
            local = ""
        }
        if (text.isNotEmpty()) connection.commitText(text, 1)
    }

    override fun deleteBefore(count: Int) {
        onEdit()
        if (local.isNotEmpty()) {
            connection.finishComposingText()
            local = ""
        }
        if (count > 0) connection.deleteSurroundingText(count, 0)
    }

    override fun abandonComposing() {
        onEdit()
        if (local.isNotEmpty()) connection.finishComposingText()
        local = ""
    }
}
