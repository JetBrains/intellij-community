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

public class ToggleColumnModeAction extends ToggleAction {

  public void setSelected(AnActionEvent e, boolean state) {
    if (isSelected(e) != state) {
      getEditor(e).getSelectionModel().removeSelection();
    }
    getEditor(e).setColumnMode(state);
  }

  public boolean isSelected(AnActionEvent e) {
    return getEditor(e).isColumnMode();
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
