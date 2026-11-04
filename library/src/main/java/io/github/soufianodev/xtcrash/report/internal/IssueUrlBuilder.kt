package io.github.soufianodev.xtcrash.report.internal

import java.net.URI

/**
 * Result of building a destination URL: the final encoded URL, the exact byte
 * budget it consumed, and whether the report body had to be shortened.
 */
internal data class BuiltUrl(
    /** The final encoded destination URL. */
    val url: String,
    /** The decoded prefill body [url] carries (empty when none fits). */
    val body: String,
    /** True when the report body was shortened to fit the budget. */
    val shortened: Boolean,
)

/**
 * Builds issue-creation URLs entirely inside XtCrash: title formatting, body
 * formatting, query encoding, and prefill-shortening decisions. Consumers
 * receive the finished [BuiltUrl.url] and must not rebuild or mutate it.
 *
 * <p>The budget is enforced on the complete encoded URL measured in UTF-8
 * bytes, not on the raw report text, so percent-encoding expansion is
 * accounted for.</p>
 */
internal object IssueUrlBuilder {

    /**
     * Default conservative budget for the entire encoded URL, in bytes. Both
     * GitHub and GitLab.com document an 8192-character cap on issue-body
     * prefill via query parameters; 8000 stays under that with headroom for
     * encoding overhead. This is a conservative default, not a claim of a
     * universally verified service limit — self-hosted GitLab deployments vary.
     */
    const val DEFAULT_URL_BUDGET_BYTES = 8000

    /** Lower bound a configured budget may not drop below. */
    const val MIN_URL_BUDGET_BYTES = 512

    /**
     * Builds a GitHub new-issue URL.
     *
     * @param owner repository owner
     * @param repository repository name
     * @param title issue title (prefill)
     * @param blocks prioritized report body blocks
     * @param budgetBytes total encoded-URL byte budget
     */
    fun github(
        owner: String,
        repository: String,
        title: String,
        blocks: List<PrefillShortener.Block>,
        budgetBytes: Int,
    ): BuiltUrl {
        // The owner and repository are inserted into the path unencoded, so
        // unsafe characters (?, #, %, /) would redirect the prefill out of the
        // path; reject them instead of emitting a URL pointing elsewhere.
        if (!isSafePathComponent(owner) || !isSafePathComponent(repository)) {
            return BuiltUrl(url = "", body = "", shortened = false)
        }
        // GitHub prefills the issue form from `title` and `body` query params.
        val base = "https://github.com/$owner/$repository/issues/new"
        return build(base, "title", "body", title, blocks, budgetBytes)
    }

    /** True for a path component that needs no encoding and cannot escape its segment. */
    fun isSafePathComponent(value: String): Boolean = value.isNotEmpty() && value.all { c ->
        c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c == '-' || c == '.' || c == '_'
    }

    /**
     * Builds a GitLab new-issue URL for an explicit base URL (gitlab.com or a
     * verified self-hosted instance).
     *
     * @param baseUrl instance base URL, e.g. `https://gitlab.com`
     * @param projectPath namespace-qualified project path, e.g. `group/project`
     * @param title issue title (prefill)
     * @param blocks prioritized report body blocks
     * @param budgetBytes total encoded-URL byte budget
     */
    fun gitlab(
        baseUrl: String,
        projectPath: String,
        title: String,
        blocks: List<PrefillShortener.Block>,
        budgetBytes: Int,
    ): BuiltUrl {
        val trimmedBase = baseUrl.trimEnd('/')
        val encodedPath = projectPath.trim('/').split('/')
            .joinToString("/") { encodeSegment(it) }
        // GitLab prefills from `issue[title]` / `issue[description]` (encoded).
        val base = "$trimmedBase/$encodedPath/-/issues/new"
        return build(base, "issue%5Btitle%5D", "issue%5Bdescription%5D", title, blocks, budgetBytes)
    }

