// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.componentTree.ComponentTreeBuilder;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.lw.IComponent;
import com.intellij.uiDesigner.propertyInspector.DesignerToolWindowManager;
import com.intellij.uiDesigner.radComponents.RadComponent;

import java.util.List;

/**
 * @author yole
 */
public class SelectAllComponentsAction extends AbstractGuiEditorAction {
  @Override
  protected void actionPerformed(final GuiEditor editor, final List<RadComponent> selection, final AnActionEvent e) {
    final ComponentTreeBuilder builder = DesignerToolWindowManager.getInstance(editor).getComponentTreeBuilder();
    builder.beginUpdateSelection();
    try {
      FormEditingUtil.iterate(editor.getRootContainer(), new FormEditingUtil.ComponentVisitor() {
        @Override
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