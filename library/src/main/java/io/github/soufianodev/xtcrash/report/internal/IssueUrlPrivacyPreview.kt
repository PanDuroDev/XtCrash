package io.github.soufianodev.xtcrash.report.internal

import io.github.soufianodev.xtcrash.report.ReportDestination
import java.net.URI
import java.net.URLDecoder

/**
 * The destination and prefilled fields that a privacy confirmation can safely
 * preview from the final URL. Query values are decoded for display, while the
 * endpoint path remains percent-encoded so the preview identifies the actual
 * destination without exposing the query string.
 */
internal data class IssueUrlPrivacyPreview(
    val destinationUrl: String,
    val title: String?,
    val description: String?,
)

/** Parses only the prepared URL; callers must not reconstruct it from config. */
internal object IssueUrlPrivacyPreviewBuilder {

    fun build(url: String, destination: ReportDestination): IssueUrlPrivacyPreview? {
        if (url.isBlank() || destination !in WEB_DESTINATIONS) return null

        val uri = try {
            URI(url)
        } catch (_: Exception) {
            return null
        }
        val scheme = uri.scheme?.takeIf { it.isNotBlank() } ?: return null
        val host = uri.host?.takeIf { it.isNotBlank() } ?: return null
        val path = uri.rawPath?.takeIf { it.isNotEmpty() } ?: "/"
        val displayHost = buildString {
            append(host)
            if (uri.port != -1) append(":").append(uri.port)
        }

        val parameters = try {
            parseQuery(uri.rawQuery)
        } catch (_: IllegalArgumentException) {
            return null
        }
        val titleKey: String
        val descriptionKey: String
        when (destination) {
            ReportDestination.GITHUB -> {
                titleKey = "title"
                descriptionKey = "body"
            }
            ReportDestination.GITLAB -> {
                titleKey = "issue[title]"
                descriptionKey = "issue[description]"
            }
            else -> return null
        }

        return IssueUrlPrivacyPreview(
            destinationUrl = "$scheme://$displayHost$path",
            title = parameters[titleKey],
            description = parameters[descriptionKey],
        )
    }

    private fun parseQuery(rawQuery: String?): Map<String, String> {
        if (rawQuery.isNullOrEmpty()) return emptyMap()
        val parameters = linkedMapOf<String, String>()
        rawQuery.split('&').forEach { pair ->
            if (pair.isEmpty()) return@forEach
            val separator = pair.indexOf('=')
            val rawKey = if (separator < 0) pair else pair.substring(0, separator)
            val rawValue = if (separator < 0) "" else pair.substring(separator + 1)
            val key = URLDecoder.decode(rawKey, "UTF-8")
            val value = URLDecoder.decode(rawValue, "UTF-8")
            // Match getQueryParameter semantics: the first occurrence wins.
            if (!parameters.containsKey(key)) parameters[key] = value
        }
        return parameters
    }

    private val WEB_DESTINATIONS = setOf(
        ReportDestination.GITHUB,
        ReportDestination.GITLAB,
    )
}

/** Keeps required manual-paste guidance ahead of URL and field details. */
internal object IssueUrlPrivacyConfirmationComposer {

    /**
     * Keeps the URL and field preview visible when an app overrides the legacy
     * privacy-message resource without including its second format placeholder.
     * Formatter ignores unused arguments, so passing a preview does not prove
     * that the customized resource rendered it.
     */
    fun ensurePreviewVisible(formattedMessage: String, preview: String): String {
        if (preview.isBlank() || formattedMessage.contains(preview)) {
            return formattedMessage
        }
        val message = formattedMessage.trimEnd()
        return if (message.isEmpty()) preview else "$message\n\n$preview"
    }

    fun compose(manualPasteNote: String?, destinationMessage: String): String {
        val note = manualPasteNote?.takeIf { it.isNotBlank() } ?: return destinationMessage
        return "$note\n\n$destinationMessage"
    }
}
