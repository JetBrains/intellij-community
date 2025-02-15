package com.intellij.lang.xml

import com.intellij.lang.ASTNode
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

interface BasicXmlElementFactory {
  fun createFile(viewProvider: FileViewProvider): PsiFile

  fun createElement(node: ASTNode): PsiElement
}
