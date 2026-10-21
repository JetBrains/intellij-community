// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.lang.lexer

import org.intellij.markdown.ast.ASTNode
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class MarkdownParsedTreeLexer(private val parsedTree: ASTNode) : MarkdownTreeLexerBase() {
  override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
    reset(buffer, endOffset)
    addTokens(parsedTree.children, startOffset)
  }
}
