package com.intellij.xml.frontend.split.editor

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.lang.xml.BasicXmlElementFactory
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.IFileElementType

class FrontendXmlElementFactory :
  BasicXmlElementFactory {
  override fun createFile(
    viewProvider: FileViewProvider,
    elementType: IElementType,
  ): PsiFile =
    FrontendXmlFileImpl(viewProvider, IFileElementType(viewProvider.baseLanguage))

  override fun createElement(node: ASTNode): PsiElement =
    ASTWrapperPsiElement(node)
}
