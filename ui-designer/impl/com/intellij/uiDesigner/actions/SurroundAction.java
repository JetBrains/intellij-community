/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.uiDesigner.*;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.designSurface.InsertComponentProcessor;
import com.intellij.uiDesigner.lw.LwSplitPane;
import com.intellij.uiDesigner.palette.ComponentItem;
import com.intellij.uiDesigner.palette.Palette;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;

/**
 * @author yole
 */
public class SurroundAction extends AbstractGuiEditorAction {
  private String myComponentClass;

  public SurroundAction(String componentClass) {
    final String className = componentClass.substring(componentClass.lastIndexOf('.') + 1);
    getTemplatePresentation().setText(UIDesignerBundle.message("action.surround.with", className));
    myComponentClass = componentClass;
  }

  protected void actionPerformed(final GuiEditor editor, final ArrayList<RadComponent> selection, final AnActionEvent e) {
    if (!editor.ensureEditable()) {
      return;
    }
    final RadContainer selectionParent = getSelectionParent(selection);
    assert selectionParent != null;

    final Palette palette = Palette.getInstance(editor.getProject());
    final ComponentItem cItem = palette.getItem(myComponentClass);
    assert cItem != null;
    CommandProcessor.getInstance().executeCommand(
      editor.getProject(),
      new Runnable() {
        public void run() {
          Rectangle rc = getSelectionBounds(selection);

          for(RadComponent c: selection) {
            selectionParent.removeComponent(c);
          }

          RadContainer newContainer = (RadContainer)InsertComponentProcessor.createInsertedComponent(editor, cItem);

          final GridConstraints newConstraints = newContainer.getConstraints();
          newConstraints.setRow(rc.y);
          newConstraints.setColumn(rc.x);
          newConstraints.setRowSpan(rc.height);
          newConstraints.setColSpan(rc.width);
          selectionParent.addComponent(newContainer);

          if (newContainer instanceof RadTabbedPane ||
              newContainer instanceof RadSplitPane ||
              newContainer instanceof RadScrollPane) {
            RadContainer panel = (RadContainer) InsertComponentProcessor.createInsertedComponent(editor, palette.getPanelItem());
            if (newContainer instanceof RadSplitPane) {
              panel.setCustomLayoutConstraints(LwSplitPane.POSITION_LEFT);
            }
            newContainer.addComponent(panel);
            newContainer = panel;
          }

          newContainer.setLayout(new GridLayoutManager(rc.height, rc.width));

          for(RadComponent c: selection) {
            c.getConstraints().setRow(c.getConstraints().getRow() - rc.y);
            c.getConstraints().setColumn(c.getConstraints().getColumn() - rc.x);
            newContainer.addComponent(c);
          }
          editor.refreshAndSave(true);
        }
      }, null, null);
  }

  @Nullable private static RadContainer getSelectionParent(final ArrayList<RadComponent> selection) {
    RadContainer parent = null;
    for(RadComponent c: selection) {
      if (parent == null) {
        parent = c.getParent();
      }
      else if (parent != c.getParent()) {
        parent = null;
        break;
      }
    }
    return parent;
  }

  private static Rectangle getSelectionBounds(ArrayList<RadComponent> selection) {
    int minRow = Integer.MAX_VALUE;
    int minCol = Integer.MAX_VALUE;
    int maxRow = 0;
    int maxCol = 0;

    for(RadComponent c: selection) {
      minRow = Math.min(minRow, c.getConstraints().getRow());
      minCol = Math.min(minCol, c.getConstraints().getColumn());
      maxRow = Math.max(maxRow, c.getConstraints().getRow() + c.getConstraints().getRowSpan());
      maxCol = Math.max(maxCol, c.getConstraints().getColumn() + c.getConstraints().getColSpan());
    }
    return new Rectangle(minCol, minRow, maxCol-minCol, maxRow-minRow);
  }

  protected void update(final GuiEditor editor, final ArrayList<RadComponent> selection, final AnActionEvent e) {
    RadContainer selectionParent = getSelectionParent(selection);
    e.getPresentation().setEnabled(selectionParent != null &&
        (selectionParent instanceof RadRootContainer || selectionParent.isGrid()) &&
        isSelectionContiguous(selectionParent, selection) &&
        canWrapSelection(selection));
  }

  private boolean canWrapSelection(final ArrayList<RadComponent> selection) {
    if (myComponentClass.equals(JScrollPane.class.getName())) {
      if (selection.size() > 1) return false;
      RadComponent component = selection.get(0);
      return component.getDelegee() instanceof Scrollable;
    }
    return true;
  }

  private static boolean isSelectionContiguous(RadContainer selectionParent,
                                               ArrayList<RadComponent> selection) {
    Rectangle rc = getSelectionBounds(selection);
    for(RadComponent c: selectionParent.getComponents()) {
      if (!selection.contains(c) &&
          constraintsIntersect(true, c.getConstraints(), rc) &&
          constraintsIntersect(false, c.getConstraints(), rc)) {
        return false;
      }
    }
    return true;
  }

  private static boolean constraintsIntersect(boolean horizontal,
                                              GridConstraints constraints,
                                              Rectangle rc) {
    int start = constraints.getCell(!horizontal);
    int end = start + constraints.getSpan(!horizontal) - 1;
    int otherStart = horizontal ? rc.x : rc.y;
    int otherEnd = otherStart + (horizontal ? rc.width : rc.height) - 1;
    return start <= otherEnd && otherStart <= end;
  }
}
