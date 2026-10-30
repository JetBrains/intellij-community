// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap

import com.intellij.ide.structureView.StructureViewModel
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder
import com.intellij.lang.LanguageStructureViewBuilder
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiDocumentManager
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.concurrency.annotations.RequiresReadLock

internal class MinimapStructureProvider(private val project: Project?, private val parentDisposable: Disposable) {
  /**
   * Finds the structure view builder for the file of [editor].
   * A builder factory can detect the file kind from the PSI, so this call can be slow.
   */
  @RequiresReadLock
  @RequiresBackgroundThread
  fun findBuilder(editor: Editor): TreeBasedStructureViewBuilder? {
    project ?: return null

    val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.document) ?: return null
    return LanguageStructureViewBuilder.getInstance().getStructureViewBuilder(psiFile) as? TreeBasedStructureViewBuilder
  }

  /**
   * Creates the structure view model from [builder] and attaches it to the parent disposable.
   * Returns `null` when the parent disposable is already disposed.
   */
  @RequiresEdt
  fun createModel(editor: Editor, builder: TreeBasedStructureViewBuilder): StructureViewModel? {
    val model = builder.createStructureViewModel(editor)
    if (!Disposer.tryRegister(parentDisposable, model)) {
      Disposer.dispose(model)
      return null
    }
    return model
  }
}
