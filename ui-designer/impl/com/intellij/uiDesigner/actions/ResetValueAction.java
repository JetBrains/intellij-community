package com.intellij.uiDesigner.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.uiDesigner.propertyInspector.Property;
import com.intellij.uiDesigner.propertyInspector.PropertyInspectorTable;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.GuiEditorUtil;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.RadComponent;

import java.util.ArrayList;

/**
 * @author yole
 */
public class ResetValueAction extends AnAction {
  private static final Logger LOG = Logger.getInstance("#intellij.uiDesigner.actions.ResetValueAction");

  public void actionPerformed(AnActionEvent e) {
    final PropertyInspectorTable inspector = (PropertyInspectorTable)e.getDataContext().getData(PropertyInspectorTable.class.getName());
    assert inspector != null;
    final Property property = inspector.getSelectedProperty();
    assert property != null;
    GuiEditor editor = GuiEditorUtil.getEditorFromContext(e.getDataContext());
    assert editor != null;
    final ArrayList<RadComponent> selectedComponents = FormEditingUtil.getSelectedComponents(editor);
    final RadComponent component = selectedComponents.get(0);
    doResetValue(component, property, editor);
  }

  public static void doResetValue(final RadComponent component, final Property property, final GuiEditor editor) {
    try {
      if (!editor.ensureEditable()) return;
      property.resetValue(component);
      component.getDelegee().invalidate();
      editor.refreshAndSave(false);
      editor.getPropertyInspector().repaint();
    }
    catch (Exception e1) {
      LOG.error(e1);
    }
  }

  @Override public void update(AnActionEvent e) {
    GuiEditor editor = GuiEditorUtil.getEditorFromContext(e.getDataContext());
    PropertyInspectorTable inspector = (PropertyInspectorTable)e.getDataContext().getData(PropertyInspectorTable.class.getName());
    if (editor != null && inspector != null) {
      final ArrayList<RadComponent> selectedComponents = FormEditingUtil.getSelectedComponents(editor);
      final Property selectedProperty = inspector.getSelectedProperty();
      e.getPresentation().setEnabled(selectedProperty != null &&
                                     selectedComponents.size() == 1 &&
                                     selectedProperty.isModified(selectedComponents.get(0)));
    }
    else {
      e.getPresentation().setEnabled(false);
    }
  }
}
