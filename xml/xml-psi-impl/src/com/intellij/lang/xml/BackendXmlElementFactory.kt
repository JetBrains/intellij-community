package com.intellij.lang.xml

import com.intellij.lang.ASTNode
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.xml.XmlFileImpl
import com.intellij.psi.impl.source.xml.stub.XmlStubBasedElementType
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.UnsupportedNodeElementTypeException

class BackendXmlElementFactory :
  BasicXmlElementFactory {

  override fun createFile(
    viewProvider: FileViewProvider,
    elementType: IElementType,
  ): PsiFile =
    XmlFileImpl(viewProvider, elementType)

  override fun createElement(node: ASTNode): PsiElement {
    val elementType = node.elementType

    return when (elementType) {
      is XmlStubBasedElementType<*, *>,
        -> elementType.createPsi(node)

      else -> throw UnsupportedNodeElementTypeException(node)
    }
  }
}
