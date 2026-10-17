package de.klartaste

import de.klartaste.engine.Board

enum class KeyStyle { NORMAL, SPECIAL, ACCENT, SPACE }

sealed class KeyAction {
    data class Text(val text: String) : KeyAction()
    data object Backspace : KeyAction()
    data object Shift : KeyAction()
    data object CapsLock : KeyAction()
    data object Space : KeyAction()
    data object Enter : KeyAction()
    data object Symbols : KeyAction()
    data object MoreSymbols : KeyAction()
    data object Letters : KeyAction()
    data object Emoji : KeyAction()
    data object Language : KeyAction()
    data object Ai : KeyAction()
    data object Hide : KeyAction()
    data object Spacer : KeyAction()
}

data class KeySpec(
    val label: String,
    val action: KeyAction,
    val weight: Float = 1f,
    val longPress: List<String> = emptyList(),
    val style: KeyStyle = KeyStyle.NORMAL,
)

object Boards {
    fun rows(board: Board, qwertz: Boolean): List<List<KeySpec>> = when (board) {
        Board.LETTERS -> letters(qwertz)
        Board.SYMBOLS -> symbols()
        Board.MORE_SYMBOLS -> moreSymbols()
        Board.EMOJI -> emoji()
    }

    private fun letters(qwertz: Boolean): List<List<KeySpec>> {
        val top = if (qwertz) "qwertzuiopü" else "qwertyuiop"
        val home = if (qwertz) "asdfghjklöä" else "asdfghjkl"
        val bottom = if (qwertz) "yxcvbnm" else "zxcvbnm"
        val rows = ArrayList<List<KeySpec>>()
        rows.add(top.map { letter(it) })
        if (qwertz) {
            rows.add(home.map { letter(it) })
        } else {
            rows.add(listOf(spacer(0.5f)) + home.map { letter(it) } + spacer(0.5f))
        }
        val third = ArrayList<KeySpec>()
        third.add(KeySpec("⇧", KeyAction.Shift, 1.45f, style = KeyStyle.SPECIAL))
        third.addAll(bottom.map { letter(it) })
        if (qwertz) third.add(letter('ß'))
        third.add(KeySpec("⌫", KeyAction.Backspace, 1.45f, style = KeyStyle.SPECIAL))
        rows.add(third)
        rows.add(bottomRow(lettersKey()))
        return rows
    }

    private fun lettersKey() = KeySpec("123", KeyAction.Symbols, 1.35f, style = KeyStyle.SPECIAL)

    private fun bottomRow(modeKey: KeySpec): List<KeySpec> = listOf(
        modeKey,
        KeySpec("DE", KeyAction.Language, 1.15f, style = KeyStyle.SPECIAL),
        KeySpec("☺", KeyAction.Emoji, 1.1f, style = KeyStyle.SPECIAL),
        KeySpec(",", KeyAction.Text(","), longPress = listOf(";", ":", "-", "–", "'")),
        KeySpec("", KeyAction.Space, 4.3f, style = KeyStyle.SPACE),
        KeySpec(".", KeyAction.Text("."), longPress = listOf("!", "?", "…")),
        KeySpec("KI", KeyAction.Ai, 1.15f, style = KeyStyle.ACCENT),
        KeySpec("⏎", KeyAction.Enter, 1.45f, style = KeyStyle.ACCENT),
    )

    private fun symbols(): List<List<KeySpec>> = listOf(
        "1234567890".map { KeySpec(it.toString(), KeyAction.Text(it.toString())) },
        listOf("@", "#", "€", "_", "&", "-", "+", "(", ")", "/").map { symbol(it) },
        listOf(
            KeySpec("#+=", KeyAction.MoreSymbols, 1.4f, style = KeyStyle.SPECIAL),
        ) + listOf("*", "\"", "'", ":", ";", "!", "?").map { symbol(it) } + listOf(
            KeySpec("⌫", KeyAction.Backspace, 1.4f, style = KeyStyle.SPECIAL),
        ),
        listOf(
            KeySpec("ABC", KeyAction.Letters, 1.45f, style = KeyStyle.SPECIAL),
            KeySpec(",", KeyAction.Text(",")),
            KeySpec("", KeyAction.Space, 4.5f, style = KeyStyle.SPACE),
            KeySpec(".", KeyAction.Text(".")),
            KeySpec("⏎", KeyAction.Enter, 1.45f, style = KeyStyle.ACCENT),
        ),
    )

    private fun moreSymbols(): List<List<KeySpec>> = listOf(
        listOf("~", "`", "|", "·", "√", "π", "÷", "×", "¶", "∆").map { symbol(it) },
        listOf("£", "$", "¥", "^", "°", "=", "{", "}", "\\", "©").map { symbol(it) },
        listOf(
            KeySpec("123", KeyAction.Symbols, 1.4f, style = KeyStyle.SPECIAL),
        ) + listOf("%", "™", "✓", "[", "]", "<", ">").map { symbol(it) } + listOf(
            KeySpec("⌫", KeyAction.Backspace, 1.4f, style = KeyStyle.SPECIAL),
        ),
        listOf(
            KeySpec("ABC", KeyAction.Letters, 1.45f, style = KeyStyle.SPECIAL),
            KeySpec(",", KeyAction.Text(",")),
            KeySpec("", KeyAction.Space, 4.5f, style = KeyStyle.SPACE),
            KeySpec(".", KeyAction.Text(".")),
            KeySpec("⏎", KeyAction.Enter, 1.45f, style = KeyStyle.ACCENT),
        ),
    )

    private fun emoji(): List<List<KeySpec>> {
        val faces = listOf("😀", "😁", "😂", "🤣", "😊", "😍", "😘", "😎", "🤔", "😅")
        val more = listOf("😢", "😭", "😡", "😴", "🤗", "🙃", "👍", "👎", "👏", "🙏")
        val marks = listOf("❤️", "🔥", "✨", "🎉", "✅", "❌", "⭐", "💯", "📌", "💬")
        return listOf(
            faces.map { KeySpec(it, KeyAction.Text(it)) },
            more.map { KeySpec(it, KeyAction.Text(it)) },
            marks.map { KeySpec(it, KeyAction.Text(it)) },
            listOf(
                KeySpec("ABC", KeyAction.Letters, 1.45f, style = KeyStyle.SPECIAL),
                KeySpec("", KeyAction.Space, 4.5f, style = KeyStyle.SPACE),
                KeySpec("⌫", KeyAction.Backspace, 1.45f, style = KeyStyle.SPECIAL),
            ),
        )
    }

    private fun letter(ch: Char): KeySpec {
        val extras = when (ch) {
            'a' -> listOf("ä", "à", "á", "â")
            'e' -> listOf("é", "è", "ê", "ë", "€")
            'i' -> listOf("í", "ì", "î", "ï")
            'o' -> listOf("ö", "ó", "ò", "ô")
            'u' -> listOf("ü", "ú", "ù", "û")
            's' -> listOf("ß", "ś")
            'n' -> listOf("ñ")
            'c' -> listOf("ç")
            'y' -> listOf("ý")
            'z' -> listOf("ž")
            'ß' -> listOf("ẞ")
            else -> emptyList()
        }
        return KeySpec(ch.toString(), KeyAction.Text(ch.toString()), longPress = extras)
    }

    private fun symbol(text: String) = KeySpec(text, KeyAction.Text(text))

    private fun spacer(weight: Float) = KeySpec("", KeyAction.Spacer, weight)
}
