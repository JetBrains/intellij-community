package com.intellij.uiDesigner.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.lw.IButtonGroup;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.radComponents.RadRootContainer;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class UngroupButtonsAction extends AbstractGuiEditorAction {
  protected UngroupButtonsAction() {
    super(true);
  }

  protected void actionPerformed(final GuiEditor editor, final List<RadComponent> selection, final AnActionEvent e) {
    for(RadComponent component: selection) {
      editor.getRootContainer().setGroupForComponent(component, null);
    }
  }

  protected void update(@NotNull final GuiEditor editor, final ArrayList<RadComponent> selection, final AnActionEvent e) {
    boolean visible = GroupButtonsAction.allButtons(selection);
    e.getPresentation().setVisible(visible);
    e.getPresentation().setEnabled(visible && canUngroup(editor, selection));
  }

  private static boolean canUngroup(final GuiEditor editor, final ArrayList<RadComponent> selectedComponents) {
    if (selectedComponents.size() < 2) {
      return false;
    }
    return isSameGroup(editor, selectedComponents);
  }

  public static boolean isSameGroup(final GuiEditor editor, final ArrayList<RadComponent> selectedComponents) {
    final RadRootContainer rootContainer = editor.getRootContainer();
    IButtonGroup group = FormEditingUtil.findGroupForComponent(rootContainer, selectedComponents.get(0));
    if (group == null) {
      return false;
    }
    for(int i=1; i<selectedComponents.size(); i++) {
      if (FormEditingUtil.findGroupForComponent(rootContainer, selectedComponents.get(i)) != group) {
        return false;
      }
    }
    return true;
  }
}
