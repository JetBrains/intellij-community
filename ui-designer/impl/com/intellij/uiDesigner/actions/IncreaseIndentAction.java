package com.intellij.uiDesigner.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.propertyInspector.properties.IndentProperty;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class IncreaseIndentAction extends AbstractGuiEditorAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.actions.IncreaseIndentAction");
  private IndentProperty myIndentProperty = new IndentProperty();

  public IncreaseIndentAction() {
    super(true);
  }

  protected void actionPerformed(final GuiEditor editor, final List<RadComponent> selection, final AnActionEvent e) {
    for(RadComponent c: selection) {
      int indent = ((Integer) myIndentProperty.getValue(c)).intValue();
      try {
        myIndentProperty.setValue(c, adjustIndent(indent));
      }
      catch (Exception ex) {
        LOG.error(ex);
      }
    }
  }

  protected void update(@NotNull GuiEditor editor, final ArrayList<RadComponent> selection, final AnActionEvent e) {
    e.getPresentation().setEnabled(canAdjustIndent(selection));
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
    return component.getParent().getLayoutManager().isGrid();
  }
}
