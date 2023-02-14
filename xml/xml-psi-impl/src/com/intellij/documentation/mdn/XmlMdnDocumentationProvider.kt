// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.documentation.mdn

import com.intellij.lang.documentation.DocumentationProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.source.html.dtd.HtmlSymbolDeclaration
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.findParentInFile
import com.intellij.psi.util.findParentOfType
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlElement
import com.intellij.psi.xml.XmlTag
import com.intellij.util.asSafely
import com.intellij.xml.util.HtmlUtil
import com.intellij.xml.util.XmlUtil
import org.jetbrains.annotations.Nls

class XmlMdnDocumentationProvider : DocumentationProvider {

  override fun getUrlFor(element: PsiElement, originalElement: PsiElement?): List<String>? =
    getMdnDocumentation(element, originalElement)?.let { listOf(it.url) }

  override fun generateDoc(element: PsiElement, originalElement: PsiElement?): @Nls String? =
    getMdnDocumentation(element, originalElement)?.getDocumentation(true)


  override fun getDocumentationElementForLookupItem(psiManager: PsiManager, `object`: Any, element: PsiElement?): PsiElement? {
    return null
  }


  companion object {

    private val supportedNamespaces = setOf(HtmlUtil.SVG_NAMESPACE, HtmlUtil.MATH_ML_NAMESPACE, XmlUtil.HTML_URI, XmlUtil.XHTML_URI)

    private fun getMdnDocumentation(element: PsiElement, originalElement: PsiElement?): MdnSymbolDocumentation? =
      originalElement.takeIf { it is XmlElement }
        ?.let { PsiTreeUtil.getNonStrictParentOfType<XmlElement>(it, XmlTag::class.java, XmlAttribute::class.java) }
        ?.let {
          when {
            it is XmlAttribute && supportedNamespaces.contains(it.parent.getNamespaceByPrefix(it.namespacePrefix)) ->
              getHtmlMdnDocumentation(element, it.parent)
            it is XmlTag && supportedNamespaces.contains(it.namespace) -> getHtmlMdnDocumentation(element, it)
            else -> null
          }
        }
      ?: (element as? HtmlSymbolDeclaration)?.let {
        getHtmlMdnDocumentation(element, PsiTreeUtil.getNonStrictParentOfType(originalElement, XmlTag::class.java))
      }
      ?: (element as? XmlTag)
        ?.takeIf { it.namespace == XmlUtil.XML_SCHEMA_URI }
        ?.let { schemaElement ->
          val targetNamespace =
            schemaElement
              .findParentInFile { it is XmlTag && it.localName == "schema" }
              ?.asSafely<XmlTag>()
              ?.getAttributeValue("targetNamespace")
              ?.let {
                when (it) {
                  HtmlUtil.SVG_NAMESPACE -> MdnApiNamespace.Svg
                  HtmlUtil.MATH_ML_NAMESPACE -> MdnApiNamespace.MathML
                  XmlUtil.HTML_URI, XmlUtil.XHTML_URI -> MdnApiNamespace.Html
                  else -> null
                }
              } ?: return@let null
          val name = schemaElement.getAttributeValue("name") ?: return@let null
          when (element.localName) {
            "element" -> getHtmlMdnTagDocumentation(targetNamespace, name)
            "attribute" -> getHtmlMdnAttributeDocumentation(targetNamespace, originalElement?.findParentOfType<XmlTag>()?.localName, name)
            else -> null
          }
        }
  }
}
