/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.GridChangeUtil;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.radComponents.RadContainer;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author yole
 */
public class FlattenAction extends AbstractGuiEditorAction {
  public FlattenAction() {
    super(true);
  }

  protected void actionPerformed(final GuiEditor editor, final List<RadComponent> selection, final AnActionEvent e) {
    for(RadComponent c: selection) {
      RadContainer container = (RadContainer) c;
      GridConstraints containerConstraints = (GridConstraints) container.getConstraints().clone();
      GridLayoutManager grid = (GridLayoutManager) container.getLayout();
      // ensure there will be enough rows and columns to fit the container contents
      final RadContainer parent = container.getParent();
      for(int i=containerConstraints.getRowSpan(); i<grid.getRowCount(); i++) {
        GridChangeUtil.splitRow(parent, containerConstraints.getRow());
      }
      for(int i=containerConstraints.getColSpan(); i<grid.getColumnCount(); i++) {
        GridChangeUtil.splitColumn(parent, containerConstraints.getColumn());
      }

      ArrayList<RadComponent> contents = new ArrayList<RadComponent>();
      for(int i=container.getComponentCount()-1; i >= 0; i--) {
        contents.add(0, container.getComponent(i));
        container.removeComponent(container.getComponent(i));
      }

      FormEditingUtil.deleteComponents(editor, Collections.singletonList(container), false);
      for(RadComponent child: contents) {
        final GridConstraints childConstraints = child.getConstraints();
        childConstraints.setRow(childConstraints.getRow() + containerConstraints.getRow());
        childConstraints.setColumn(childConstraints.getColumn() + containerConstraints.getColumn());
        parent.addComponent(child);
        child.revalidate();
      }
      parent.revalidate();
    }
  }

  @Override
  protected void update(@NotNull GuiEditor editor, final ArrayList<RadComponent> selection, final AnActionEvent e) {
    for(RadComponent c: selection) {
      if (!(c instanceof RadContainer)) {
        e.getPresentation().setVisible(false);
        return;
      }
      RadContainer container = (RadContainer) c;
      if (!container.isGrid() || !container.getParent().isGrid()) {
        e.getPresentation().setVisible(false);
        return;
      }
    }
  }
}
