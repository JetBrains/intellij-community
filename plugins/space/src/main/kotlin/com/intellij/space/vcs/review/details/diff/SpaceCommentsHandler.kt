// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.vcs.review.details.diff

import com.intellij.diff.FrameDiffTool
import com.intellij.diff.tools.fragmented.UnifiedDiffViewer
import com.intellij.diff.tools.simple.SimpleOnesideDiffViewer
import com.intellij.diff.tools.util.side.TwosideTextDiffViewer
import com.intellij.diff.util.Side
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.util.ui.codereview.diff.EditorComponentInlaysManager
import javax.swing.JComponent

internal fun createHandler(viewer: FrameDiffTool.DiffViewer): SpaceDiffCommentsHandler = when (viewer) {
  is TwosideTextDiffViewer -> TwoSideDiffCommentsHandler(viewer)
  is UnifiedDiffViewer -> UnifiedDiffCommentsHandler(viewer)
  is SimpleOnesideDiffViewer -> SimpleOnesideDiffCommentsHandler(viewer)
  else -> error("unsupported")
}

internal abstract class SpaceDiffCommentsHandler {
  abstract fun insertLeft(line: Int, component: JComponent): Disposable?

  abstract fun insertRight(line: Int, component: JComponent): Disposable?

  protected fun insert(manager: EditorComponentInlaysManager, line: Int, component: JComponent): Disposable? {
    return manager.insertAfter(line, component)
  }
}

private class SimpleOnesideDiffCommentsHandler(viewer: SimpleOnesideDiffViewer) : SpaceDiffCommentsHandler() {
  private val manager = EditorComponentInlaysManager(viewer.editor as EditorImpl)

  override fun insertLeft(line: Int, component: JComponent): Disposable? = insert(manager, line, component)

  override fun insertRight(line: Int, component: JComponent): Disposable? = insertLeft(line, component)
}

private class TwoSideDiffCommentsHandler(viewer: TwosideTextDiffViewer) : SpaceDiffCommentsHandler() {
  private val leftManager = EditorComponentInlaysManager(viewer.editor1 as EditorImpl)
  private val rightManager = EditorComponentInlaysManager(viewer.editor2 as EditorImpl)

  override fun insertLeft(line: Int, component: JComponent): Disposable? = insert(leftManager, line, component)

  override fun insertRight(line: Int, component: JComponent): Disposable? = insert(rightManager, line, component)
}

private class UnifiedDiffCommentsHandler(private val viewer: UnifiedDiffViewer) : SpaceDiffCommentsHandler() {
  private val manager = EditorComponentInlaysManager(viewer.editor as EditorImpl)

  override fun insertLeft(line: Int, component: JComponent): Disposable? {
    val newLine = viewer.transferLineToOneside(Side.LEFT, line)
    return insert(manager, newLine, component)
  }

  override fun insertRight(line: Int, component: JComponent): Disposable? {
    val newLine = viewer.transferLineToOneside(Side.RIGHT, line)
    return insert(manager, newLine, component)
  }
}