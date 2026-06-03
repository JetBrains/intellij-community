package org.intellij.plugins.markdown.lang.references

import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.fileTypes.UnknownFileType
import com.intellij.openapi.project.Project
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiReference
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import org.intellij.plugins.markdown.lang.MarkdownFileType
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

  @JvmStatic
  fun hasMarkdownFiles(project: Project): Boolean {
    return CachedValuesManager.getManager(project).getCachedValue(project) {
      CachedValueProvider.Result.create(
        FileTypeIndex.containsFileOfType(MarkdownFileType.INSTANCE, GlobalSearchScope.projectScope(project)),
        PsiModificationTracker.MODIFICATION_COUNT
      )
    }
  }

  @ApiStatus.Internal
  fun String.isRelativePathLike(): Boolean {
    if (startsWith('/')) return false
    if (any(Char::isWhitespace)) return false
    if (contains("://")) return false
    if (contains('/')) return true
    return FileTypeRegistry.getInstance().getFileTypeByFileName(this) != UnknownFileType.INSTANCE
  }

  val linkDestinationPattern = PlatformPatterns.psiElement(MarkdownLinkDestination::class.java)
    .inFile(PlatformPatterns.psiFile(MarkdownFile::class.java))
}
