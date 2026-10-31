package com.meetmate.app.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

object Links {
    private val urlPattern = Regex("""(?:https?://|www\.)\S+|\b(?:[\w-]+\.)?(?:zoom\.us|webex\.com)/\S+|\bmeet\.google\.com/\S+|\bteams\.microsoft\.com/\S+""", RegexOption.IGNORE_CASE)

    /** First URL-looking token in [text], e.g. a Meet or Zoom link pasted into a sentence. */
    fun find(text: String): String? = urlPattern.find(text)?.value?.trimEnd('.', ',', ')')

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
