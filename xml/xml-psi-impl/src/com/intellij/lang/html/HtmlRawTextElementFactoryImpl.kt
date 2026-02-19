package com.intellij.lang.html

import com.intellij.html.embedding.BasicHtmlRawTextElementFactory
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.html.HtmlRawTextImpl

class HtmlRawTextElementFactoryImpl: BasicHtmlRawTextElementFactory {
  override fun createRawTextElement(node: ASTNode): PsiElement = HtmlRawTextImpl(node)
}
