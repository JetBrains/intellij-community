package org.intellij.plugins.markdown.lang.references

import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiReference
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownFile
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownLinkDestination
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object ReferenceUtil {
  @JvmStatic
  fun findFileReference(references: MutableList<in PsiReference>): FileReference? {
    val reference = references.asSequence().filterIsInstance<FileReference>().firstOrNull()
    return reference?.fileReferenceSet?.lastReference
  }

  val linkDestinationPattern = PlatformPatterns.psiElement(MarkdownLinkDestination::class.java)
    .inFile(PlatformPatterns.psiFile(MarkdownFile::class.java))
}
