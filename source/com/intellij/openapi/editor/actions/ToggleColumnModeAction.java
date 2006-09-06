/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: May 14, 2002
 * Time: 7:40:40 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.LogicalPosition;

public class ToggleColumnModeAction extends ToggleAction {

  public void setSelected(AnActionEvent e, boolean state) {
    final EditorEx editor = getEditor(e);
    final SelectionModel selectionModel = editor.getSelectionModel();
    if (state) {
      boolean hasSelection = selectionModel.hasSelection();
      int selStart = selectionModel.getSelectionStart();
      int selEnd = selectionModel.getSelectionEnd();
      final CaretModel caretModel = editor.getCaretModel();
      LogicalPosition blockStart = selStart == caretModel.getOffset()
                                   ? caretModel.getLogicalPosition()
                                   : editor.offsetToLogicalPosition(selStart);
      LogicalPosition blockEnd = selEnd == caretModel.getOffset()
                                 ? caretModel.getLogicalPosition()
                                 : editor.offsetToLogicalPosition(selEnd);
      editor.setColumnMode(true);
      if (hasSelection) {
        selectionModel.setBlockSelection(blockStart, blockEnd);
      }
      else {
        selectionModel.removeSelection();
      }
    }
    else {
      boolean hasSelection = selectionModel.hasBlockSelection();
      int selStart = hasSelection ? editor.logicalPositionToOffset(selectionModel.getBlockStart()) : 0;
      int selEnd = hasSelection ? editor.logicalPositionToOffset(selectionModel.getBlockEnd()) : 0;
      editor.setColumnMode(false);
      if (hasSelection) {
        selectionModel.setSelection(selStart, selEnd);
      }
      else {
        selectionModel.removeSelection();
      }
    }
  }

  public boolean isSelected(AnActionEvent e) {
    final EditorEx ex = getEditor(e);
    return ex != null && ex.isColumnMode();
  }

  private static EditorEx getEditor(AnActionEvent e) {
    return (EditorEx) e.getDataContext().getData(DataConstants.EDITOR);
  }

  public void update(AnActionEvent e){
    if (getEditor(e) == null) {
      e.getPresentation().setEnabled(false);
      e.getPresentation().setVisible(false);
    } else {
      e.getPresentation().setEnabled(true);
      e.getPresentation().setVisible(true);
      super.update(e);
    }
  }
}
