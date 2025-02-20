package com.intellij.lang.html

import com.intellij.html.embedding.BasicHtmlRawTextElementFactory
import com.intellij.html.embedding.HtmlCustomEmbeddedContentTokenType
import com.intellij.lang.ASTNode
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.html.HtmlEmbeddedContentImpl
import com.intellij.psi.impl.source.html.HtmlFileImpl
import com.intellij.psi.impl.source.html.HtmlRawTextImpl
import com.intellij.psi.impl.source.xml.stub.XmlStubBasedElementType
import com.intellij.psi.util.UnsupportedNodeElementTypeException
import com.intellij.psi.xml.XmlElementType

class BackendHtmlElementFactory:
  BasicHtmlElementFactory,
  BasicHtmlRawTextElementFactory {

  override fun createFile(viewProvider: FileViewProvider): PsiFile =
    HtmlFileImpl(viewProvider)

  override fun createElement(node: ASTNode): PsiElement {
    val elementType = node.elementType

    return when {
      elementType is XmlStubBasedElementType<*, *>
        -> elementType.createPsi(node)

      elementType is HtmlCustomEmbeddedContentTokenType
        -> elementType.createPsi(node)

      elementType === XmlElementType.HTML_EMBEDDED_CONTENT
        -> HtmlEmbeddedContentImpl(node)

      else -> throw UnsupportedNodeElementTypeException(node)
    }
  }

  override fun createRawTextElement(node: ASTNode): PsiElement =
    HtmlRawTextImpl(node)
}
