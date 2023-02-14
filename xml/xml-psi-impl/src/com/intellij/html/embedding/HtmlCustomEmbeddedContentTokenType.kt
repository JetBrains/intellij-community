// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.html.embedding

import com.intellij.embedding.EmbeddingElementType
import com.intellij.lang.*
import com.intellij.lexer.Lexer
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LazyParseableElement
import com.intellij.psi.tree.ICustomParsingType
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.ILazyParseableElementTypeBase
import com.intellij.psi.tree.ILightLazyParseableElementType
import com.intellij.util.CharTable
import com.intellij.util.diff.FlyweightCapableTreeStructure
import org.jetbrains.annotations.NonNls

abstract class HtmlCustomEmbeddedContentTokenType
  : IElementType, EmbeddingElementType, ICustomParsingType,
    ILazyParseableElementTypeBase, ILightLazyParseableElementType {

  protected constructor(@NonNls debugName: String, language: Language?) : super(debugName, language)

  protected constructor(@NonNls debugName: String, language: Language?, register: Boolean) : super(debugName, language, register)

  override fun parse(text: CharSequence, table: CharTable): ASTNode = LazyParseableElement(this, text)

  override fun parseContents(chameleon: ASTNode): ASTNode? =
    doParseContents(chameleon).treeBuilt.firstChildNode

  override fun parseContents(chameleon: LighterLazyParseableNode): FlyweightCapableTreeStructure<LighterASTNode> {
    val file = chameleon.containingFile ?: error("Let's add LighterLazyParseableNode#getProject() method")
    return PsiBuilderFactory.getInstance().createBuilder(
      file.project, chameleon, createLexer(), language, chameleon.text
    )
      .also { parse(it) }
      .lightTree
  }

  protected fun doParseContents(chameleon: ASTNode): PsiBuilder =
    PsiBuilderFactory.getInstance().createBuilder(
      chameleon.psi.project, chameleon, createLexer(), language, chameleon.chars
    )
      .also { parse(it) }

  protected abstract fun createLexer(): Lexer

  protected abstract fun parse(builder: PsiBuilder)

  abstract fun createPsi(node: ASTNode): PsiElement

}
