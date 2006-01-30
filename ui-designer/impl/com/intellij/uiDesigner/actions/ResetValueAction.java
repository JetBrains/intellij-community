package com.intellij.uiDesigner.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.uiDesigner.RadComponent;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.propertyInspector.Property;
import com.intellij.uiDesigner.propertyInspector.PropertyInspectorTable;
import com.intellij.uiDesigner.propertyInspector.UIDesignerToolWindowManager;

import java.util.ArrayList;

/**
 * @author yole
 */
public class ResetValueAction extends AbstractGuiEditorAction {
  private static final Logger LOG = Logger.getInstance("#intellij.uiDesigner.actions.ResetValueAction");

  protected void actionPerformed(final GuiEditor editor, final ArrayList<RadComponent> selection, final AnActionEvent e) {
    final PropertyInspectorTable inspector = (PropertyInspectorTable)e.getDataContext().getData(PropertyInspectorTable.class.getName());
    assert inspector != null;
    final Property property = inspector.getSelectedProperty();
    assert property != null;
    final RadComponent component = selection.get(0);
    doResetValue(component, property, editor);
  }

  public static void doResetValue(final RadComponent component, final Property property, final GuiEditor editor) {
    try {
      if (!editor.ensureEditable()) return;
      property.resetValue(component);
      component.getDelegee().invalidate();
      editor.refreshAndSave(false);
      UIDesignerToolWindowManager.getInstance(editor.getProject()).getPropertyInspector().repaint();
    }
    catch (Exception e1) {
      LOG.error(e1);
    }
  }

  protected void update(final GuiEditor editor, final ArrayList<RadComponent> selection, final AnActionEvent e) {
    PropertyInspectorTable inspector = (PropertyInspectorTable)e.getDataContext().getData(PropertyInspectorTable.class.getName());
    if (inspector != null) {
      final Property selectedProperty = inspector.getSelectedProperty();
      e.getPresentation().setEnabled(selectedProperty != null &&
                                     selection.size() == 1 &&
                                     selectedProperty.isModified(selection.get(0)));
    }
    else {
      e.getPresentation().setEnabled(false);
    }
  }
}
