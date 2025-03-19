package com.intellij.html.embedding

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement

interface BasicHtmlRawTextElementFactory {
  fun createRawTextElement(node: ASTNode): PsiElement
}
