package de.klartaste

import de.klartaste.engine.ChatJson
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object AiClient {
    data class Outcome(val text: String?, val error: String?)

    fun correct(baseUrl: String, model: String, apiKey: String, text: String): Outcome {
        val root = baseUrl.trim().trimEnd('/')
        if (root.isBlank() || model.isBlank()) return Outcome(null, "Adresse oder Modell fehlt.")
        val endpoint = if (root.endsWith("/chat/completions")) root else "$root/chat/completions"
        val payload = JSONObject()
            .put("model", model)
            .put("temperature", 0)
            .put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", SYSTEM))
                    .put(JSONObject().put("role", "user").put("content", text)),
            )
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 6_000
            readTimeout = 12_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            if (apiKey.isNotBlank()) setRequestProperty("Authorization", "Bearer $apiKey")
        }
        return try {
            connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                Outcome(null, messageFor(code, body))
            } else {
                val content = ChatJson.assistantContent(body)?.let(ChatJson::clean)
                if (content.isNullOrBlank()) Outcome(null, "Die KI hat keine Korrektur zurückgegeben.")
                else Outcome(content, null)
            }
        } catch (error: Exception) {
            Outcome(null, "KI nicht erreichbar.")
        } finally {
            connection.disconnect()
        }
    }

    private fun messageFor(code: Int, body: String): String = when (code) {
        401, 403 -> "Schlüssel wurde abgelehnt."
        404 -> "Modell oder Adresse wurde nicht gefunden."
        429 -> "Zu viele Anfragen. Kurz warten."
        else -> {
            val detail = runCatching {
                JSONObject(body.ifBlank { "{}" }).optJSONObject("error")?.optString("message").orEmpty()
            }.getOrDefault("")
            if (detail.isNotBlank()) "KI-Fehler: ${detail.take(140)}" else "KI-Fehler ($code)."
        }
    }

    private const val SYSTEM =
        "Du korrigierst Texte, die auf einem Handy getippt wurden. " +
            "Korrigiere Rechtschreibung, Grammatik, Zeichensetzung und Großschreibung. " +
            "Behalte Sprache, Bedeutung, Ton und Emojis bei. " +
            "Antworte nur mit dem korrigierten Text, ohne Anführungszeichen und ohne Erklärung. " +
            "Wenn nichts zu korrigieren ist, gib den Text unverändert zurück."
}
