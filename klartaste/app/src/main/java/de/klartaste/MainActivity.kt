package de.klartaste

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        findViewById<MaterialButton>(R.id.enable_button).setOnClickListener {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }
        findViewById<MaterialButton>(R.id.pick_button).setOnClickListener {
            val imm = getSystemService(InputMethodManager::class.java)
            imm.showInputMethodPicker()
        }
        findViewById<MaterialButton>(R.id.settings_button).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        val imm = getSystemService(InputMethodManager::class.java)
        val enabled = imm.enabledInputMethodList.any { it.packageName == packageName }
        val current = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD).orEmpty()
        val selected = current.startsWith("$packageName/")
        val status = findViewById<TextView>(R.id.status_text)
        val enable = findViewById<MaterialButton>(R.id.enable_button)
        status.text = when {
            selected -> getString(R.string.status_selected)
            enabled -> getString(R.string.status_enabled)
            else -> getString(R.string.status_off)
        }
        enable.isEnabled = !enabled
        enable.text = getString(if (enabled) R.string.enabled else R.string.enable)
    }
}
