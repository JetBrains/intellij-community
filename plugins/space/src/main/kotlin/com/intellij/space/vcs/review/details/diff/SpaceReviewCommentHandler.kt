// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.vcs.review.details.diff

import com.intellij.diff.FrameDiffTool
import com.intellij.diff.tools.fragmented.UnifiedDiffViewer
import com.intellij.diff.tools.simple.SimpleOnesideDiffViewer
import com.intellij.diff.tools.util.side.TwosideTextDiffViewer
import com.intellij.diff.util.LineRange
import com.intellij.diff.util.Side
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.component1
import com.intellij.openapi.util.component2
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.space.chat.ui.SpaceChatAvatarType
import com.intellij.space.chat.ui.SpaceChatNewMessageWithAvatarComponent
import com.intellij.space.messages.SpaceBundle
import com.intellij.space.stats.SpaceStatsCounterCollector
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.codereview.comment.wrapComponentUsingRoundedPanel
import com.intellij.util.ui.codereview.diff.AddCommentGutterIconRenderer
import com.intellij.util.ui.codereview.diff.DiffEditorGutterIconRendererFactory
import com.intellij.util.ui.codereview.diff.EditorComponentInlaysManager
import com.intellij.util.ui.codereview.diff.EditorRangesController
import com.intellij.util.ui.codereview.timeline.comment.SubmittableTextFieldModelBase
import libraries.coroutines.extra.launch
import runtime.Ui
import javax.swing.JComponent

internal fun createHandler(viewer: FrameDiffTool.DiffViewer,
                           commentSubmitter: SpaceReviewCommentSubmitter): SpaceDiffCommentsHandler = when (viewer) {
  is TwosideTextDiffViewer -> TwoSideDiffCommentsHandler(viewer, commentSubmitter)
  is UnifiedDiffViewer -> UnifiedDiffCommentsHandler(viewer, commentSubmitter)
  is SimpleOnesideDiffViewer -> SimpleOnesideDiffCommentsHandler(viewer, commentSubmitter)
  else -> error("Unsupported DiffViewer")
}

internal abstract class SpaceDiffCommentsHandler {
  abstract fun insertLeft(line: Int, component: JComponent): Disposable?

  abstract fun insertRight(line: Int, component: JComponent): Disposable?

  protected fun insert(manager: EditorComponentInlaysManager, line: Int, component: JComponent): Disposable? {
    val lineCount = manager.editor.document.lineCount
    if (lineCount < line) return null

    return manager.insertAfter(line, component)
  }

  open fun updateCommentableRanges() {}
}

private class SimpleOnesideDiffCommentsHandler(viewer: SimpleOnesideDiffViewer,
                                               commentSubmitter: SpaceReviewCommentSubmitter) : SpaceDiffCommentsHandler() {
  private val manager = EditorComponentInlaysManager(viewer.editor as EditorImpl)

  init {
    val factory = SpaceDiffEditorGutterIconRendererFactory(manager, commentSubmitter) { viewer.side to it }
    SpaceEditorRangesController(factory, viewer.editor)
  }

  override fun insertLeft(line: Int, component: JComponent): Disposable? = insert(manager, line, component)

  override fun insertRight(line: Int, component: JComponent): Disposable? = insertLeft(line, component)
}

private class TwoSideDiffCommentsHandler(viewer: TwosideTextDiffViewer,
                                         commentSubmitter: SpaceReviewCommentSubmitter) : SpaceDiffCommentsHandler() {
  private val leftManager = EditorComponentInlaysManager(viewer.editor1 as EditorImpl)
  private val rightManager = EditorComponentInlaysManager(viewer.editor2 as EditorImpl)

  init {
    val leftFactory = SpaceDiffEditorGutterIconRendererFactory(leftManager, commentSubmitter) { line -> Side.LEFT to line }
    SpaceEditorRangesController(leftFactory, viewer.editor1)
    val rightFactory = SpaceDiffEditorGutterIconRendererFactory(rightManager, commentSubmitter) { line -> Side.RIGHT to line }
    SpaceEditorRangesController(rightFactory, viewer.editor2)
  }

  override fun insertLeft(line: Int, component: JComponent): Disposable? = insert(leftManager, line, component)

  override fun insertRight(line: Int, component: JComponent): Disposable? = insert(rightManager, line, component)
}

