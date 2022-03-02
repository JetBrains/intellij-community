// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.lang.references

import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference
import com.intellij.psi.util.PsiUtilCore
import org.intellij.plugins.markdown.lang.MarkdownFileType

abstract class MissingExtensionFileReferenceBase(element: PsiElement,
                                                 private val myFileReference: FileReference,
                                                 soft: Boolean) : PsiReferenceBase<PsiElement>(element, myFileReference.rangeInElement,
                                                                                               soft) {
  protected val path get() = myFileReference.fileReferenceSet.pathString + '.' + MarkdownFileType.INSTANCE.defaultExtension
  protected val containingFile: VirtualFile? get() = element.containingFile?.virtualFile

  override fun resolve(): PsiElement? {
    val referencedFile = findReferencedFile() ?: return null
    return if (referencedFile.parent.findChild(referencedFile.nameWithoutExtension) == null) {
      PsiUtilCore.getPsiFile(element.project, referencedFile)
    }
    else {
      null
    }
  }

  protected abstract fun findReferencedFile(): VirtualFile?

  override fun handleElementRename(newElementName: String): PsiElement {
    val newText = if (FileUtilRt.extensionEquals(newElementName, MarkdownFileType.INSTANCE.defaultExtension)) {
      FileUtilRt.getNameWithoutExtension(newElementName)
    }
    else {
      newElementName
    }
    return super.handleElementRename(newText)
  }
}

private inline fun createReference(references: MutableList<PsiReference>,
                                   constructor: (FileReference) -> MissingExtensionFileReferenceBase) {
  MarkdownAnchorPathReferenceProvider.findFileReference(references)?.let { references.add(constructor(it)) }
}

class RelativeMissingExtensionFileReference private constructor(element: PsiElement,
                                                                fileReference: FileReference,
                                                                soft: Boolean) : MissingExtensionFileReferenceBase(element, fileReference,
                                                                                                                   soft) {
  override fun findReferencedFile() = VfsUtilCore.findRelativeFile(path, containingFile)

  companion object {
    fun createReference(element: PsiElement, references: MutableList<PsiReference>, soft: Boolean) =
      createReference(references) { RelativeMissingExtensionFileReference(element, it, soft) }
  }
}

class ContentRootRelatedMissingExtensionFileReference private constructor(element: PsiElement,
                                                                          fileReference: FileReference,
                                                                          soft: Boolean) : MissingExtensionFileReferenceBase(element,
                                                                                                                             fileReference,
                                                                                                                             soft) {
  override fun findReferencedFile(): VirtualFile? {
    return ProjectRootManager.getInstance(element.project).fileIndex
      .getContentRootForFile(containingFile ?: return null)?.findFileByRelativePath(path)
  }

  companion object {
    fun createReference(element: PsiElement, references: MutableList<PsiReference>, soft: Boolean) =
      createReference(references) { ContentRootRelatedMissingExtensionFileReference(element, it, soft) }
  }
}