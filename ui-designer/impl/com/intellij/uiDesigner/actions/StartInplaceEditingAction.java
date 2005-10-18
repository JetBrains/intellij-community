package com.intellij.uiDesigner.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.GuiEditor;
import com.intellij.uiDesigner.RadComponent;

import java.util.ArrayList;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class StartInplaceEditingAction extends AnAction{
  private static final Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.actions.StartInplaceEditingAction");

  private final GuiEditor myEditor;

  public StartInplaceEditingAction(final GuiEditor editor) {
    LOG.assertTrue(editor != null);
    myEditor = editor;
  }

  public void actionPerformed(final AnActionEvent e) {
    final ArrayList<RadComponent> selection = FormEditingUtil.getAllSelectedComponents(myEditor);
    final RadComponent component = selection.get(0);

    final int x = component.getX() + component.getWidth() / 2; // central X point
    final int y = component.getY() + component.getHeight() / 2; // central Y point

    myEditor.getInplaceEditingLayer().startInplaceEditing(x, y);
  }

  public void update(final AnActionEvent e) {
    final Presentation presentation = e.getPresentation();
    final ArrayList<RadComponent> selection = FormEditingUtil.getAllSelectedComponents(myEditor);

    // Inplace editing can be started only if single component is selected
    if(selection.size() != 1){
      presentation.setEnabled(false);
      return;
    }

    // Selected component should have "inplace" property
    final RadComponent component = selection.get(0);
    final int x = component.getX() + component.getWidth() / 2; // central X point
    final int y = component.getY() + component.getHeight() / 2; // central Y point
    presentation.setEnabled(component.getInplaceProperty(x, y) != null);
  }
}
