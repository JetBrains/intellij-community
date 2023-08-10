/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.lang.psi

import org.toml.lang.lexer.parseTomlStringCharacters

class TomlLiteralTextEscaper(host: TomlLiteral) : LiteralTextEscaperBase<TomlLiteral>(host) {

    override fun parseStringCharacters(chars: String, outChars: StringBuilder): Pair<IntArray, Boolean> {
        val child = myHost.node.findChildByType(TOML_BASIC_STRINGS) ?: error("Failed to find basic string child")
        return parseTomlStringCharacters(child.elementType, chars, outChars)
    }

    override fun isOneLine(): Boolean = false
}
