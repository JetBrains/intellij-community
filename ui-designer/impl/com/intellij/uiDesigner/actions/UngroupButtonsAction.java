package com.intellij.uiDesigner.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.uiDesigner.RadButtonGroup;
import com.intellij.uiDesigner.RadComponent;
import com.intellij.uiDesigner.RadRootContainer;
import com.intellij.uiDesigner.designSurface.GuiEditor;

import java.util.ArrayList;

/**
 * @author yole
 */
public class UngroupButtonsAction extends AbstractGuiEditorAction {
  protected void actionPerformed(final GuiEditor editor, final ArrayList<RadComponent> selection, final AnActionEvent e) {
    for(RadComponent component: selection) {
      editor.getRootContainer().setGroupForComponent(component, null);
    }
    editor.refreshAndSave(true);
  }

  protected void update(final GuiEditor editor, final ArrayList<RadComponent> selection, final AnActionEvent e) {
    e.getPresentation().setEnabled(canUngroup(editor, selection));
  }

  private static boolean canUngroup(final GuiEditor editor, final ArrayList<RadComponent> selectedComponents) {
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
