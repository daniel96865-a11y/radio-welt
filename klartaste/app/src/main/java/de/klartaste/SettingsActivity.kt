package de.klartaste

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import de.klartaste.engine.LanguageMode
import kotlin.concurrent.thread

class SettingsActivity : AppCompatActivity() {
    private lateinit var prefs: Prefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        prefs = Prefs(this)
        findViewById<MaterialButton>(R.id.back_button).setOnClickListener { finish() }

        bindSwitch(R.id.switch_autocorrect, prefs.autocorrect) { prefs.autocorrect = it }
        bindSwitch(R.id.switch_suggestions, prefs.suggestions) { prefs.suggestions = it }
        bindSwitch(R.id.switch_cap, prefs.autoCap) { prefs.autoCap = it }
        bindSwitch(R.id.switch_double, prefs.doubleSpace) { prefs.doubleSpace = it }
        bindSwitch(R.id.switch_haptic, prefs.haptic) { prefs.haptic = it }
        bindSwitch(R.id.switch_sound, prefs.sound) { prefs.sound = it }
        bindSwitch(R.id.switch_ai, prefs.aiEnabled) { prefs.aiEnabled = it }

        val language = findViewById<RadioGroup>(R.id.language_group)
        language.check(
            when (prefs.language) {
                LanguageMode.GERMAN -> R.id.lang_de
                LanguageMode.ENGLISH -> R.id.lang_en
                LanguageMode.BOTH -> R.id.lang_both
            },
        )
        language.setOnCheckedChangeListener { _, id ->
            prefs.language = when (id) {
                R.id.lang_de -> LanguageMode.GERMAN
                R.id.lang_en -> LanguageMode.ENGLISH
                else -> LanguageMode.BOTH
            }
        }

        val layout = findViewById<RadioGroup>(R.id.layout_group)
        layout.check(if (prefs.qwertz) R.id.layout_de else R.id.layout_en)
        layout.setOnCheckedChangeListener { _, id -> prefs.qwertz = id == R.id.layout_de }

        val theme = findViewById<RadioGroup>(R.id.theme_group)
        theme.check(if (prefs.darkKeys) R.id.theme_dark else R.id.theme_light)
        theme.setOnCheckedChangeListener { _, id -> prefs.darkKeys = id == R.id.theme_dark }

        val base = findViewById<TextInputEditText>(R.id.base_url)
        val model = findViewById<TextInputEditText>(R.id.model_name)
        val key = findViewById<TextInputEditText>(R.id.api_key)
        base.setText(prefs.aiBase)
        model.setText(prefs.aiModel)
        key.setText(prefs.aiKey)
        base.watch { prefs.aiBase = it }
        model.watch { prefs.aiModel = it }
        key.watch { prefs.aiKey = it }

        findViewById<MaterialButton>(R.id.preset_openai).setOnClickListener {
            applyPreset("https://api.openai.com/v1", "gpt-4o-mini")
        }
        findViewById<MaterialButton>(R.id.preset_groq).setOnClickListener {
            applyPreset("https://api.groq.com/openai/v1", "llama-3.3-70b-versatile")
        }
        findViewById<MaterialButton>(R.id.preset_ollama).setOnClickListener {
            applyPreset("http://127.0.0.1:11434/v1", "llama3.2")
        }

        val result = findViewById<TextView>(R.id.ai_result)
        if (prefs.lastAiError.isNotBlank()) result.text = prefs.lastAiError
        findViewById<MaterialButton>(R.id.test_ai).setOnClickListener {
            val sample = "das ist ein satz mit feher und das er korrigiert werden muss"
            result.text = getString(R.string.ai_testing)
            val baseUrl = prefs.aiBase
            val modelName = prefs.aiModel
            val apiKey = prefs.aiKey
            thread {
                val outcome = AiClient.correct(baseUrl, modelName, apiKey, sample)
                runOnUiThread {
                    if (outcome.text != null) {
                        prefs.lastAiError = ""
                        result.text = outcome.text
                    } else {
                        val message = outcome.error ?: getString(R.string.ai_missing)
                        prefs.lastAiError = message
                        result.text = message
                    }
                }
            }
        }

        findViewById<MaterialButton>(R.id.clear_words).setOnClickListener {
            prefs.clearLearned()
            Toast.makeText(this, R.string.words_cleared, Toast.LENGTH_SHORT).show()
        }
    }

    private fun applyPreset(baseUrl: String, modelName: String) {
        findViewById<TextInputEditText>(R.id.base_url).setText(baseUrl)
        findViewById<TextInputEditText>(R.id.model_name).setText(modelName)
        prefs.aiBase = baseUrl
        prefs.aiModel = modelName
    }

    private fun bindSwitch(id: Int, initial: Boolean, onChange: (Boolean) -> Unit) {
        val view = findViewById<MaterialSwitch>(id)
        view.isChecked = initial
        view.setOnCheckedChangeListener { _, checked -> onChange(checked) }
    }

    private fun TextInputEditText.watch(onChange: (String) -> Unit) {
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                onChange(s?.toString().orEmpty())
            }
        })
    }
}
