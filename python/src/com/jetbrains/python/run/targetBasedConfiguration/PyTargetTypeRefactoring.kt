/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.jetbrains.python.run.targetBasedConfiguration

import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.extensions.getQName
import com.jetbrains.python.psi.PyQualifiedNameOwner
import com.jetbrains.python.run.UndoRefactoringCompletionListener


/**
 * Creates listener that renames target.
 * @param elementUnderRefactoring "element" argument of [com.intellij.execution.configurations.RefactoringListenerProvider.getRefactoringElementListener]
 * @param targetPsiElement configuration psi element (see [targetAsPsiElement]] or null if configuration is not qname-based
 * @param targetVirtualFile configuration virtual file element (see [targetAsVirtualFile]] or null if configuration is not file-based
 * @param setTarget lambda to set target on rename
 * @return renamer or null if [elementUnderRefactoring] does not affect current configuration
 */
fun createRefactoringListenerIfPossible(elementUnderRefactoring: PsiElement,
                                        targetPsiElement: PsiElement?,
                                        targetVirtualFile: VirtualFile?,
                                        setTarget: (String) -> Unit)
  : UndoRefactoringCompletionListener? {
  if (targetPsiElement != null && PsiTreeUtil.isAncestor(elementUnderRefactoring, targetPsiElement, false)) {
    return PyElementTargetRenamer(targetPsiElement, setTarget)
  }
  if (targetVirtualFile != null && elementUnderRefactoring is PsiFileSystemItem && VfsUtil.isAncestor(
    elementUnderRefactoring.virtualFile, targetVirtualFile, false)) {
    return PyVirtualFileRenamer(targetVirtualFile, setTarget)
  }
  return null
}


/**
 * Renames python target if python symbol, module or folder renamed
 */
private class PyElementTargetRenamer(private val originalElement: PsiElement, private val setTarget: (String) -> Unit) :
  UndoRefactoringCompletionListener() {
  override fun refactored(element: PsiElement, oldQualifiedName: String?) {
    if (originalElement is PyQualifiedNameOwner) {
      originalElement.qualifiedName?.let { setTarget(it) }
      return
    }
    if (originalElement is PsiFileSystemItem) {
      originalElement.getQName()?.let { setTarget(it.toString()) }
      return
    }
    else if (originalElement is PsiNamedElement) {
      originalElement.name?.let { setTarget(it) }
    }
  }
}

/**
 * Renames folder target if file or folder really renamed
 */
private class PyVirtualFileRenamer(private val virtualFile: VirtualFile, private val setTarget: (String) -> Unit) :
  UndoRefactoringCompletionListener() {
  override fun refactored(element: PsiElement, oldQualifiedName: String?) {
    setTarget(virtualFile.path)
  }
}
