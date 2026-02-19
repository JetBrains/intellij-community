package org.intellij.plugins.markdown.lang.references.headers

import com.intellij.openapi.paths.PathReference
import com.intellij.openapi.paths.PathReferenceProviderBase
import com.intellij.openapi.util.TextRange
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownLinkDestination
import org.intellij.plugins.markdown.lang.references.paths.FileWithoutExtensionReference

internal class HeaderAnchorPathReferenceProvider: PathReferenceProviderBase() {
  override fun createReferences(
    element: PsiElement,
    offset: Int,
    text: String?,
    references: MutableList<in PsiReference>,
    soft: Boolean
  ): Boolean {
    if (element !is MarkdownLinkDestination) {
      return false
    }
    val range = ElementManipulators.getValueTextRange(element)
    val elementText = element.getText()
    val actualTextRange = calculateAnchorTextRange(elementText, range) ?: return false
    val anchor = actualTextRange.substring(elementText).lowercase()
    val fileReference = when {
      actualTextRange.startOffset != range.startOffset -> findFileReference(references) ?: return false
      else -> null
    }
    references.add(HeaderAnchorReference(element, fileReference, anchor, actualTextRange))
    return false
  }

  private fun findFileReference(alreadyCreatedReferences: MutableList<in PsiReference>): PsiReference? {
    val references = alreadyCreatedReferences.asSequence()
    val reference = references.filterIsInstance<FileReference>().firstOrNull()
    val actualReference = reference?.fileReferenceSet?.lastReference ?: return null
    val resolvedReference = actualReference.takeIf { it.resolve() != null }
    if (resolvedReference != null) {
      return resolvedReference
    }
    val fileWithoutExtensionReference = references.filterIsInstance<FileWithoutExtensionReference>().firstOrNull()
    return fileWithoutExtensionReference?.takeIf { it.resolve() != null }
  }

  override fun getPathReference(path: String, element: PsiElement): PathReference? {
    return null
  }

  private fun calculateAnchorTextRange(elementText: String, valueTextRange: TextRange): TextRange? {
    val anchorOffset = elementText.indexOf('#')
    if (anchorOffset == -1) {
      return null
    }
    val endOffset = valueTextRange.endOffset
    val endIndex = when {
      endOffset <= anchorOffset -> anchorOffset + 1
      else -> endOffset
    }
    return TextRange(anchorOffset + 1, endIndex)
  }
}
