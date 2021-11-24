/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.lang.lexer

import com.intellij.psi.tree.IElementType
import org.toml.lang.psi.TOML_BASIC_STRINGS
import org.toml.lang.psi.TOML_MULTILINE_STRINGS
import org.toml.lang.psi.TOML_STRING_LITERALS

fun String.unescapeToml(tokenType: IElementType): String {
    require(tokenType in TOML_STRING_LITERALS)
    val unNewlined = when (tokenType) {
        in TOML_MULTILINE_STRINGS -> this.removePrefix("\n")
        else -> this
    }
    return if (tokenType in TOML_BASIC_STRINGS) {
        val outChars = StringBuilder()
        val result = parseTomlStringCharacters(tokenType, unNewlined, outChars).second
        if (result) outChars.toString() else unNewlined
    } else {
        unNewlined
    }
}

fun parseTomlStringCharacters(
    tokenType: IElementType,
    chars: String,
    outChars: StringBuilder
): Pair<IntArray, Boolean> {
    require(tokenType in TOML_BASIC_STRINGS)
    val sourceOffsets = IntArray(chars.length + 1)
    val lexer = TomlEscapeLexer.of(tokenType)
    val result = parseTomlStringCharacters(lexer, chars, outChars, sourceOffsets)
    return sourceOffsets to result
}

private fun parseTomlStringCharacters(
    lexer: TomlEscapeLexer,
    chars: String,
    outChars: StringBuilder,
    sourceOffsets: IntArray
): Boolean {
    return parseStringCharacters(lexer, chars, outChars, sourceOffsets, ::decodeEscape)
}

/** See https://github.com/toml-lang/toml/blob/master/README.md#string */
private fun decodeEscape(esc: String): String = when (esc) {
    "\\b" -> "\b"
    "\\t" -> "\t"
    "\\n" -> "\n"
    "\\f" -> "\u000C"
    "\\r" -> "\r"
    "\\\"" -> "\""
    "\\\\" -> "\\"

    else -> {
        assert(esc.length >= 2)
        assert(esc[0] == '\\')
        when (esc[1]) {
            'u', 'U' -> Integer.parseInt(esc.substring(2, esc.length), 16).toChar().toString()
            '\r', '\n' -> ""
            else -> error("unreachable")
        }
    }
}
