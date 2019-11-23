/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.lang.lexer

import com.intellij.lang.lexer.LexerBaseEx
import com.intellij.lang.lexer.esc
import com.intellij.lang.lexer.isWhitespaceChar
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.StringEscapesTokenTypes
import com.intellij.psi.tree.IElementType
import com.intellij.util.text.CharArrayUtil
import org.toml.lang.psi.TOML_BASIC_STRINGS
import org.toml.lang.psi.TomlElementTypes.BASIC_STRING
import org.toml.lang.psi.TomlElementTypes.MULTILINE_BASIC_STRING

class TomlEscapeLexer private constructor(
    private val defaultToken: IElementType,
    private val eol: Boolean
) : LexerBaseEx() {

    override fun determineTokenType(): IElementType? {
        // We're at the end of the string token => finish lexing
        if (tokenStart >= tokenEnd) {
            return null
        }

        // We're not inside escape sequence
        if (bufferSequence[tokenStart] != '\\') {
            return defaultToken
        }

        // \ is at the end of the string token
        if (tokenStart + 1 >= tokenEnd) {
            return StringEscapesTokenTypes.INVALID_CHARACTER_ESCAPE_TOKEN
        }

        return when (bufferSequence[tokenStart + 1]) {
            'u', 'U' -> when {
                isValidUnicodeEscape(tokenStart, tokenEnd) -> StringEscapesTokenTypes.VALID_STRING_ESCAPE_TOKEN
                else -> StringEscapesTokenTypes.INVALID_UNICODE_ESCAPE_TOKEN
            }
            '\r', '\n' -> esc(eol)
            'b', 't', 'n', 'f', 'r', '"', '\\' -> StringEscapesTokenTypes.VALID_STRING_ESCAPE_TOKEN
            else -> StringEscapesTokenTypes.INVALID_CHARACTER_ESCAPE_TOKEN
        }
    }

    private fun isValidUnicodeEscape(start: Int, end: Int): Boolean {
        return try {
            val value = bufferSequence.substring(start + 2, end).toInt(16)
            // https://github.com/toml-lang/toml/blame/92a0a60bf37093fe0a888e5c98445e707ac9375b/README.md#L296-L297
            // http://unicode.org/glossary/#unicode_scalar_value
            value in 0..0xD7FF || value in 0xE000..0x10FFFF
        } catch (e: NumberFormatException) {
            false
        }
    }

    override fun locateToken(start: Int): Int {
        if (start >= bufferEnd) {
            return start
        }

        if (bufferSequence[start] == '\\') {
            val i = start + 1

            if (i >= bufferEnd) {
                return bufferEnd
            }

            return when (bufferSequence[i]) {
                'u' -> unicodeTokenEnd(i + 1, 4)
                'U' -> unicodeTokenEnd(i + 1, 8)
                '\r', '\n' -> {
                    var j = i
                    while (j < bufferEnd && bufferSequence[j].isWhitespaceChar()) {
                        j++
                    }
                    j
                }
                else -> i + 1
            }
        } else {
            val idx = CharArrayUtil.indexOf(bufferSequence, "\\", start + 1, bufferEnd)
            return if (idx != -1) idx else bufferEnd
        }
    }

    private fun unicodeTokenEnd(start: Int, expectedLength: Int): Int {
        for (i in start until start + expectedLength) {
            if (i >= bufferEnd || !StringUtil.isHexDigit(bufferSequence[i])) {
                return start
            }
        }
        return start + expectedLength
    }

    companion object {
        fun of(tokenType: IElementType): TomlEscapeLexer {
            return when (tokenType) {
                BASIC_STRING -> TomlEscapeLexer(BASIC_STRING, eol = false)
                MULTILINE_BASIC_STRING -> TomlEscapeLexer(MULTILINE_BASIC_STRING, eol = true)
                else -> throw IllegalArgumentException("Unsupported literal type: $tokenType")
            }
        }

        /**
         * Set of possible arguments for [of]
         */
        val ESCAPABLE_LITERALS_TOKEN_SET = TOML_BASIC_STRINGS
    }
}
