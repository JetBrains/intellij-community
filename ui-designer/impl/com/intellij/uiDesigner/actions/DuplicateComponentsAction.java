/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.uiDesigner.*;
import com.intellij.uiDesigner.propertyInspector.properties.BindingProperty;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.radComponents.RadContainer;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import gnu.trove.TIntHashSet;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author yole
 */
public class DuplicateComponentsAction extends AbstractGuiEditorAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.actions.DuplicateComponentsAction");

  public DuplicateComponentsAction() {
    super(true);
  }

  protected void actionPerformed(final GuiEditor editor, final List<RadComponent> selection, final AnActionEvent e) {
    RadContainer parent = FormEditingUtil.getSelectionParent(selection);
    assert parent != null;
    List<RadComponent> duplicates = new ArrayList<RadComponent>();
    TIntHashSet insertedRows = new TIntHashSet();
    for(RadComponent c: selection) {
      final int row = c.getConstraints().getRow();
      int rowSpan = c.getConstraints().getRowSpan();
      int insertIndex = parent.indexOfComponent(c);

      if (parent.isGrid()) {
        if (!insertedRows.contains(row) && !isSpaceBelowEmpty(c)) {
          insertedRows.add(row);
          for(int i=0; i<rowSpan; i++) {
            GridChangeUtil.insertRowAfter(parent, row+rowSpan-1);
          }
        }
      }

      List<RadComponent> copyList = CutCopyPasteSupport.copyComponents(editor, Collections.singletonList(c));
      if (copyList != null) {
        RadComponent copy = copyList.get(0);
        if (parent.isGrid()) {
          copy.getConstraints().setRow(row+rowSpan);
          copy.getConstraints().setRowSpan(rowSpan);
        }
        parent.addComponent(copy, insertIndex+1);
        copyBinding(c, copy);
        duplicates.add(copy);
      }
    }
    FormEditingUtil.selectComponents(duplicates);
  }

  private static void copyBinding(final RadComponent c, final RadComponent copy) {
    if (c.getBinding() != null) {
      String binding = BindingProperty.getDefaultBinding(copy);
      try {
        new BindingProperty(c.getProject()).setValue(copy, binding);
      }
      catch (Exception e1) {
        LOG.error(e1);
      }
      copy.setDefaultBinding(true);
    }
  }

  private static boolean isSpaceBelowEmpty(final RadComponent component) {
    GridLayoutManager layout = (GridLayoutManager) component.getParent().getLayout();
    final GridConstraints constraints = component.getConstraints();
    int startRow = constraints.getRow() + constraints.getRowSpan();
    int endRow = constraints.getRow() + constraints.getRowSpan()*2;
    if (endRow > layout.getRowCount()) {
      return false;
    }
    for(int row=startRow; row < endRow; row++) {
      for(int col=constraints.getColumn(); col < constraints.getColumn() + constraints.getColSpan(); col++) {
        if (component.getParent().getComponentAtGrid(row, col) != null) {
          return false;
        }
      }
    }
    return true;
  }

  protected void update(@NotNull GuiEditor editor, final ArrayList<RadComponent> selection, final AnActionEvent e) {
    final RadContainer parent = FormEditingUtil.getSelectionParent(selection);
    e.getPresentation().setEnabled(parent != null && (parent.isGrid() || parent.getLayoutManager().isIndexed()));
    // The action is enabled in any of the following cases:
    // 1) a single component is selected;
    // 2) all selected components have rowspan=1
    // 3) all selected components have the same row and rowspan
    if (selection.size() > 1 && parent != null && parent.isGrid()) {
      int aRow = selection.get(0).getConstraints().getRow();
      int aRowSpan = selection.get(0).getConstraints().getRowSpan();
      for(int i=1; i<selection.size(); i++) {
        final RadComponent c = selection.get(i);
        if (c.getConstraints().getRowSpan() > 1 || aRowSpan > 1) {
          if (c.getConstraints().getRow() != aRow || c.getConstraints().getRowSpan() != aRowSpan) {
            e.getPresentation().setEnabled(false);
            break;
          }
        }
      }
    }
  }
}
