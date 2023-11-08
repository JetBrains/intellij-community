package org.intellij.plugins.markdown.model.psi.headers.html

import com.intellij.lang.html.HTMLLanguage
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.parentOfType
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlAttributeValue
import org.intellij.plugins.markdown.lang.isMarkdownLanguage
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownFile

internal fun XmlAttributeValue.isValidAnchorAttributeValue(): Boolean {
  val attribute = parentOfType<XmlAttribute>() ?: return false
  val name = attribute.name
  return name == "name" || name == "id"
}

internal fun isInsideInjectedHtml(element: PsiElement): Boolean {
  val file = element.containingFile
  val viewProvider = file.viewProvider
  val baseLanguage = viewProvider.baseLanguage
  return baseLanguage.isMarkdownLanguage()
}

internal fun findHostMarkdownFile(element: PsiElement): PsiFile? {
  val file = element.containingFile
  if (file.language.isMarkdownLanguage()) {
    return file
  }
  val viewProvider = file.viewProvider
  val baseLanguage = viewProvider.baseLanguage
  if (!baseLanguage.isMarkdownLanguage()) {
    return null
  }
  val files = viewProvider.allFiles.asSequence().filterIsInstance<MarkdownFile>()
  check(files.count() == 1)
  return files.firstOrNull()
}

internal fun findInjectedHtmlFile(file: PsiFile): PsiFile? {
  val viewProvider = file.viewProvider
  return viewProvider.allFiles.find { it.language == HTMLLanguage.INSTANCE }
}
