// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.requirements.parser

import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiParser
import com.intellij.lexer.FlexAdapter
import com.intellij.lexer.Lexer
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet
import com.intellij.python.requirements.RequirementsFile
import com.intellij.python.requirements.RequirementsLanguage
import com.intellij.python.requirements.parser.psi.RequirementsTypes

internal class RequirementsParserDefinition : ParserDefinition {
  override fun createLexer(project: Project): Lexer {
    return RequirementsLexerAdapter()
  }

  override fun getStringLiteralElements(): TokenSet {
    return STRING_LITERALS
  }

  override fun createParser(project: Project): PsiParser {
    return RequirementsParser()
  }

  override fun getFileNodeType(): IFileElementType {
    return FILE
  }

  override fun getCommentTokens(): TokenSet {
    return COMMENTS
  }

  override fun createFile(viewProvider: FileViewProvider): PsiFile {
    return RequirementsFile(viewProvider)
  }

  override fun createElement(node: ASTNode): PsiElement {
    return RequirementsTypes.Factory.createElement(node)
  }
}

private val STRING_LITERALS: TokenSet = TokenSet.create(RequirementsTypes.QUOTED_STRING_TOKEN)
private val COMMENTS: TokenSet = TokenSet.create(RequirementsTypes.COMMENT)
private val FILE = IFileElementType(RequirementsLanguage)

internal class RequirementsLexerAdapter : FlexAdapter(RequirementsLexer(null)) {
  /**
   * [FlexAdapter] reuses one [RequirementsLexer] instance and calls its generated `reset()` on every
   * (re)start. That reset restores the JFlex `zz*` fields but not the custom `yypush`/`yypop` state
   * stack, so incremental re-lexing in the editor would carry a stale stack from the previous run and
   * mis-tokenize — e.g. a version losing its highlight when a space is typed before `;`. Injected
   * fragments (`pyproject.toml`) are unaffected because they are always lexed from scratch.
   *
   * Rebuild the stack deterministically from `initialState` before each start so re-lexing is a pure
   * function of `(text, initialState)`, matching the stack a full scan would have at that state.
   */
  override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
    val lexer = flex as RequirementsLexer
    lexer.stack.clear()
    if (initialState != RequirementsLexer.YYINITIAL) {
      lexer.stack.push(RequirementsLexer.YYINITIAL)
    }
    lexer.stack.push(initialState)
    super.start(buffer, startOffset, endOffset, initialState)
  }
}
