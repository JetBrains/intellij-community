/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.ide.wordSelection

import com.intellij.codeInsight.editorActions.ExtendWordSelectionHandlerBase
import com.intellij.codeInsight.editorActions.SelectWordUtil
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.toml.lang.lexer.TomlEscapeLexer
import org.toml.lang.psi.TOML_STRING_LITERALS
import org.toml.lang.psi.ext.TomlLiteralKind
import org.toml.lang.psi.ext.elementType

class TomlStringLiteralSelectionHandler : ExtendWordSelectionHandlerBase() {
    override fun canSelect(e: PsiElement): Boolean =
        e.elementType in TOML_STRING_LITERALS

    override fun select(e: PsiElement, editorText: CharSequence, cursorOffset: Int, editor: Editor): List<TextRange>? {
        val kind = TomlLiteralKind.fromAstNode(e.node) as? TomlLiteralKind.String ?: return null
        val valueRange = kind.offsets.value?.shiftRight(kind.node.startOffset) ?: return null
        val result = super.select(e, editorText, cursorOffset, editor) ?: mutableListOf()

        val elementType = e.elementType
        if (elementType in TomlEscapeLexer.ESCAPABLE_LITERALS_TOKEN_SET) {
            SelectWordUtil.addWordHonoringEscapeSequences(
                editorText,
                valueRange,
                cursorOffset,
                TomlEscapeLexer.of(elementType),
                result
            )
        }

        result += valueRange
        return result
    }
}