private class UnifiedDiffCommentsHandler(private val viewer: UnifiedDiffViewer,
                                         commentSubmitter: SpaceReviewCommentSubmitter) : SpaceDiffCommentsHandler() {
  private val manager = EditorComponentInlaysManager(viewer.editor as EditorImpl)
  private var spaceEditorRangesController: SpaceEditorRangesController? = null

  init {
    val factory = SpaceDiffEditorGutterIconRendererFactory(manager, commentSubmitter) { fileLine ->
      val (indices, side) = viewer.transferLineFromOneside(fileLine)
      val line = side.select(indices).takeIf { it >= 0 } ?: return@SpaceDiffEditorGutterIconRendererFactory null

      side to line
    }
    spaceEditorRangesController = SpaceEditorRangesController(factory, viewer.editor)
  }

  override fun insertLeft(line: Int, component: JComponent): Disposable? {
    val newLine = viewer.transferLineToOneside(Side.LEFT, line)
    return insert(manager, newLine, component)
  }

  override fun insertRight(line: Int, component: JComponent): Disposable? {
    val newLine = viewer.transferLineToOneside(Side.RIGHT, line)
    return insert(manager, newLine, component)
  }

  override fun updateCommentableRanges() {
    spaceEditorRangesController?.updateCommentableRanges()
  }
}

internal class SpaceDiffEditorGutterIconRendererFactory(
  private val inlaysManager: EditorComponentInlaysManager,
  private val commentSubmitter: SpaceReviewCommentSubmitter,
  private val lineLocationCalculator: (Int) -> Pair<Side, Int>?
) : DiffEditorGutterIconRendererFactory {

  override fun createCommentRenderer(line: Int): AddCommentGutterIconRenderer = CreateCommentGutterRenderer(line)

  private inner class CreateCommentGutterRenderer(override val line: Int) : AddCommentGutterIconRenderer() {
    private var inlay: ComponentWithDisposable? = null

    override fun disposeInlay() {
      inlay?.let { Disposer.dispose(it.disposable) }
    }

    override fun getClickAction(): AnAction {
      return InlayAction({ SpaceBundle.message("action.comment.line.text") }, line)
    }

    private inner class InlayAction(actionName: () -> String, private val editorLine: Int) : DumbAwareAction(actionName) {
      override fun actionPerformed(e: AnActionEvent) {
        if (inlay?.let { focusPanel(it.component) } != null) return

        val (side, line) = lineLocationCalculator(editorLine) ?: return

        val hideCallback = {
          inlay?.let { Disposer.dispose(it.disposable) }
          inlay = null
          SpaceStatsCounterCollector.CLOSE_LEAVE_COMMENT.log()
        }
        val component = createComponent(side, line, hideCallback)
        val disposable = inlaysManager.insertAfter(editorLine, component) ?: return
        focusPanel(component)
        inlay = ComponentWithDisposable(component, disposable)

        SpaceStatsCounterCollector.LEAVE_COMMENT.log()
      }

      fun createComponent(side: Side, line: Int, hideCallback: () -> Unit): JComponent {
        val model = object : SubmittableTextFieldModelBase("") {
          override fun submit() {
            launch(commentSubmitter.lifetime, Ui) {
              isBusy = true
              try {
                commentSubmitter.submitComment(side, line, document.text)
                hideCallback()
              }
              catch (e: Exception) {
                error = e
              }
              isBusy = false
            }
          }
        }

        val component = SpaceChatNewMessageWithAvatarComponent(commentSubmitter.lifetime, SpaceChatAvatarType.THREAD, model,
                                                               SpaceStatsCounterCollector.SendMessagePlace.NEW_DISCUSSION,
                                                               hideCallback).apply {
          border = JBUI.Borders.empty(10)
        }

        return wrapComponentUsingRoundedPanel(component).apply {
          border = JBUI.Borders.empty(2, 0)
        }
      }
    }
  }
}

internal data class ComponentWithDisposable(val component: JComponent, val disposable: Disposable)

internal class SpaceEditorRangesController(
  factory: SpaceDiffEditorGutterIconRendererFactory,
  private val editor: EditorEx
) : EditorRangesController(factory, editor) {
  init {
    updateCommentableRanges()
  }

  fun updateCommentableRanges() {
    // all lines are commentable in Space review
    markCommentableLines(LineRange(0, editor.document.lineCount))
  }
}

private fun focusPanel(panel: JComponent) {
  val focusManager = IdeFocusManager.findInstanceByComponent(panel)
  val toFocus = focusManager.getFocusTargetFor(panel) ?: return
  focusManager.doWhenFocusSettlesDown { focusManager.requestFocus(toFocus, true) }
}

