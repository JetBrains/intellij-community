// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.miscProject.impl

import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.refactoring.listeners.RefactoringElementListener
import com.intellij.refactoring.listeners.RefactoringElementListenerProvider
import kotlin.io.path.name

/**
 * Listens for misc project rename
 */
internal class MiscProjectListenerProvider : RefactoringElementListenerProvider {
  override fun getListener(element: PsiElement): RefactoringElementListener? {
    if (!miscProjectEnabled) return null
    val dir = (element as? PsiDirectory) ?: return null
    // dir name is enough
    return if (dir.name == miscProjectDefaultPath.value.name) MiscProjectRenameReporter else null
  }
}

private object MiscProjectRenameReporter : RefactoringElementListener {
  override fun elementMoved(newElement: PsiElement) = Unit

  override fun elementRenamed(newElement: PsiElement) {
    // log
  }
}