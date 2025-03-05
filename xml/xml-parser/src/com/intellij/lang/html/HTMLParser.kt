// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.html

import com.intellij.lang.ASTNode
import com.intellij.lang.PsiBuilder
import com.intellij.lang.PsiParser
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet

open class HTMLParser : PsiParser {
  override fun parse(
    root: IElementType,
    builder: PsiBuilder,
  ): ASTNode {
    parseWithoutBuildingTree(root, builder, createHtmlParsing(builder))
    return builder.treeBuilt
  }

  fun parseWithoutBuildingTree(
    root: IElementType,
    builder: PsiBuilder,
  ) {
    parseWithoutBuildingTree(root, builder, createHtmlParsing(builder))
  }

  // to be able to manage what tags treated as single
  protected open fun createHtmlParsing(
    builder: PsiBuilder,
  ): HtmlParsing =
    HtmlParsing(builder)

  private companion object {
    private fun parseWithoutBuildingTree(
      root: IElementType,
      builder: PsiBuilder,
      htmlParsing: HtmlParsing,
    ) {
      builder.enforceCommentTokens(TokenSet.EMPTY)
      val file = builder.mark()
      htmlParsing.parseDocument()
      file.done(root)
    }
  }
}
