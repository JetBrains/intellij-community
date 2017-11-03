/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.ide

import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.psi.tree.IElementType
import gnu.trove.THashMap
import org.toml.lang.parse.TomlLexer
//import org.toml.lang.core.psi.TomlTypes
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors as Default

class TomlHighlighter : SyntaxHighlighterBase() {
    private val tokenMap: Map<IElementType, TextAttributesKey> =
        THashMap<IElementType, TextAttributesKey>().apply {
//            put(TomlTypes.KEY, createTextAttributesKey("TOML_KEY", Default.KEYWORD))
//            put(TomlTypes.COMMENT, createTextAttributesKey("TOML_COMMENT", Default.LINE_COMMENT))
//            put(TomlTypes.STRING, createTextAttributesKey("TOML_STRING", Default.STRING))
//            put(TomlTypes.NUMBER, createTextAttributesKey("TOML_NUMBER", Default.NUMBER))
//            put(TomlTypes.BOOLEAN, createTextAttributesKey("TOML_BOOLEAN", Default.PREDEFINED_SYMBOL))
//            put(TomlTypes.DATE, createTextAttributesKey("TOML_DATE", Default.PREDEFINED_SYMBOL))
        }

    override fun getHighlightingLexer(): Lexer =
        TomlLexer()

    override fun getTokenHighlights(tokenType: IElementType): Array<out TextAttributesKey> =
        pack(tokenMap[tokenType])
}

