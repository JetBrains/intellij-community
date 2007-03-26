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
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.editor.Editor;

public class ToggleShowWhitespacesAction extends ToggleAction {
  public void setSelected(AnActionEvent e, boolean state) {
    getEditor(e).getSettings().setWhitespacesShown(state);
    getEditor(e).getComponent().repaint();
  }

  public boolean isSelected(AnActionEvent e) {
    Editor editor = getEditor(e);
    return editor != null && editor.getSettings().isWhitespacesShown();
  }

  private static Editor getEditor(AnActionEvent e) {
    return e.getData(DataKeys.EDITOR);
  }

  public void update(AnActionEvent e) {
    if (getEditor(e) == null) {
      e.getPresentation().setEnabled(false);
      e.getPresentation().setVisible(false);
    }
    else {
      e.getPresentation().setEnabled(true);
      e.getPresentation().setVisible(true);
      super.update(e);
    }
  }
}
