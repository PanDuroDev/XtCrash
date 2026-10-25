package io.github.soufianodev.xtcrash.report.internal

/**
 * Deterministic, priority-aware shortening of the canonical report into a
 * destination URL prefill that fits an encoded byte budget.
 *
 * <p>The canonical report is never mutated: shortening builds a separate
 * prefill string. Content is supplied as an ordered list of blocks, highest
 * priority first. Blocks are kept whole while they fit; the first block that
 * does not fit is truncated with a visible marker, and every lower-priority
 * block is dropped.</p>
 *
 * <p>The budget is measured with [cost], the size the block contributes to the
 * final encoded URL. [IssueUrlBuilder] passes an encoded-length cost so
 * percent-encoding expansion is accounted for exactly; the default cost is the
 * raw UTF-8 length for tests and non-URL use. Because the encoder is byte-wise,
 * per-block costs sum to the whole-body encoded cost, so the result is
 * guaranteed to fit when concatenated.</p>
 *
 * <p>When developer-supplied information alone exceeds the budget it is
 * truncated with the marker rather than silently deleted, and the result flags
 * that manual pasting is required; the full text stays in the canonical
 * report.</p>
 */
internal object PrefillShortener {

    /** ASCII marker inserted where content was removed (cheap to percent-encode). */
    const val TRUNCATION_MARKER = "\n[...truncated by XtCrash, paste the full report...]\n"

    /** One prioritized piece of the report. */
    data class Block(val text: String)

    /** Result of building a prefill within a budget. */
    data class Result(
        /** The shortened prefill body. */
        val body: String,
        /** True when some canonical content was dropped from the prefill. */
        val omittedContent: Boolean,
    )

    /**
     * Shortens the given [blocks] (highest priority first) so the result costs
     * at most [budgetBytes] under [cost].
     *
     * @param cost what one block contributes to the final encoded size
     */
    fun shorten(
        blocks: List<Block>,
        budgetBytes: Int,
        cost: (String) -> Int = { utf8Bytes(it) },
    ): Result {
        if (budgetBytes <= 0) {
            return Result("", omittedContent = blocks.any { it.text.isNotEmpty() })
        }

        val body = StringBuilder()
        var omitted = false

        for (block in blocks) {
            if (block.text.isEmpty()) continue
            val remaining = budgetBytes - cost(body.toString())
            if (remaining <= 0) {
                omitted = true
                break
            }
            if (cost(block.text) <= remaining) {
                body.append(block.text)
            } else {
                // Truncate here: keep as much of this block as fits alongside
                // the marker so the user can see that content was removed.
                val markerCost = cost(TRUNCATION_MARKER)
                if (markerCost <= remaining) {
                    body.append(takeHeadBytes(block.text, remaining - markerCost, cost))
                    body.append(TRUNCATION_MARKER)
                } else {
                    // Not even the marker fits: keep a bare head so the prefix
                    // stays readable, still within budget.
                    body.append(takeHeadBytes(block.text, remaining, cost))
                }
                omitted = true
                break
            }
        }

        return Result(body.toString(), omitted)
    }

    /**
     * Returns the longest prefix of [text] that costs at most [budgetBytes]
     * under [cost]. Never splits a surrogate pair by falling back to the
     * previous position when a lone high surrogate would result.
     */
    fun takeHeadBytes(text: String, budgetBytes: Int, cost: (String) -> Int = { utf8Bytes(it) }): String {
        if (budgetBytes <= 0) return ""
        if (cost(text) <= budgetBytes) return text
        var low = 0
        var high = text.length
        while (low < high) {
            val mid = (low + high + 1) ushr 1
            if (cost(text.substring(0, mid)) <= budgetBytes) {
                low = mid
            } else {
                high = mid - 1
            }
        }
        var end = low
        if (end in 1 until text.length && Character.isHighSurrogate(text[end - 1])
                && Character.isLowSurrogate(text[end])) {
            end -= 1
        }
        return text.substring(0, end)
    }

    /** Raw UTF-8 byte length of [text] (not encoded length). */
    fun utf8Bytes(text: String): Int = text.toByteArray(Charsets.UTF_8).size
}
