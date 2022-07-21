package org.intellij.plugins.markdown.ui.actions.styling

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.Utils
import com.intellij.openapi.editor.CaretState
import com.intellij.openapi.editor.Editor
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
  @RequiresBackgroundThread
  fun obtainCaretSnapshots(action: AnAction, event: AnActionEvent): Collection<CaretSnapshot>? {
    val session = Utils.getOrCreateUpdateSession(event)
    return session.compute(action, "obtainCaretSnapshot", ActionUpdateThread.EDT) {
      val editor = event.getData(CommonDataKeys.EDITOR) ?: return@compute null
      return@compute editor.caretModel.caretsAndSelections.mapNotNull { it.toSnapshot(editor) }
    }
  }

  private fun CaretState.toSnapshot(editor: Editor): CaretSnapshot? {
    val caretPosition = caretPosition ?: return null
    val selectionStart = selectionStart ?: caretPosition
    val selectionEnd = selectionEnd ?: caretPosition
    return CaretSnapshot(
      offset = editor.logicalPositionToOffset(caretPosition),
      selectionStart = editor.logicalPositionToOffset(selectionStart),
      selectionEnd = editor.logicalPositionToOffset(selectionEnd)
    )
  }
}
