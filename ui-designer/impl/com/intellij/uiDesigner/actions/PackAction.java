package com.intellij.uiDesigner.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.GuiEditorUtil;
import com.intellij.uiDesigner.RadComponent;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.RadContainer;

import java.util.ArrayList;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 15.11.2005
 * Time: 13:48:24
 * To change this template use File | Settings | File Templates.
 */
public class PackAction extends AnAction {
  public void actionPerformed(AnActionEvent e) {
    final GuiEditor editor = GuiEditorUtil.getEditorFromContext(e.getDataContext());
    RadContainer container = getContainerToPack(editor);
    if (container != null) {
      container.getDelegee().setSize(container.getMinimumSize());
      container.getDelegee().revalidate();
    }
  }

  @Override
  public void update(AnActionEvent e) {
    final GuiEditor editor = GuiEditorUtil.getEditorFromContext(e.getDataContext());
    if (editor == null || getContainerToPack(editor) == null) {
      e.getPresentation().setEnabled(false);
    }
  }

  private RadContainer getContainerToPack(final GuiEditor editor) {
    ArrayList<RadComponent> selection = FormEditingUtil.getSelectedComponents(editor);
    if (selection.size() != 1 || !(selection.get(0) instanceof RadContainer)) {
      return null;
    }

    RadContainer container = (RadContainer)selection.get(0);
    if (!container.getParent().isXY()) {
      return null;
    }
    return container;
  }
}
