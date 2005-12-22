package com.intellij.uiDesigner.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.*;

import javax.swing.*;
import java.util.ArrayList;

/**
 * @author yole
 */
public class GroupButtonsAction extends AnAction {
  public void actionPerformed(AnActionEvent e) {
    GuiEditor editor = GuiEditorUtil.getEditorFromContext(e.getDataContext());
    if (editor != null) {
      ArrayList<RadComponent> selectedComponents = FormEditingUtil.getSelectedComponents(editor);
      groupButtons(editor, selectedComponents);
    }
  }

  public static void groupButtons(final GuiEditor editor, final ArrayList<RadComponent> selectedComponents) {
    RadRootContainer rootContainer = editor.getRootContainer();
    RadButtonGroup group = rootContainer.createGroup(rootContainer.suggestGroupName());
    for(RadComponent component: selectedComponents) {
      rootContainer.setGroupForComponent(component, group);
    }
    editor.refreshAndSave(true);
  }

  @Override public void update(AnActionEvent e) {
    GuiEditor editor = GuiEditorUtil.getEditorFromContext(e.getDataContext());
    if (editor != null) {
      final ArrayList<RadComponent> selectedComponents = FormEditingUtil.getSelectedComponents(editor);
      e.getPresentation().setEnabled(canGroup(selectedComponents) &&
                                     !UngroupButtonsAction.isSameGroup(editor, selectedComponents));
    }
    else {
      e.getPresentation().setVisible(false);
    }
  }

  private boolean canGroup(final ArrayList<RadComponent> selectedComponents) {
    if (selectedComponents.size() < 2) {
      return false;
    }
    for(RadComponent component: selectedComponents) {
      if (!(component.getDelegee() instanceof AbstractButton) ||
          component.getDelegee() instanceof JButton) {
        return false;
      }
    }
    return true;
  }
}
