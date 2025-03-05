// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.actions

import com.intellij.codeInsight.folding.impl.actions.BaseFoldingHandler
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorAction
import com.jetbrains.python.PythonFoldingBuilder

private class PyCollapseTypeAnnotationsAction : EditorAction(PyTypeAnnotationsFoldingHandler(false))

private class PyExpandTypeAnnotationsAction : EditorAction(PyTypeAnnotationsFoldingHandler(true))

internal class PyTypeAnnotationsFoldingHandler(val expand: Boolean) : BaseFoldingHandler() {
  override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext) {
    val pythonTypeRegions = getFoldRegionsForSelection(editor, caret)
      .filter { it.group != null && it.group.toString() == PythonFoldingBuilder.PYTHON_TYPE_ANNOTATION_GROUP_NAME }
    editor.foldingModel.runBatchFoldingOperation {
      for (region in pythonTypeRegions) {
        region.isExpanded = expand
      }
    }
  }
}