package de.klartaste

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import de.klartaste.engine.Board
import de.klartaste.engine.ShiftMode
import kotlin.math.min

data class KeyboardModel(
    val suggestions: List<String> = emptyList(),
    val shift: ShiftMode = ShiftMode.OFF,
    val shiftOn: Boolean = false,
    val board: Board = Board.LETTERS,
    val qwertz: Boolean = true,
    val languageLabel: String = "DE+",
    val enterLabel: String = "⏎",
    val dark: Boolean = true,
    val hint: String = "KlarTaste",
)

class KeyboardView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    interface Listener {
        fun onKey(action: KeyAction)
        fun onText(text: String)
        fun onSuggestion(text: String)
    }

    var listener: Listener? = null
    var model: KeyboardModel = KeyboardModel()
        set(value) {
            field = value
            if (width > 0 && height > 0) layoutKeys(width, height)
            invalidate()
        }

    private val keys = ArrayList<DrawnKey>()
    private val suggestionHits = ArrayList<Pair<RectF, String>>()
    private val suggestionBar = RectF()
    private val hideRect = RectF()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val handler = Handler(Looper.getMainLooper())
    private var downKey: DrawnKey? = null
    private var downSuggestion: String? = null
    private var downHide = false
    private var popup: Popup? = null
    private var longConsumed = false
    private var repeating = false

    init {
        isHapticFeedbackEnabled = true
        isClickable = true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec).takeIf { it > 0 }
            ?: resources.displayMetrics.widthPixels
        val density = resources.displayMetrics.density
        val landscape = width > resources.displayMetrics.heightPixels
        val key = (if (landscape) 46f else 52f) * density
        val gap = 5f * density
        val suggestions = 48f * density
        val wanted = (suggestions + 4 * key + 5 * gap).toInt()
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val offered = MeasureSpec.getSize(heightMeasureSpec)
        val height = when (heightMode) {
            MeasureSpec.AT_MOST -> min(wanted, offered)
            MeasureSpec.EXACTLY -> offered
            else -> wanted
        }
        setMeasuredDimension(width, height.coerceAtLeast((suggestions + key).toInt()))
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        layoutKeys(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        val palette = palette()
        canvas.drawColor(palette.board)
        fill.color = palette.suggestion
        canvas.drawRoundRect(suggestionBar, dp(12f), dp(12f), fill)
        if (model.suggestions.isEmpty()) {
            drawLabel(canvas, suggestionBar, model.hint, palette.muted, 13f)
        }
        for ((rect, label) in suggestionHits) {
            fill.color = palette.key
            canvas.drawRoundRect(rect, dp(10f), dp(10f), fill)
            drawLabel(canvas, rect, label, palette.text, if (label.length > 12) 12f else 15f)
        }
        fill.color = palette.special
        canvas.drawRoundRect(hideRect, dp(10f), dp(10f), fill)
        drawLabel(canvas, hideRect, "⌄", palette.muted, 18f)

        val popupNow = popup
        for (key in keys) {
            if (key.spec.action is KeyAction.Spacer) continue
            val pressed = key == downKey && popupNow == null
            fill.color = when {
                pressed && key.spec.style == KeyStyle.ACCENT -> palette.accentPressed
                pressed -> palette.pressed
                key.spec.style == KeyStyle.ACCENT -> palette.accent
                key.spec.style == KeyStyle.SPECIAL -> palette.special
                key.spec.action is KeyAction.Shift && model.shift == ShiftMode.LOCK -> palette.accent
                key.spec.action is KeyAction.Shift && model.shiftOn -> palette.pressed
                else -> palette.key
            }
            canvas.drawRoundRect(key.rect, dp(8f), dp(8f), fill)
            val label = displayLabel(key.spec)
            val color = if (key.spec.style == KeyStyle.ACCENT || (key.spec.action is KeyAction.Shift && model.shift == ShiftMode.LOCK)) {
                palette.onAccent
            } else {
                palette.text
            }
            if (key.spec.style == KeyStyle.SPACE) {
                drawLabel(canvas, key.rect, "KlarTaste", palette.muted, 12f)
            } else {
                drawLabel(canvas, key.rect, label, color, labelSize(label))
            }
        }
        popupNow?.let { drawPopup(canvas, it, palette) }
        if (popupNow == null) downKey?.let { drawPreview(canvas, it, palette) }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                begin(event.x, event.y)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                move(event.x, event.y)
                return true
            }
            MotionEvent.ACTION_UP -> {
                finish(event.x, event.y)
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                cancelTouch()
                return true
            }
        }
        return true
    }

    private fun begin(x: Float, y: Float) {
        cancelTouch()
        val suggestion = suggestionHits.firstOrNull { it.first.contains(x, y) }
        if (suggestion != null) {
            downSuggestion = suggestion.second
            return
        }
        if (hideRect.contains(x, y)) {
            downHide = true
            invalidate()
            return
        }
        val key = keyAt(x, y) ?: return
        downKey = key
        invalidate()
        when (key.spec.action) {
            KeyAction.Backspace -> {
                repeating = true
                listener?.onKey(KeyAction.Backspace)
                handler.postDelayed(repeat, 320L)
            }
            else -> handler.postDelayed(longPress, 280L)
        }
    }

    private fun move(x: Float, y: Float) {
        val shown = popup
        if (shown != null) {
            val width = shown.rect.width() / shown.options.size
            val index = ((x - shown.rect.left) / width).toInt().coerceIn(0, shown.options.lastIndex)
            if (index != shown.index) {
                shown.index = index
                invalidate()
            }
            return
        }
        if (downSuggestion != null || downHide || repeating) return
        val key = keyAt(x, y)
        if (key != downKey) {
            handler.removeCallbacks(longPress)
            downKey = key
            if (key != null) handler.postDelayed(longPress, 280L)
            invalidate()
        }
    }

    private fun finish(x: Float, y: Float) {
        handler.removeCallbacks(longPress)
        handler.removeCallbacks(repeat)
        val shown = popup
        val key = downKey
        val suggestion = downSuggestion
        val hide = downHide
        popup = null
        downKey = null
        downSuggestion = null
        downHide = false
        repeating = false
        invalidate()
        when {
            shown != null -> listener?.onText(shown.options[shown.index])
            longConsumed -> Unit
            suggestion != null && suggestionHits.any { it.second == suggestion && it.first.contains(x, y) } ->
                listener?.onSuggestion(suggestion)
            hide && hideRect.contains(x, y) -> listener?.onKey(KeyAction.Hide)
            key != null && key.rect.contains(x, y) -> dispatch(key.spec)
        }
        longConsumed = false
    }

    private fun dispatch(spec: KeySpec) {
        when (val action = spec.action) {
            is KeyAction.Text -> {
                if (action.text.length == 1 && action.text[0].isLetter()) listener?.onText(action.text)
                else listener?.onText(action.text)
            }
            KeyAction.Spacer -> Unit
            else -> listener?.onKey(action)
        }
    }

    private val longPress = Runnable {
        val key = downKey ?: return@Runnable
        when (key.spec.action) {
            KeyAction.Shift -> {
                longConsumed = true
                listener?.onKey(KeyAction.CapsLock)
            }
            else -> {
                val options = key.spec.longPress
                if (options.isEmpty()) return@Runnable
                longConsumed = true
                val cased = options.map { caseAlternative(it) }
                popup = popupFor(key.rect, cased)
                invalidate()
            }
        }
    }

    private val repeat = object : Runnable {
        override fun run() {
            if (!repeating) return
            listener?.onKey(KeyAction.Backspace)
            handler.postDelayed(this, 42L)
        }
    }

    private fun cancelTouch() {
        handler.removeCallbacks(longPress)
        handler.removeCallbacks(repeat)
        downKey = null
        downSuggestion = null
        downHide = false
        popup = null
        repeating = false
        longConsumed = false
        invalidate()
    }

    private fun popupFor(anchor: RectF, options: List<String>): Popup {
        val optW = dp(if (options.any { it.length > 2 }) 46f else 36f)
        val width = optW * options.size
        val left = (anchor.centerX() - width / 2f).coerceIn(dp(4f), this.width - width - dp(4f))
        val top = (anchor.top - dp(48f)).coerceAtLeast(dp(4f))
        return Popup(options, RectF(left, top, left + width, top + dp(42f)), 0)
    }

    private fun drawPopup(canvas: Canvas, popup: Popup, palette: Palette) {
        fill.color = palette.key
        canvas.drawRoundRect(popup.rect, dp(12f), dp(12f), fill)
        val optW = popup.rect.width() / popup.options.size
        popup.options.forEachIndexed { index, option ->
            val rect = RectF(
                popup.rect.left + index * optW,
                popup.rect.top,
                popup.rect.left + (index + 1) * optW,
                popup.rect.bottom,
            )
            if (index == popup.index) {
                fill.color = palette.accent
                canvas.drawRoundRect(rect.insetBy(dp(3f)), dp(8f), dp(8f), fill)
            }
            drawLabel(canvas, rect, option, if (index == popup.index) palette.onAccent else palette.text, 16f)
        }
    }

    private fun drawPreview(canvas: Canvas, key: DrawnKey, palette: Palette) {
        val action = key.spec.action as? KeyAction.Text ?: return
        if (action.text.length != 1) return
        val shown = if (action.text[0].isLetter()) caseAlternative(action.text) else action.text
        val rect = RectF(
            key.rect.centerX() - dp(22f),
            (key.rect.top - dp(50f)).coerceAtLeast(dp(2f)),
            key.rect.centerX() + dp(22f),
            (key.rect.top - dp(6f)).coerceAtLeast(dp(40f)),
        )
        fill.color = palette.key
        canvas.drawRoundRect(rect, dp(10f), dp(10f), fill)
        drawLabel(canvas, rect, shown, palette.text, 20f)
    }

    private fun layoutKeys(width: Int, height: Int) {
        keys.clear()
        suggestionHits.clear()
        val gap = dp(5f)
        val side = dp(4f)
        val sugH = dp(48f)
        suggestionBar.set(side, dp(4f), width - side, sugH - dp(2f))
        hideRect.set(width - dp(46f), dp(8f), width - side - dp(2f), sugH - dp(8f))
        val rows = Boards.rows(model.board, model.qwertz)
        val keyH = (height - sugH - gap * (rows.size + 1)) / rows.size.coerceAtLeast(1)
        var y = sugH + gap
        for (row in rows) {
            val weight = row.sumOf { it.weight.toDouble() }.toFloat().coerceAtLeast(1f)
            val available = width - side * 2 - gap * (row.size - 1)
            var x = side
            for (spec in row) {
                val w = available * (spec.weight / weight)
                keys.add(DrawnKey(spec, RectF(x, y, x + w, y + keyH)))
                x += w + gap
            }
            y += keyH + gap
        }
        val right = hideRect.left - gap
        val slot = ((right - side) - gap * 2) / 3f
        model.suggestions.take(3).forEachIndexed { index, label ->
            val left = side + index * (slot + gap)
            suggestionHits.add(RectF(left, dp(8f), left + slot, sugH - dp(8f)) to label)
        }
    }

    private fun keyAt(x: Float, y: Float): DrawnKey? {
        return keys.firstOrNull { it.spec.action !is KeyAction.Spacer && it.rect.contains(x, y) }
    }

    private fun displayLabel(spec: KeySpec): String {
        return when (spec.action) {
            KeyAction.Enter -> model.enterLabel
            KeyAction.Language -> model.languageLabel
            KeyAction.Shift -> if (model.shift == ShiftMode.LOCK) "⇪" else "⇧"
            is KeyAction.Text -> if (spec.label.length == 1 && spec.label[0].isLetter() && model.shiftOn) {
                caseAlternative(spec.label)
            } else {
                spec.label
            }
            else -> spec.label
        }
    }

    private fun caseAlternative(value: String): String {
        if (!model.shiftOn || value.isEmpty()) return value
        val chars = value.map { if (it == 'ß') 'ẞ' else if (it.isLetter()) it.uppercaseChar() else it }
        return chars.joinToString("")
    }

    private fun labelSize(label: String): Float = when {
        label.length <= 1 -> 20f
        label.length == 2 -> 15f
        else -> 12f
    }

    private fun drawLabel(canvas: Canvas, rect: RectF, value: String, color: Int, sp: Float) {
        text.color = color
        text.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, resources.displayMetrics)
        val y = rect.centerY() - (text.descent() + text.ascent()) / 2f
        canvas.drawText(value, rect.centerX(), y, text)
    }

    private fun dp(value: Float) = value * resources.displayMetrics.density

    private fun RectF.insetBy(amount: Float) = RectF(left + amount, top + amount, right - amount, bottom - amount)

    private fun palette(): Palette {
        return if (model.dark) {
            Palette(
                board = 0xFF17191E.toInt(),
                key = 0xFF2C313A.toInt(),
                pressed = 0xFF3E4654.toInt(),
                special = 0xFF22262E.toInt(),
                accent = 0xFF0E7C66.toInt(),
                accentPressed = 0xFF14967A.toInt(),
                text = 0xFFF4F1EA.toInt(),
                muted = 0xFFA7A39A.toInt(),
                suggestion = 0xFF22262E.toInt(),
                onAccent = 0xFFF7F4EE.toInt(),
            )
        } else {
            Palette(
                board = 0xFFD9D3C7.toInt(),
                key = 0xFFF7F4EE.toInt(),
                pressed = 0xFFE7E0D4.toInt(),
                special = 0xFFCDC6B8.toInt(),
                accent = 0xFF0E7C66.toInt(),
                accentPressed = 0xFF0B5C4C.toInt(),
                text = 0xFF1C1917.toInt(),
                muted = 0xFF6F6A62.toInt(),
                suggestion = 0xFFEFEAE1.toInt(),
                onAccent = 0xFFF7F4EE.toInt(),
            )
        }
    }

    private class DrawnKey(val spec: KeySpec, val rect: RectF)
    private class Popup(val options: List<String>, val rect: RectF, var index: Int)
    private class Palette(
        val board: Int,
        val key: Int,
        val pressed: Int,
        val special: Int,
        val accent: Int,
        val accentPressed: Int,
        val text: Int,
        val muted: Int,
        val suggestion: Int,
        val onAccent: Int,
    )
}
