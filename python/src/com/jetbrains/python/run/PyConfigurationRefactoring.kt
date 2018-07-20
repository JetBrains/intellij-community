/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.jetbrains.python.run

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.refactoring.listeners.UndoRefactoringElementAdapter

/**
 * Tools to support refactoring for configurations
 * that implements [com.intellij.execution.configurations.RefactoringListenerProvider]
 */


/**
 * @see CompositeRefactoringElementListener
 */
abstract class UndoRefactoringCompletionListener : UndoRefactoringElementAdapter() {
  public abstract override fun refactored(element: PsiElement, oldQualifiedName: String?)
}

/**
 * Chains several [com.intellij.refactoring.listeners.RefactoringElementListener]
 */
class CompositeRefactoringElementListener(private vararg val listeners: UndoRefactoringCompletionListener) : UndoRefactoringElementAdapter() {
  override fun refactored(element: PsiElement, oldQualifiedName: String?) {
    listeners.forEach { it.refactored(element, oldQualifiedName) }
  }

  /**
   * Creates new listener adding provided one
   */
  operator fun plus(listener: UndoRefactoringCompletionListener): CompositeRefactoringElementListener = CompositeRefactoringElementListener(*arrayOf(listener) + listeners)
}


/**
 * Renames working directory if folder physically renamed
 */
class PyWorkingDirectoryRenamer(private val workingDirectoryFile: VirtualFile?,
                                private val conf: AbstractPythonRunConfiguration<*>) : UndoRefactoringCompletionListener() {
  override fun refactored(element: PsiElement, oldQualifiedName: String?) {
    workingDirectoryFile?.let {
      conf.setWorkingDirectory(it.path)
    }
  }
}

