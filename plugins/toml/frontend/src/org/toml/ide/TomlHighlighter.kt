/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */
package org.toml.ide

import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.psi.StringEscapesTokenTypes.INVALID_CHARACTER_ESCAPE_TOKEN
import com.intellij.psi.StringEscapesTokenTypes.INVALID_UNICODE_ESCAPE_TOKEN
import com.intellij.psi.StringEscapesTokenTypes.VALID_STRING_ESCAPE_TOKEN
import com.intellij.psi.tree.IElementType
import org.toml.ide.colors.TomlColor
import org.toml.lang.lexer.TomlHighlightingLexer
import org.toml.lang.psi.TomlElementTypes.BARE_KEY
import org.toml.lang.psi.TomlElementTypes.BASIC_STRING
import org.toml.lang.psi.TomlElementTypes.BOOLEAN
import org.toml.lang.psi.TomlElementTypes.COMMENT
import org.toml.lang.psi.TomlElementTypes.DATE_TIME
import org.toml.lang.psi.TomlElementTypes.LITERAL_STRING
import org.toml.lang.psi.TomlElementTypes.MULTILINE_BASIC_STRING
import org.toml.lang.psi.TomlElementTypes.MULTILINE_LITERAL_STRING
import org.toml.lang.psi.TomlElementTypes.NUMBER

class TomlHighlighter : SyntaxHighlighterBase() {
  override fun getHighlightingLexer(): Lexer = TomlHighlightingLexer()

  override fun getTokenHighlights(tokenType: IElementType): Array<out TextAttributesKey> {
    return pack(tokenMap[tokenType]?.textAttributesKey)
  }

  private val tokenMap: Map<IElementType, TomlColor> = HashMap<IElementType, TomlColor>().apply {
    put(BARE_KEY, TomlColor.KEY)
    put(COMMENT, TomlColor.COMMENT)
    put(BASIC_STRING, TomlColor.STRING)
    put(LITERAL_STRING, TomlColor.STRING)
    put(MULTILINE_BASIC_STRING, TomlColor.STRING)
    put(MULTILINE_LITERAL_STRING, TomlColor.STRING)
    put(NUMBER, TomlColor.NUMBER)
    put(BOOLEAN, TomlColor.BOOLEAN)
    put(DATE_TIME, TomlColor.DATE)
    put(INVALID_CHARACTER_ESCAPE_TOKEN, TomlColor.INVALID_STRING_ESCAPE)
    put(INVALID_UNICODE_ESCAPE_TOKEN, TomlColor.INVALID_STRING_ESCAPE)
    put(VALID_STRING_ESCAPE_TOKEN, TomlColor.VALID_STRING_ESCAPE)
  }
}
