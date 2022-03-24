/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.lang.lexer

import com.intellij.lexer.LayeredLexer
import org.toml.lang.lexer.TomlEscapeLexer.Companion.ESCAPABLE_LITERALS_TOKEN_SET

class TomlHighlightingLexer : LayeredLexer(TomlLexer()) {
    init {
        for (tokenType in ESCAPABLE_LITERALS_TOKEN_SET.types) {
            registerLayer(TomlEscapeLexer.of(tokenType), tokenType)
        }
    }
}
