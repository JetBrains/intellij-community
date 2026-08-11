// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.xml

import com.intellij.codeInspection.DefaultXmlSuppressionProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.templateLanguages.TemplateLanguageFileViewProvider
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlComment
import com.intellij.psi.xml.XmlFile
import org.intellij.plugins.markdown.lang.MarkdownElementTypes

internal class MarkdownSuppressionProvider: DefaultXmlSuppressionProvider() {
  override fun isSuppressedFor(element: PsiElement, inspectionId: String): Boolean =
    findFileSuppression(element, inspectionId, element) != null

  override fun suppressForFile(element: PsiElement, inspectionId: String) {
    val xmlFile = getXmlFile(element)
    if (xmlFile == null) return

    val candidate = xmlFile.findElementAt(0) ?: return
    val anchor = if (candidate.node.elementType == MarkdownElementTypes.FRONT_MATTER_HEADER_DELIMITER) {
      candidate.parent.nextSibling?.nextSibling ?: return // Next line after header delimiter
    } else {
      candidate
    }
    val suppression = findFileSuppression(anchor, null, element)
    suppress(xmlFile, suppression, inspectionId, anchor.textRange.startOffset)
  }

  override fun findFileSuppression(anchor: PsiElement, id: String?, originalElement: PsiElement): PsiElement? {
    val file = getXmlFile(anchor)
    if (file == null) return null
    val comment = PsiTreeUtil.findChildOfType(file, XmlComment::class.java) ?: return null
    return findSuppressionLeaf(comment, id, comment.textRange.startOffset)
  }

  private fun getXmlFile(element: PsiElement): XmlFile? {
    val file = element.containingFile
    if (file is XmlFile) return file

    val viewProvider = file.viewProvider
    if (viewProvider !is TemplateLanguageFileViewProvider) return null

    return viewProvider.allFiles.asSequence().filterIsInstance<XmlFile>().firstOrNull()
  }
}
