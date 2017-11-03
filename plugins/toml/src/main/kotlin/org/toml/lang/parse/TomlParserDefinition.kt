/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.lang.parse

import com.intellij.lang.ASTNode
import com.intellij.lang.LanguageUtil
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiParser
import com.intellij.lexer.Lexer
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet
import org.toml.lang.TomlLanguage
import org.toml.lang.psi.TomlFile
import org.toml.lang.psi.TomlElementTypes

class TomlParserDefinition : ParserDefinition {
    override fun createParser(project: Project?): PsiParser = TomlParser()

    override fun createFile(viewProvider: FileViewProvider): PsiFile = TomlFile(viewProvider)

    override fun spaceExistanceTypeBetweenTokens(left: ASTNode?, right: ASTNode?): ParserDefinition.SpaceRequirements =
        LanguageUtil.canStickTokensTogetherByLexer(left, right, TomlLexer())

    override fun getStringLiteralElements(): TokenSet = TokenSet.EMPTY

    override fun getWhitespaceTokens(): TokenSet = WHITE_SPACES

    override fun getCommentTokens(): TokenSet = COMMENTS

    override fun getFileNodeType(): IFileElementType? = FILE

    override fun createLexer(project: Project?): Lexer = TomlLexer()

    override fun createElement(node: ASTNode): PsiElement =
        throw UnsupportedOperationException(node.elementType.toString()) // See org.toml.lang.psi.impl.TomlASTFactory

    companion object {
        val FILE: IFileElementType = IFileElementType(TomlLanguage)
        val WHITE_SPACES: TokenSet = TokenSet.create(TokenType.WHITE_SPACE)
        val COMMENTS: TokenSet = TokenSet.create(TomlElementTypes.COMMENT)
    }
}


