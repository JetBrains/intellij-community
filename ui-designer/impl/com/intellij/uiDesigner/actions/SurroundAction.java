/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.uiDesigner.*;
import com.intellij.uiDesigner.shared.XYLayoutManager;
import com.intellij.uiDesigner.radComponents.*;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.designSurface.InsertComponentProcessor;
import com.intellij.uiDesigner.lw.LwSplitPane;
import com.intellij.uiDesigner.palette.ComponentItem;
import com.intellij.uiDesigner.palette.Palette;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * @author yole
 */
public class SurroundAction extends AbstractGuiEditorAction {
  private String myComponentClass;

  public SurroundAction(String componentClass) {
    final String className = componentClass.substring(componentClass.lastIndexOf('.') + 1);
    getTemplatePresentation().setText(className);
    myComponentClass = componentClass;
  }

  public void actionPerformed(final GuiEditor editor, final List<RadComponent> selection, final AnActionEvent e) {
    // the action is also reused as quickfix for NoScrollPaneInspection, so this code should be kept here
    if (!editor.ensureEditable()) {
      return;
    }
    final RadContainer selectionParent = FormEditingUtil.getSelectionParent(selection);
    assert selectionParent != null;

    final Palette palette = Palette.getInstance(editor.getProject());
    final ComponentItem cItem = palette.getItem(myComponentClass);
    assert cItem != null;
    CommandProcessor.getInstance().executeCommand(
      editor.getProject(),
      new Runnable() {
        public void run() {
          RadContainer newContainer = (RadContainer) InsertComponentProcessor.createInsertedComponent(editor, cItem);
          if (newContainer == null) {
            return;
          }

          if (cItem == palette.getPanelItem()) {
            newContainer.setLayoutManager(new RadGridLayoutManager());
          }

          Rectangle rc = new Rectangle(0, 0, 1, 1);
          if (selectionParent.getLayoutManager().isGrid()) {
            rc = FormEditingUtil.getSelectionBounds(selection);
          }
          for(RadComponent c: selection) {
            selectionParent.removeComponent(c);
          }

          if (selectionParent.getLayoutManager().isGrid()) {
            final GridConstraints newConstraints = newContainer.getConstraints();
            newConstraints.setRow(rc.y);
            newConstraints.setColumn(rc.x);
            newConstraints.setRowSpan(rc.height);
            newConstraints.setColSpan(rc.width);
          }
          else if (selectionParent.getLayout() instanceof XYLayoutManager && selection.size() == 1) {
            newContainer.setBounds(selection.get(0).getBounds());
          }

          if (selection.size() == 1) {
            newContainer.setCustomLayoutConstraints(selection.get(0).getCustomLayoutConstraints());
          }
          selectionParent.addComponent(newContainer);

          if (newContainer instanceof RadTabbedPane) {
            // the first tab is created by RadTabbedPane itself
            assert newContainer.getComponentCount() == 1;
            newContainer = (RadContainer) newContainer.getComponent(0);
          }
          else if (newContainer instanceof RadSplitPane) {
            if (selection.size() > 2) {
              RadContainer panel = InsertComponentProcessor.createPanelComponent(editor);
              panel.setCustomLayoutConstraints(LwSplitPane.POSITION_LEFT);
              newContainer.addComponent(panel);
              newContainer = panel;
            }
            else {
              if (selection.size() > 0) {
                selection.get(0).setCustomLayoutConstraints(LwSplitPane.POSITION_LEFT);
              }
              if (selection.size() > 1) {
                selection.get(1).setCustomLayoutConstraints(LwSplitPane.POSITION_RIGHT);
              }
            }
          }

          if (!(newContainer instanceof RadScrollPane) && !(newContainer instanceof RadSplitPane)) {
            newContainer.setLayout(new GridLayoutManager(rc.height, rc.width));
          }

          for(RadComponent c: selection) {
            if (selectionParent.getLayoutManager().isGrid()) {
              c.getConstraints().setRow(c.getConstraints().getRow() - rc.y);
              c.getConstraints().setColumn(c.getConstraints().getColumn() - rc.x);
            }
            newContainer.addComponent(c);
          }
          editor.refreshAndSave(true);
        }
      }, null, null);
  }

  protected void update(final GuiEditor editor, final ArrayList<RadComponent> selection, final AnActionEvent e) {
    RadContainer selectionParent = FormEditingUtil.getSelectionParent(selection);
    e.getPresentation().setEnabled(selectionParent != null &&
                                   ((!selectionParent.getLayoutManager().isGrid() && selection.size() == 1) ||
                                     isSelectionContiguous(selectionParent, selection)) &&
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
    assert selectionParent.getLayoutManager().isGrid();
    Rectangle rc = FormEditingUtil.getSelectionBounds(selection);
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
