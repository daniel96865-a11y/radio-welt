package de.klartaste

import android.content.Context
import de.klartaste.engine.CorrectionEngine
import de.klartaste.engine.LanguageMode

class Prefs(context: Context) {
    private val app = context.applicationContext
    private val store = app.getSharedPreferences("klartaste", Context.MODE_PRIVATE)

    var autocorrect: Boolean
        get() = store.getBoolean(KEY_AUTOCORRECT, true)
        set(value) = store.edit().putBoolean(KEY_AUTOCORRECT, value).apply()

    var suggestions: Boolean
        get() = store.getBoolean(KEY_SUGGESTIONS, true)
        set(value) = store.edit().putBoolean(KEY_SUGGESTIONS, value).apply()

    var autoCap: Boolean
        get() = store.getBoolean(KEY_AUTOCAP, true)
        set(value) = store.edit().putBoolean(KEY_AUTOCAP, value).apply()

    var doubleSpace: Boolean
        get() = store.getBoolean(KEY_DOUBLE_SPACE, true)
        set(value) = store.edit().putBoolean(KEY_DOUBLE_SPACE, value).apply()

    var haptic: Boolean
        get() = store.getBoolean(KEY_HAPTIC, true)
        set(value) = store.edit().putBoolean(KEY_HAPTIC, value).apply()

    var sound: Boolean
        get() = store.getBoolean(KEY_SOUND, false)
        set(value) = store.edit().putBoolean(KEY_SOUND, value).apply()

    var qwertz: Boolean
        get() = store.getBoolean(KEY_QWERTZ, true)
        set(value) = store.edit().putBoolean(KEY_QWERTZ, value).apply()

    var darkKeys: Boolean
        get() = store.getBoolean(KEY_DARK, true)
        set(value) = store.edit().putBoolean(KEY_DARK, value).apply()

    var aiEnabled: Boolean
        get() = store.getBoolean(KEY_AI, true)
        set(value) = store.edit().putBoolean(KEY_AI, value).apply()

    var aiBase: String
        get() = store.getString(KEY_BASE, DEFAULT_BASE) ?: DEFAULT_BASE
        set(value) = store.edit().putString(KEY_BASE, value.trim()).apply()

    var aiModel: String
        get() = store.getString(KEY_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL
        set(value) = store.edit().putString(KEY_MODEL, value.trim()).apply()

    var aiKey: String
        get() = store.getString(KEY_TOKEN, "") ?: ""
        set(value) = store.edit().putString(KEY_TOKEN, value.trim()).apply()

    var lastAiError: String
        get() = store.getString(KEY_ERROR, "") ?: ""
        set(value) = store.edit().putString(KEY_ERROR, value).apply()

    var language: LanguageMode
        get() = when (store.getString(KEY_LANG, "both")) {
            "de" -> LanguageMode.GERMAN
            "en" -> LanguageMode.ENGLISH
            else -> LanguageMode.BOTH
        }
        set(value) {
            val code = when (value) {
                LanguageMode.GERMAN -> "de"
                LanguageMode.ENGLISH -> "en"
                LanguageMode.BOTH -> "both"
            }
            store.edit().putString(KEY_LANG, code).apply()
        }

    fun languageLabel(): String = when (language) {
        LanguageMode.GERMAN -> "DE"
        LanguageMode.ENGLISH -> "EN"
        LanguageMode.BOTH -> "DE+"
    }

    fun aiReady(): Boolean {
        if (!aiEnabled) return false
        if (aiBase.isBlank() || aiModel.isBlank()) return false
        if (aiKey.isNotBlank()) return true
        val base = aiBase.lowercase()
        return base.startsWith("http://") || base.contains("localhost") || base.contains("127.0.0.1")
    }

    fun newEngine(): CorrectionEngine {
        fun read(name: String) = app.assets.open(name).bufferedReader().use { it.readText() }
        return CorrectionEngine.load(
            germanText = read("lexicon_de.txt"),
            englishText = read("lexicon_en.txt"),
            aliasText = read("aliases.txt"),
            userWords = userWords(),
        )
    }

    fun saveLearned(engine: CorrectionEngine) {
        val packed = engine.learned().entries.joinToString("\n") { "${it.key}\t${it.value}" }
        store.edit().putString(KEY_USER, packed).apply()
    }

    fun clearLearned() {
        store.edit().remove(KEY_USER).apply()
    }

    private fun userWords(): Map<String, String> {
        val raw = store.getString(KEY_USER, "") ?: return emptyMap()
        val out = LinkedHashMap<String, String>()
        for (line in raw.lineSequence()) {
            val tab = line.indexOf('\t')
            if (tab <= 0) continue
            out[line.substring(0, tab)] = line.substring(tab + 1)
        }
        return out
    }

    companion object {
        const val DEFAULT_BASE = "https://api.openai.com/v1"
        const val DEFAULT_MODEL = "gpt-4o-mini"

        private const val KEY_AUTOCORRECT = "autocorrect"
        private const val KEY_SUGGESTIONS = "suggestions"
        private const val KEY_AUTOCAP = "autocap"
        private const val KEY_DOUBLE_SPACE = "double_space"
        private const val KEY_HAPTIC = "haptic"
        private const val KEY_SOUND = "sound"
        private const val KEY_QWERTZ = "qwertz"
        private const val KEY_DARK = "dark"
        private const val KEY_AI = "ai"
        private const val KEY_BASE = "ai_base"
        private const val KEY_MODEL = "ai_model"
        private const val KEY_TOKEN = "ai_key"
        private const val KEY_ERROR = "ai_error"
        private const val KEY_LANG = "language"
        private const val KEY_USER = "user_words"
    }
}
