/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.uiDesigner.CutCopyPasteSupport;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.propertyInspector.properties.BindingProperty;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.radComponents.RadContainer;
import gnu.trove.TIntHashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
    remapToActionTargets(selection);
    RadContainer parent = FormEditingUtil.getSelectionParent(selection);
    assert parent != null;
    List<RadComponent> duplicates = new ArrayList<RadComponent>();
    TIntHashSet insertedRows = new TIntHashSet();
    boolean incrementRow = true;
    if (selection.size() > 1 && canDuplicate(selection, false) && FormEditingUtil.getSelectionBounds(selection).width == 1) {
      incrementRow = false;
    }
    for(RadComponent c: selection) {
      final int row = c.getConstraints().getCell(incrementRow);
      int rowSpan = c.getConstraints().getSpan(incrementRow);
      int insertIndex = parent.indexOfComponent(c);
      if (parent.getLayoutManager().isGrid()) {
        if (!insertedRows.contains(row) && !isSpaceBelowEmpty(c, incrementRow)) {
          insertedRows.add(row);
          parent.getGridLayoutManager().copyGridCells(parent, incrementRow, row, rowSpan, row + rowSpan);
        }
      }

      List<RadComponent> copyList = CutCopyPasteSupport.copyComponents(editor, Collections.singletonList(c));
      if (copyList != null) {
        RadComponent copy = copyList.get(0);
        if (parent.getLayoutManager().isGrid()) {
          copy.getConstraints().setCell(incrementRow, row + rowSpan + parent.getGridLayoutManager().getGapCellCount());
          copy.getConstraints().setSpan(incrementRow, rowSpan);
        }
        parent.addComponent(copy, insertIndex+1);
        copyBinding(c, copy);
        duplicates.add(copy);
      }
    }
    FormEditingUtil.selectComponents(editor, duplicates);
  }

  private static void remapToActionTargets(final List<RadComponent> selection) {
    for(int i=0; i<selection.size(); i++) {
      final RadComponent c = selection.get(i);
      if (c.getParent() != null) {
        selection.set(i, c.getParent().getActionTargetComponent(c));
      }
    }
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

  private static boolean isSpaceBelowEmpty(final RadComponent component, boolean incrementRow) {
    final GridConstraints constraints = component.getConstraints();
    int startRow = constraints.getCell(incrementRow) + constraints.getSpan(incrementRow);
    int endRow = constraints.getCell(incrementRow) + constraints.getSpan(incrementRow)*2;
    if (endRow > component.getParent().getGridCellCount(incrementRow)) {
      return false;
    }
    for(int row=startRow; row < endRow; row++) {
      for(int col=constraints.getCell(!incrementRow); col < constraints.getCell(!incrementRow) + constraints.getSpan(!incrementRow); col++) {
        if (component.getParent().getComponentAtGrid(incrementRow, row, col) != null) {
          return false;
        }
      }
    }
    return true;
  }

  protected void update(@NotNull GuiEditor editor, final ArrayList<RadComponent> selection, final AnActionEvent e) {
    remapToActionTargets(selection);
    final RadContainer parent = FormEditingUtil.getSelectionParent(selection);
    e.getPresentation().setEnabled(parent != null && (parent.getLayoutManager().isGrid() || parent.getLayoutManager().isIndexed()));
    // The action is enabled in any of the following cases:
    // 1) a single component is selected;
    // 2) all selected components have rowspan=1
    // 3) all selected components have the same row and rowspan
    if (selection.size() > 1 && parent != null && parent.getLayoutManager().isGrid()) {
      e.getPresentation().setEnabled(canDuplicate(selection, true) || canDuplicate(selection, false));
    }
  }

  private static boolean canDuplicate(final List<RadComponent> selection, final boolean incrementRow) {
    int aRow = selection.get(0).getConstraints().getCell(incrementRow);
    int aRowSpan = selection.get(0).getConstraints().getSpan(incrementRow);
    for(int i=1; i<selection.size(); i++) {
      final RadComponent c = selection.get(i);
      if (c.getConstraints().getSpan(incrementRow) > 1 || aRowSpan > 1) {
        if (c.getConstraints().getCell(incrementRow) != aRow || c.getConstraints().getSpan(incrementRow) != aRowSpan) {
          return false;
        }
      }
    }
    return true;
  }

  @Override @Nullable
  protected String getCommandName() {
    return UIDesignerBundle.message("command.duplicate");
  }
}