    /**
     * Builds the destination URL. The description query-parameter name differs
     * per service (`body` on GitHub, `issue[description]` on GitLab).
     */
    private fun build(
        base: String,
        titleParam: String,
        descriptionParam: String,
        title: String,
        blocks: List<PrefillShortener.Block>,
        budgetBytes: Int,
    ): BuiltUrl {
        val fixed = base + "?" + titleParam + "=" + encodeValue(title) + "&" + descriptionParam + "="
        val fixedBytes = PrefillShortener.utf8Bytes(fixed)

        if (fixedBytes > budgetBytes) {
            // The title alone exhausts the budget. Trimming the trailing
            // separator cannot help, because the title stays in the URL, so
            // offer only the bare issue page when it fits on its own; the
            // caller treats an empty url as "no usable URL" and offers Copy
            // instead of opening a malformed target.
            val bare = base.takeIf { PrefillShortener.utf8Bytes(it) <= budgetBytes } ?: ""
            return BuiltUrl(bare, body = "", shortened = true)
        }

        // Cost per block is its percent-encoded size, so the final encoded URL
        // (fixed prefix + encoded body) is guaranteed to stay within budget.
        val bodyBudget = budgetBytes - fixedBytes
        val shortened = PrefillShortener.shorten(blocks, bodyBudget, cost = { encodeValue(it).length })
        val url = fixed + encodeValue(shortened.body)
        return BuiltUrl(url, body = shortened.body, shortened = shortened.omittedContent)
    }

    /** Percent-encodes a query-parameter value (RFC 3986, space as %20). */
    fun encodeValue(value: String): String {
        val sb = StringBuilder()
        for (byte in value.toByteArray(Charsets.UTF_8)) {
            val b = byte.toInt() and 0xFF
            val c = b.toChar()
            val unreserved = (c in 'a'..'z') || (c in 'A'..'Z') || (c in '0'..'9')
                    || c == '-' || c == '.' || c == '_' || c == '~'
            if (unreserved) {
                sb.append(c)
            } else {
                sb.append('%')
                sb.append(HEX[b ushr 4])
                sb.append(HEX[b and 0x0F])
            }
        }
        return sb.toString()
    }

    private fun encodeSegment(segment: String): String = encodeValue(segment)

    private const val HEX = "0123456789ABCDEF"

    /**
     * Clamps a configured budget into the supported range, never letting
     * configuration bypass the known floor.
     */
    fun clampBudget(configured: Int?): Int {
        val value = configured ?: DEFAULT_URL_BUDGET_BYTES
        return value.coerceIn(MIN_URL_BUDGET_BYTES, Int.MAX_VALUE)
    }

    /** Exposed for validation of a configured base URL. */
    fun isSupportedBaseUrl(baseUrl: String): Boolean {
        return try {
            val uri = URI(baseUrl.trimEnd('/'))
            val scheme = uri.scheme?.lowercase()
            val host = uri.host
            val path = uri.path
            val pathOk = path.isNullOrEmpty() || path == "/"
            // A query or fragment would swallow the appended issue path
            // (e.g. https://host?x=1 + /p/-/issues/new), so bases carrying
            // one are rejected outright.
            (scheme == "https" || scheme == "http") && !host.isNullOrEmpty() && pathOk
                    && uri.userInfo == null && uri.query == null && uri.fragment == null
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Returns true when a destination uses plaintext HTTP. Callers must warn
     * before navigation even when the final issue URL is bare and carries no
     * non-empty prefilled report fields.
     */
    fun usesUnencryptedHttp(url: String): Boolean {
        return try {
            val uri = URI(url)
            uri.scheme.equals("http", ignoreCase = true) && !uri.host.isNullOrEmpty()
                    && uri.userInfo == null && uri.fragment == null
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Structural check on a finished URL before it becomes the report's
     * openable target: a parseable http(s) URL with a host, no user-info, and
     * no fragment, so malformed targets degrade to Copy-only fallback.
     */
    fun isValidIssueUrl(url: String): Boolean {
        return try {
            val uri = URI(url)
            val scheme = uri.scheme?.lowercase()
            (scheme == "https" || scheme == "http") && !uri.host.isNullOrEmpty()
                    && uri.userInfo == null && uri.fragment == null
        } catch (e: Exception) {
            false
        }
    }
}
