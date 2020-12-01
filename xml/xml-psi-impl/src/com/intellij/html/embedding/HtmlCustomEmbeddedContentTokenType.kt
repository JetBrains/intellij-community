// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.html.embedding

import com.intellij.embedding.EmbeddingElementType
import com.intellij.lang.ASTNode
import com.intellij.lang.Language
import com.intellij.lang.PsiBuilder
import com.intellij.lang.PsiBuilderFactory
import com.intellij.lexer.Lexer
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LazyParseableElement
import com.intellij.psi.tree.ICustomParsingType
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.ILazyParseableElementTypeBase
import com.intellij.util.CharTable
import org.jetbrains.annotations.NonNls

abstract class HtmlCustomEmbeddedContentTokenType : IElementType, EmbeddingElementType, ICustomParsingType, ILazyParseableElementTypeBase {

  protected constructor(@NonNls debugName: String, language: Language?) : super(debugName, language)

  protected constructor(@NonNls debugName: String, language: Language?, register: Boolean) : super(debugName, language, register)

  override fun parse(text: CharSequence, table: CharTable): ASTNode = LazyParseableElement(this, text)

  override fun parseContents(chameleon: ASTNode): ASTNode? =
    doParseContents(chameleon).treeBuilt.firstChildNode

  protected fun doParseContents(chameleon: ASTNode): PsiBuilder =
    PsiBuilderFactory.getInstance().createBuilder(chameleon.psi.project, chameleon,
                                                  createLexer(), language, chameleon.chars)
      .also { parse(it) }

  protected abstract fun createLexer(): Lexer

  protected abstract fun parse(builder: PsiBuilder)

  abstract fun createPsi(node: ASTNode): PsiElement

}
