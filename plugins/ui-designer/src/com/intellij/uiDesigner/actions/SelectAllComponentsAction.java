package com.intellij.uiDesigner.actions;

import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.propertyInspector.UIDesignerToolWindowManager;
import com.intellij.uiDesigner.componentTree.ComponentTreeBuilder;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.lw.IComponent;
import com.intellij.openapi.actionSystem.AnActionEvent;

import java.util.List;

/**
 * @author yole
 */
public class SelectAllComponentsAction extends AbstractGuiEditorAction {
  protected void actionPerformed(final GuiEditor editor, final List<RadComponent> selection, final AnActionEvent e) {
    final ComponentTreeBuilder builder = UIDesignerToolWindowManager.getInstance(editor.getProject()).getComponentTreeBuilder();
    builder.beginUpdateSelection();
    try {
      FormEditingUtil.iterate(editor.getRootContainer(), new FormEditingUtil.ComponentVisitor() {
        public boolean visit(final IComponent component) {
          ((RadComponent) component).setSelected(true);
          return true;
        }
      });
    }
    finally {
      builder.endUpdateSelection();
    }
  }
}