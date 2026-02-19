// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.lang.references.paths

import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference
import com.intellij.psi.util.PsiUtilCore
import org.intellij.plugins.markdown.lang.MarkdownFileType
import org.intellij.plugins.markdown.lang.isMarkdownType
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
abstract class FileWithoutExtensionReference(
  element: PsiElement,
  private val fileReference: FileReference,
  soft: Boolean
): PsiReferenceBase<PsiElement>(element, fileReference.rangeInElement, soft) {
  protected val path
    get() = fileReference.fileReferenceSet.pathString + '.' + MarkdownFileType.INSTANCE.defaultExtension

  protected val containingFile: VirtualFile?
    get() = element.containingFile?.virtualFile

  override fun resolve(): PsiElement? {
    val referencedFile = findReferencedFile() ?: return null
    val directory = referencedFile.parent ?: return null
    if (directory.findChild(referencedFile.nameWithoutExtension) == null) {
      return PsiUtilCore.getPsiFile(element.project, referencedFile)
    }
    return null
  }

  protected abstract fun findReferencedFile(): VirtualFile?

  override fun handleElementRename(newElementName: String): PsiElement {
    val newText = when {
      hasMarkdownExtensions(newElementName) -> FileUtilRt.getNameWithoutExtension(newElementName)
      else -> newElementName
    }
    return super.handleElementRename(newText)
  }

  companion object {
    private fun hasMarkdownExtensions(name: String): Boolean {
      val type = FileTypeRegistry.getInstance().getFileTypeByFileName(name)
      return type.isMarkdownType()
    }
  }
}
