/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.lang.psi

import com.intellij.openapi.util.TextRange

/**
 * Stores offsets of distinguishable parts of a literal.
 */
data class LiteralOffsets(
    val prefix: TextRange? = null,
    val openDelim: TextRange? = null,
    val value: TextRange? = null,
    val closeDelim: TextRange? = null,
    val suffix: TextRange? = null
) {
    companion object {
        fun fromEndOffsets(
            prefixEnd: Int, openDelimEnd: Int, valueEnd: Int,
            closeDelimEnd: Int, suffixEnd: Int
        ): LiteralOffsets {
            val prefix = makeRange(0, prefixEnd)
            val openDelim = makeRange(prefixEnd, openDelimEnd)

            val value = makeRange(openDelimEnd, valueEnd) ?:
                // empty value is still a value provided we have open delimiter
                if (openDelim != null) TextRange.create(openDelimEnd, openDelimEnd) else null

            val closeDelim = makeRange(valueEnd, closeDelimEnd)
            val suffix = makeRange(closeDelimEnd, suffixEnd)

            return LiteralOffsets(
                prefix = prefix, openDelim = openDelim, value = value,
                closeDelim = closeDelim, suffix = suffix
            )
        }

        private fun makeRange(start: Int, end: Int): TextRange? = when {
            end - start > 0 -> TextRange(start, end)
            else -> null
        }
    }
}
