package org.intellij.plugins.markdown.lang.references.paths

import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference

internal class RelativeFileWithoutExtensionReference(
  element: PsiElement,
  fileReference: FileReference,
  soft: Boolean
): FileWithoutExtensionReference(element, fileReference, soft) {
  override fun findReferencedFile(): VirtualFile? {
    return VfsUtilCore.findRelativeFile(path, containingFile)
  }
}
