/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.ide.todo

import com.intellij.lexer.Lexer
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.search.IndexPatternBuilder
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import org.toml.lang.lexer.TomlLexer
import org.toml.lang.psi.TOML_COMMENTS
import org.toml.lang.psi.TomlFile

private class TomlTodoIndexPatternBuilder : IndexPatternBuilder {
    override fun getIndexingLexer(file: PsiFile): Lexer? = if (file is TomlFile) TomlLexer() else null
    override fun getCommentTokenSet(file: PsiFile): TokenSet? = if (file is TomlFile) TOML_COMMENTS else null
    override fun getCommentStartDelta(tokenType: IElementType?): Int = if (tokenType in TOML_COMMENTS) 1 else 0
    override fun getCommentEndDelta(tokenType: IElementType?): Int = 0
}
