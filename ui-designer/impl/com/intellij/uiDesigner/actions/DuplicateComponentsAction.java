/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.uiDesigner.*;
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
  public DuplicateComponentsAction() {
    super(true);
  }

  protected void actionPerformed(final GuiEditor editor, final List<RadComponent> selection, final AnActionEvent e) {
    RadContainer parent = GuiEditorUtil.getSelectionParent(selection);
    assert parent != null;
    FormEditingUtil.clearSelection(parent);
    TIntHashSet insertedRows = new TIntHashSet();
    for(RadComponent c: selection) {
      final int row = c.getConstraints().getRow();
      int rowSpan = c.getConstraints().getRowSpan();
      if (!insertedRows.contains(row)) {
        insertedRows.add(row);
        for(int i=0; i<rowSpan; i++) {
          GridChangeUtil.insertRowAfter(parent, row+rowSpan-1);
        }
      }
      List<RadComponent> copyList = CutCopyPasteSupport.copyComponents(editor, Collections.singletonList(c));
      if (copyList != null) {
        RadComponent copy = copyList.get(0);
        copy.getConstraints().setRow(row+rowSpan);
        copy.getConstraints().setRowSpan(rowSpan);
        parent.addComponent(copy);
        copy.setSelected(true);
      }
    }
  }

  protected void update(@NotNull GuiEditor editor, final ArrayList<RadComponent> selection, final AnActionEvent e) {
    final RadContainer parent = GuiEditorUtil.getSelectionParent(selection);
    e.getPresentation().setEnabled(parent != null && parent.isGrid());
    // The action is enabled in any of the following cases:
    // 1) a single component is selected;
    // 2) all selected components have rowspan=1
    // 3) all selected components have the same row and rowspan
    if (selection.size() > 1) {
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
