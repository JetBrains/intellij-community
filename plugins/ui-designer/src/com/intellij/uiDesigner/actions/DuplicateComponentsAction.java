// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.uiDesigner.CutCopyPasteSupport;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.lw.IProperty;
import com.intellij.uiDesigner.propertyInspector.properties.BindingProperty;
import com.intellij.uiDesigner.propertyInspector.properties.IntroComponentProperty;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.radComponents.RadContainer;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.jetbrains.annotations.NotNull;

import java.util.*;


public final class DuplicateComponentsAction extends AbstractGuiEditorAction {
  private static final Logger LOG = Logger.getInstance(DuplicateComponentsAction.class);

  public DuplicateComponentsAction() {
    super(true);
  }

  @Override
  protected void actionPerformed(final GuiEditor editor, final List<? extends RadComponent> input, final AnActionEvent e) {
    List<RadComponent> selection = FormEditingUtil.remapToActionTargets(input);
    RadContainer parent = FormEditingUtil.getSelectionParent(selection);
    assert parent != null;
    List<RadComponent> duplicates = new ArrayList<>();
    Map<RadComponent, RadComponent> duplicateMap = new HashMap<>();
    IntSet insertedRows = new IntOpenHashSet();
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
          parent.getGridLayoutManager().copyGridCells(parent, parent, incrementRow, row, rowSpan, row + rowSpan);
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
        fillDuplicateMap(duplicateMap, c, copy);
        duplicates.add(copy);
      }
    }
    adjustDuplicates(duplicateMap);
    FormEditingUtil.selectComponents(editor, duplicates);
  }

  private static void fillDuplicateMap(Map<RadComponent, RadComponent> duplicates, final RadComponent c, final RadComponent copy) {
    duplicates.put(c, copy);
    if (c instanceof RadContainer container) {
      LOG.assertTrue(copy instanceof RadContainer);
      final RadContainer containerCopy = (RadContainer)copy;
      for(int i=0; i<container.getComponentCount(); i++) {
        fillDuplicateMap(duplicates, container.getComponent(i), containerCopy.getComponent(i));
      }
    }
  }

  private static void adjustDuplicates(final Map<RadComponent, RadComponent> duplicates) {
    for(RadComponent c: duplicates.keySet()) {
      RadComponent copy = duplicates.get(c);
      if (c.getBinding() != null) {
        String binding = BindingProperty.getDefaultBinding(copy);
        new BindingProperty(c.getProject()).setValueEx(copy, binding);
        copy.setDefaultBinding(true);
      }
      for(IProperty prop: copy.getModifiedProperties()) {
        if (prop instanceof IntroComponentProperty componentProperty) {
          String copyValue = componentProperty.getValue(copy);
          for(RadComponent original: duplicates.keySet()) {
            if (original.getId().equals(copyValue)) {
              componentProperty.setValueEx(copy, duplicates.get(original).getId());
            }
          }
        }
      }
    }
  }

  private static boolean isSpaceBelowEmpty(final RadComponent component, boolean incrementRow) {
    final GridConstraints constraints = component.getConstraints();
    int startRow = constraints.getCell(incrementRow) + constraints.getSpan(incrementRow);
    int endRow = constraints.getCell(incrementRow) + constraints.getSpan(incrementRow)*2 +
                 component.getParent().getGridLayoutManager().getGapCellCount();
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

  @Override
  protected void update(@NotNull GuiEditor editor, final ArrayList<? extends RadComponent> input, final AnActionEvent e) {
    List<RadComponent> selection = FormEditingUtil.remapToActionTargets(input);
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

  @Override
  protected @NotNull String getCommandName() {
    return UIDesignerBundle.message("command.duplicate");
  }
}
