package org.intellij.plugins.markdown.lang.references.paths.github

import com.intellij.openapi.paths.PathReferenceProviderBase
import com.intellij.openapi.paths.StaticPathReferenceProvider
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceProvider
import com.intellij.util.ProcessingContext
import org.intellij.plugins.markdown.lang.references.ReferenceUtil
import org.intellij.plugins.markdown.lang.references.headers.HeaderAnchorPathReferenceProvider
import org.intellij.plugins.markdown.lang.references.paths.ContentRootRelatedFileWithoutExtensionReference
import java.util.regex.Pattern

internal class GithubWikiLocalFileReferenceProvider: PsiReferenceProvider() {
  private val anchorReferenceProvider = HeaderAnchorPathReferenceProvider()
  private val staticPathProvider = StaticPathReferenceProvider(null)

  init {
    staticPathProvider.apply {
      setEndingSlashNotAllowed(true)
      setRelativePathsAllowed(false)
    }
  }

  override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
    val text = element.text
    val pathRange = findPathRangeInElement(text) ?: return emptyArray()
    val path = pathRange.substring(text)
    val references = mutableListOf<PsiReference>()
    staticPathProvider.createReferences(element, pathRange.startOffset, path, references, ARE_REFERENCES_SOFT)
    val fileReferences = ReferenceUtil.findFileReference(references)
    if (fileReferences != null) {
      references.add(ContentRootRelatedFileWithoutExtensionReference(element, fileReferences, ARE_REFERENCES_SOFT))
    }
    anchorReferenceProvider.createReferences(element, references, ARE_REFERENCES_SOFT)
    return references.map { GithubWikiLocalFileReference(it) }.toTypedArray()
  }

  private fun findPathRangeInElement(text: String): TextRange? {
    val matcher = linkPattern.matcher(text)
    if (!matcher.find()) {
      return null
    }
    val start = matcher.end()
    return when (val end = PathReferenceProviderBase.getLastPosOfURL(start, text)) {
      -1 -> TextRange(start, text.length)
      else -> TextRange(start, end)
    }
  }

  companion object {
    private val linkPattern = Pattern.compile("^https://github.com/[^/]*/[^/]*/wiki/")
    private const val ARE_REFERENCES_SOFT = false
  }
}
