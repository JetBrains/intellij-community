/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.ide

import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey
import com.intellij.openapi.fileTypes.SingleLazyInstanceSyntaxHighlighterFactory
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.psi.tree.IElementType
import gnu.trove.THashMap
import org.toml.lang.parse.TomlLexer
import org.toml.lang.psi.TomlElementTypes.*
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors as Default

class TomlHighlighter : SyntaxHighlighterBase() {
    override fun getHighlightingLexer(): Lexer =
        TomlLexer()

    override fun getTokenHighlights(tokenType: IElementType): Array<out TextAttributesKey> =
        pack(tokenMap[tokenType])

    private val tokenMap: Map<IElementType, TextAttributesKey> =
        THashMap<IElementType, TextAttributesKey>().apply {
            put(BARE_KEY, createTextAttributesKey("TOML_KEY", Default.KEYWORD))
            put(COMMENT, createTextAttributesKey("TOML_COMMENT", Default.LINE_COMMENT))
            put(BASIC_STRING, createTextAttributesKey("TOML_STRING", Default.STRING))
            put(LITERAL_STRING, createTextAttributesKey("TOML_STRING", Default.STRING))
            put(MULTILINE_BASIC_STRING, createTextAttributesKey("TOML_STRING", Default.STRING))
            put(MULTILINE_LITERAL_STRING, createTextAttributesKey("TOML_STRING", Default.STRING))
            put(NUMBER, createTextAttributesKey("TOML_NUMBER", Default.NUMBER))
            put(BOOLEAN, createTextAttributesKey("TOML_BOOLEAN", Default.PREDEFINED_SYMBOL))
            put(DATE_TIME, createTextAttributesKey("TOML_DATE", Default.PREDEFINED_SYMBOL))
        }
}

class TomlHighlighterFactory : SingleLazyInstanceSyntaxHighlighterFactory() {
    override fun createHighlighter(): SyntaxHighlighter = TomlHighlighter()
}


