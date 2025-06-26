package com.intellij.xml.frontend.split.editor

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.html.embedding.BasicHtmlRawTextElementFactory
import com.intellij.lang.ASTNode
import com.intellij.lang.html.BasicHtmlElementFactory
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

class FrontendHtmlElementFactory :
  BasicHtmlElementFactory,
  BasicHtmlRawTextElementFactory {

  override fun createFile(viewProvider: FileViewProvider): PsiFile =
    FrontendHtmlFileImpl(viewProvider)

  override fun createElement(node: ASTNode): PsiElement =
    ASTWrapperPsiElement(node)

  override fun createRawTextElement(node: ASTNode): PsiElement =
    ASTWrapperPsiElement(node)
}
