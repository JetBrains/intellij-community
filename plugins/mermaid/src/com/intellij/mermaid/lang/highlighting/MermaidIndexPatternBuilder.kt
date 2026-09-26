// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mermaid.lang.highlighting

import com.intellij.lexer.Lexer
import com.intellij.mermaid.lang.lexer.MermaidLexer
import com.intellij.mermaid.lang.lexer.MermaidTokens
import com.intellij.mermaid.lang.psi.MermaidFile
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.search.IndexPatternBuilder
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet

class MermaidIndexPatternBuilder : IndexPatternBuilder {
  override fun getIndexingLexer(file: PsiFile): Lexer? {
    if (file !is MermaidFile) return null

    return MermaidLexer()
  }

  override fun getCommentTokenSet(file: PsiFile): TokenSet? {
    if (file !is MermaidFile) return null

    return TokenSet.create(MermaidTokens.LINE_COMMENT)
  }

  override fun getCommentStartDelta(tokenType: IElementType?) = 2

  override fun getCommentEndDelta(tokenType: IElementType?) = 0
}
