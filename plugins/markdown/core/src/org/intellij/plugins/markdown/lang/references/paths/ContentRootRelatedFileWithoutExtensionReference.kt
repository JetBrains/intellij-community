package org.intellij.plugins.markdown.lang.references.paths

import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference

internal class ContentRootRelatedFileWithoutExtensionReference(
  element: PsiElement,
  fileReference: FileReference,
  soft: Boolean
): FileWithoutExtensionReference(element, fileReference, soft) {
  override fun findReferencedFile(): VirtualFile? {
    val containingFile = containingFile ?: return null
    val fileIndex = ProjectRootManager.getInstance(element.project).fileIndex
    return fileIndex.getContentRootForFile(containingFile)?.findFileByRelativePath(path)
  }
}
