package com.meetmate.app.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/** A link found in free text: [raw] is exactly what was typed, [url] is the cleaned-up link. */
data class FoundLink(val raw: String, val url: String)

object Links {
    // Keyboards autocorrect "meet.google.com" into "meet. Google. Com" and speech gives "meet dot google dot com",
    // so the separators between the parts of well-known meeting hosts are allowed to be sloppy.
    private const val DOT = """\s*(?:\.|\bdot\b)\s*"""
    private const val PATH = """\s*/\s*[\w\-/?=&#%.]+"""
    private val sloppyHosts = listOf(
        "meet${DOT}google${DOT}com$PATH",
        "(?:[\\w-]+$DOT)?zoom${DOT}us$PATH",
        "teams${DOT}microsoft${DOT}com$PATH",
        "(?:[\\w-]+$DOT)?webex${DOT}com$PATH"
    )
    private val urlPattern = Regex(
        (listOf("""(?:https?://|www\.)\S+""") + sloppyHosts).joinToString("|") { "(?:$it)" },
        RegexOption.IGNORE_CASE
    )

    /** First meeting link in [text], tolerant of autocorrected or spoken dots. */
    fun find(text: String): FoundLink? {
        val match = urlPattern.find(text) ?: return null
        val raw = match.value.trimEnd('.', ',', ')')
        val url = raw
            .replace(Regex("""\s*\bdot\b\s*""", RegexOption.IGNORE_CASE), ".")
            .replace(Regex("""\s+"""), "")
            .let { cleaned ->
                // Lower-case the host part (autocorrect capitalises it) but keep the path as typed.
                val slash = cleaned.indexOf('/', cleaned.indexOf("://").let { if (it < 0) 0 else it + 3 })
                if (slash < 0) cleaned.lowercase() else cleaned.substring(0, slash).lowercase() + cleaned.substring(slash)
            }
        return FoundLink(raw, url)
    }

    fun normalize(link: String): String =
        link.trim().let { if (it.startsWith("http", ignoreCase = true)) it else "https://$it" }

    fun open(context: Context, link: String) {
        if (link.isBlank()) return
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(normalize(link))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "No app can open this link", Toast.LENGTH_SHORT).show()
        }
    }
}
