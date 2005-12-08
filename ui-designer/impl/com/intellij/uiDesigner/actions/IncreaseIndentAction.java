package com.intellij.uiDesigner.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.GuiEditorUtil;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.RadComponent;
import com.intellij.uiDesigner.propertyInspector.properties.IndentProperty;

import java.util.ArrayList;

/**
 * @author yole
 */
public class IncreaseIndentAction extends AnAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.actions.IncreaseIndentAction");
  private IndentProperty myIndentProperty = new IndentProperty();

  public void actionPerformed(AnActionEvent e) {
    GuiEditor editor = GuiEditorUtil.getEditorFromContext(e.getDataContext());
    if (editor != null) {
      final ArrayList<RadComponent> selectedComponents = FormEditingUtil.getSelectedComponents(editor);
      RadComponent component = selectedComponents.get(0);
      int indent = ((Integer) myIndentProperty.getValue(component)).intValue();
      try {
        myIndentProperty.setValue(component, new Integer(adjustIndent(indent)));
        editor.refreshAndSave(true);
      }
      catch (Exception ex) {
        LOG.error(ex);
      }
    }
  }

  @Override public void update(AnActionEvent e) {
    GuiEditor editor = GuiEditorUtil.getEditorFromContext(e.getDataContext());
    if (editor != null) {
      final ArrayList<RadComponent> selectedComponents = FormEditingUtil.getSelectedComponents(editor);
      if (selectedComponents.size() == 1 && canAdjustIndent(selectedComponents.get(0))) {
        e.getPresentation().setEnabled(true);
        return;
      }
    }
    e.getPresentation().setEnabled(false);
  }

  protected int adjustIndent(final int indent) {
    return indent + 1;
  }

  protected boolean canAdjustIndent(final RadComponent component) {
    return component.getParent().isGrid();
  }
}
