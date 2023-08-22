package org.intellij.plugins.markdown.lang.references.headers

import com.intellij.codeInsight.daemon.EmptyResolveMessageProvider
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownHeader
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownLinkDestination
import org.intellij.plugins.markdown.lang.psi.util.children
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class HeaderAnchorReference(
  element: MarkdownLinkDestination,
  private val fileReference: PsiReference?,
  private val anchorText: String,
  private val textRangeInElement: TextRange
): PsiPolyVariantReferenceBase<MarkdownLinkDestination>(element), EmptyResolveMessageProvider {
  private fun resolveFile(): PsiFile? {
    return when (fileReference) {
      null -> element.containingFile.originalFile
      else -> fileReference.resolve() as? PsiFile
    }
  }

  override fun getCanonicalText(): String {
    return anchorText
  }

  override fun getRangeInElement(): TextRange {
    return textRangeInElement
  }

  override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
    val file = resolveFile()
    val header = file?.children().orEmpty().filterIsInstance<MarkdownHeader>().find { it.anchorText == canonicalText } ?: return emptyArray()
    return PsiElementResolveResult.createResults(header)
  }

  override fun getUnresolvedMessagePattern(): String {
    return when (val file = resolveFile()) {
      null -> MarkdownBundle.message("markdown.cannot.resolve.anchor.error.message", anchorText)
      else -> MarkdownBundle.message("markdown.cannot.resolve.anchor.in.file.error.message", anchorText, file.name)
    }
  }
}
