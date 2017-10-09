/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.ide

import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.psi.tree.IElementType
import gnu.trove.THashMap
import org.toml.lang.core.lexer.TomlLexer
import org.toml.lang.core.psi.TomlTypes


class TomlHighlighter : SyntaxHighlighterBase() {
    override fun getHighlightingLexer(): Lexer =
        TomlLexer()


    override fun getTokenHighlights(tokenType: IElementType?): Array<out TextAttributesKey> =
        pack(tokenType?.let { tokenMap[it] })


    private val tokenMap: Map<IElementType, TextAttributesKey> = makeTokenMap()

}

private fun makeTokenMap(): Map<IElementType, TextAttributesKey> {
    val result = THashMap<IElementType, TextAttributesKey>()
    result[TomlTypes.KEY] =
        TextAttributesKey.createTextAttributesKey("TOML_KEY", DefaultLanguageHighlighterColors.KEYWORD)

    result[TomlTypes.COMMENT] =
        TextAttributesKey.createTextAttributesKey("TOML_COMMENT", DefaultLanguageHighlighterColors.LINE_COMMENT)

    result[TomlTypes.STRING] =
        TextAttributesKey.createTextAttributesKey("TOML_STRING", DefaultLanguageHighlighterColors.STRING)

    result[TomlTypes.NUMBER] =
        TextAttributesKey.createTextAttributesKey("TOML_NUMBER", DefaultLanguageHighlighterColors.NUMBER)

    result[TomlTypes.BOOLEAN] =
        TextAttributesKey.createTextAttributesKey("TOML_BOOLEAN", DefaultLanguageHighlighterColors.PREDEFINED_SYMBOL)

    result[TomlTypes.DATE] =
        TextAttributesKey.createTextAttributesKey("TOML_DATE", DefaultLanguageHighlighterColors.PREDEFINED_SYMBOL)

    return result
}
