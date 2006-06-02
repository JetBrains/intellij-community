package com.intellij.uiDesigner.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.propertyInspector.properties.IndentProperty;
import com.intellij.uiDesigner.radComponents.RadComponent;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class IncreaseIndentAction extends AbstractGuiEditorAction {
  private IndentProperty myIndentProperty = new IndentProperty();

  public IncreaseIndentAction() {
    super(true);
  }

  protected void actionPerformed(final GuiEditor editor, final List<RadComponent> selection, final AnActionEvent e) {
    for(RadComponent c: selection) {
      int indent = myIndentProperty.getValue(c).intValue();
      myIndentProperty.setValueEx(c, adjustIndent(indent));
    }
  }

  protected void update(@NotNull GuiEditor editor, final ArrayList<RadComponent> selection, final AnActionEvent e) {
    e.getPresentation().setVisible(canAdjustIndent(selection));
  }

  private boolean canAdjustIndent(final ArrayList<RadComponent> selection) {
    for(RadComponent c: selection) {
      if (canAdjustIndent(c)) {
        return true;
      }
    }
    return false;
  }

  protected int adjustIndent(final int indent) {
    return indent + 1;
  }

  protected boolean canAdjustIndent(final RadComponent component) {
    return component.getParent().getLayout() instanceof GridLayoutManager;
  }
}
