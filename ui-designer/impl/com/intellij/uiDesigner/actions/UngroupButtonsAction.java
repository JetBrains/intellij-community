package com.intellij.uiDesigner.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.*;

import java.util.ArrayList;

/**
 * @author yole
 */
public class UngroupButtonsAction extends AnAction {
  public void actionPerformed(AnActionEvent e) {
    GuiEditor editor = GuiEditorUtil.getEditorFromContext(e.getDataContext());
    if (editor != null) {
      final ArrayList<RadComponent> selectedComponents = FormEditingUtil.getSelectedComponents(editor);
      for(RadComponent component: selectedComponents) {
        editor.getRootContainer().setGroupForComponent(component, null);
      }
      editor.refreshAndSave(true);
    }
  }

  public void update(AnActionEvent e) {
    GuiEditor editor = GuiEditorUtil.getEditorFromContext(e.getDataContext());
    if (editor != null) {
      final ArrayList<RadComponent> selectedComponents = FormEditingUtil.getSelectedComponents(editor);
      e.getPresentation().setEnabled(canUngroup(editor, selectedComponents));
    }
    else {
      e.getPresentation().setVisible(false);
    }
  }

  private boolean canUngroup(final GuiEditor editor, final ArrayList<RadComponent> selectedComponents) {
    if (selectedComponents.size() < 2) {
      return false;
    }
    return isSameGroup(editor, selectedComponents);
  }

  public static boolean isSameGroup(final GuiEditor editor, final ArrayList<RadComponent> selectedComponents) {
    final RadRootContainer rootContainer = editor.getRootContainer();
    RadButtonGroup group = rootContainer.findGroupForComponent(selectedComponents.get(0));
    if (group == null) {
      return false;
    }
    for(int i=1; i<selectedComponents.size(); i++) {
      if (rootContainer.findGroupForComponent(selectedComponents.get(i)) != group) {
        return false;
      }
    }
    return true;
  }
}
