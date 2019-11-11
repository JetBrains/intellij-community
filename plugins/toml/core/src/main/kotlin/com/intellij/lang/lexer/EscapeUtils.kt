/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package com.intellij.lang.lexer

import com.intellij.lexer.Lexer
import com.intellij.psi.StringEscapesTokenTypes.*
import com.intellij.psi.tree.IElementType

fun esc(test: Boolean): IElementType = if (test) VALID_STRING_ESCAPE_TOKEN else INVALID_CHARACTER_ESCAPE_TOKEN

/**
 * Determines if the char is a whitespace character.
 */
fun Char.isWhitespaceChar(): Boolean = equals(' ') || equals('\r') || equals('\n') || equals('\t')

/**
 * Mimics [com.intellij.codeInsight.CodeInsightUtilCore.parseStringCharacters]
 * but obeys specific escaping rules provided by [decoder].
 */
inline fun parseStringCharacters(
    lexer: Lexer,
    chars: String,
    outChars: StringBuilder,
    sourceOffsets: IntArray,
    decoder: (String) -> String
): Boolean {
    val outOffset = outChars.length
    var index = 0
    for ((type, text) in chars.tokenize(lexer)) {
        // Set offset for the decoded character to the beginning of the escape sequence.
        sourceOffsets[outChars.length - outOffset] = index
        sourceOffsets[outChars.length - outOffset + 1] = index + 1
        when (type) {
            VALID_STRING_ESCAPE_TOKEN -> {
                outChars.append(decoder(text))
                // And perform a "jump"
                index += text.length
            }

            INVALID_CHARACTER_ESCAPE_TOKEN,
            INVALID_UNICODE_ESCAPE_TOKEN ->
                return false

            else -> {
                val first = outChars.length - outOffset
                outChars.append(text)
                val last = outChars.length - outOffset - 1
                // Set offsets for each character of given chunk
                for (i in first..last) {
                    sourceOffsets[i] = index
                    index++
                }
            }
        }
    }

    sourceOffsets[outChars.length - outOffset] = index

    return true
}
