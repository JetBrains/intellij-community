/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.GridChangeUtil;
import com.intellij.uiDesigner.core.GridConstraints;
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
      final RadContainer parent = container.getParent();
      if (container.getLayoutManager().isGrid()) {
        flattenGrid(container);
      }
      else {
        flattenSimple(container);
      }
      parent.revalidate();
    }
  }

  private static void flattenGrid(final RadContainer container) {
    RadContainer parent = container.getParent();
    GridConstraints containerConstraints = (GridConstraints) container.getConstraints().clone();
    // ensure there will be enough rows and columns to fit the container contents
    for(int i=containerConstraints.getRowSpan(); i<container.getGridRowCount(); i++) {
      GridChangeUtil.splitRow(parent, containerConstraints.getRow());
    }
    for(int i=containerConstraints.getColSpan(); i<container.getGridColumnCount(); i++) {
      GridChangeUtil.splitColumn(parent, containerConstraints.getColumn());
    }

    ArrayList<RadComponent> contents = new ArrayList<RadComponent>();
    for(int i=container.getComponentCount()-1; i >= 0; i--) {
      contents.add(0, container.getComponent(i));
      container.removeComponent(container.getComponent(i));
    }

    FormEditingUtil.deleteComponents(Collections.singletonList(container), false);
    for(RadComponent child: contents) {
      final GridConstraints childConstraints = child.getConstraints();
      childConstraints.setRow(childConstraints.getRow() + containerConstraints.getRow());
      childConstraints.setColumn(childConstraints.getColumn() + containerConstraints.getColumn());
      parent.addComponent(child);
      child.revalidate();
    }
  }

  private static void flattenSimple(final RadContainer container) {
    RadContainer parent = container.getParent();
    RadComponent child = null;
    if (container.getComponentCount() == 1) {
      child = container.getComponent(0);
      child.setCustomLayoutConstraints(container.getCustomLayoutConstraints());
      child.getConstraints().restore(container.getConstraints());
    }
    int childIndex = parent.indexOfComponent(container);
    FormEditingUtil.deleteComponents(Collections.singletonList(container), false);
    if (child != null) {
      parent.addComponent(child, childIndex);
      child.revalidate();
    }
  }

  @Override
  protected void update(@NotNull GuiEditor editor, final ArrayList<RadComponent> selection, final AnActionEvent e) {
    for(RadComponent c: selection) {
      if (!canFlatten(c)) {
        e.getPresentation().setVisible(false);
        return;
      }
    }
  }

  private static boolean canFlatten(final RadComponent c) {
    if (!(c instanceof RadContainer)) {
      return false;
    }
    RadContainer container = (RadContainer) c;
    if (container.getLayoutManager().isGrid() && container.getParent().getLayoutManager().isGrid()) {
      return true;
    }
    if (container.getComponentCount() <= 1) {
      return true;
    }
    return false;
  }
}
