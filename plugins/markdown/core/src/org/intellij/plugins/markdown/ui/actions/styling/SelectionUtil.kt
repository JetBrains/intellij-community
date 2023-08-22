package org.intellij.plugins.markdown.ui.actions.styling

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.Caret
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import kotlin.math.abs

internal object SelectionUtil {
  data class CaretSnapshot(
    val offset: Int,
    val selectionStart: Int,
    val selectionEnd: Int
  ) {
    val hasSelection
      get() = abs(selectionEnd - selectionStart) > 0
  }

  /**
   * A bridge method to help obtainting information about current editor carets and their selections.
   * Intended to be used only from [AnAction.update] method with action update thread set to [ActionUpdateThread.BGT].
   *
   * @return *unsorted* collection of current carets and their selections.
   */
  @JvmStatic
  @RequiresBackgroundThread
  fun obtainCaretSnapshots(action: AnAction, event: AnActionEvent): Collection<CaretSnapshot>? {
    return event.updateSession.compute(action, "obtainCaretSnapshot", ActionUpdateThread.EDT) {
      val editor = event.getData(CommonDataKeys.EDITOR) ?: return@compute null
      return@compute editor.caretModel.allCarets.map { it.toSnapshot() }
    }
  }

  /**
   * Same as [obtainCaretSnapshots] but only for the primary caret.
   */
  @JvmStatic
  @RequiresBackgroundThread
  fun obtainPrimaryCaretSnapshot(action: AnAction, event: AnActionEvent): CaretSnapshot? {
    return event.updateSession.compute(action, "obtainPrimaryCaretSnapshot", ActionUpdateThread.EDT) {
      val editor = event.getData(CommonDataKeys.EDITOR) ?: return@compute null
      return@compute editor.caretModel.primaryCaret.toSnapshot()
    }
  }

  private fun Caret.toSnapshot(): CaretSnapshot {
    return CaretSnapshot(offset, selectionStart, selectionEnd)
  }
}
