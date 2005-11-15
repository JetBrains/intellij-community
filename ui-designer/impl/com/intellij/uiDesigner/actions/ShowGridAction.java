package com.intellij.uiDesigner.actions;

import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.GuiEditorUtil;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 15.11.2005
 * Time: 18:33:22
 * To change this template use File | Settings | File Templates.
 */
public class ShowGridAction extends ToggleAction {
  public void update(final AnActionEvent e) {
    super.update(e);
    GuiEditor editor = GuiEditorUtil.getEditorFromContext(e.getDataContext());
    e.getPresentation().setEnabled(editor != null);
  }

  public boolean isSelected(AnActionEvent e) {
    GuiEditor editor = GuiEditorUtil.getEditorFromContext(e.getDataContext());
    return editor != null && editor.isShowGrid();
  }

  public void setSelected(AnActionEvent e, boolean state) {
    GuiEditor editor = GuiEditorUtil.getEditorFromContext(e.getDataContext());
    if (editor != null) {
      editor.setShowGrid(state);
    }
  }
}
