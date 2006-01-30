package com.intellij.uiDesigner.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.uiDesigner.RadComponent;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.propertyInspector.properties.IndentProperty;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

/**
 * @author yole
 */
public class IncreaseIndentAction extends AbstractGuiEditorAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.actions.IncreaseIndentAction");
  private IndentProperty myIndentProperty = new IndentProperty();

  protected void actionPerformed(final GuiEditor editor, final ArrayList<RadComponent> selection, final AnActionEvent e) {
    RadComponent component = selection.get(0);
    int indent = ((Integer) myIndentProperty.getValue(component)).intValue();
    try {
      myIndentProperty.setValue(component, adjustIndent(indent));
      editor.refreshAndSave(true);
    }
    catch (Exception ex) {
      LOG.error(ex);
    }
  }

  protected void update(@NotNull GuiEditor editor, final ArrayList<RadComponent> selection, final AnActionEvent e) {
    e.getPresentation().setEnabled(selection.size() == 1 && canAdjustIndent(selection.get(0)));
  }

  protected int adjustIndent(final int indent) {
    return indent + 1;
  }

  protected boolean canAdjustIndent(final RadComponent component) {
    return component.getParent().isGrid();
  }
}
