// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.uiDesigner.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.designSurface.InsertComponentProcessor;
import com.intellij.uiDesigner.lw.LwSplitPane;
import com.intellij.uiDesigner.palette.ComponentItem;
import com.intellij.uiDesigner.palette.Palette;
import com.intellij.uiDesigner.radComponents.*;
import com.intellij.uiDesigner.shared.XYLayoutManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;


public class SurroundAction extends AbstractGuiEditorAction {
  private static final Logger LOG = Logger.getInstance(SurroundAction.class);

  private final String myComponentClass;

  public SurroundAction(@NlsSafe String componentClass) {
    final String className = componentClass.substring(componentClass.lastIndexOf('.') + 1);
    getTemplatePresentation().setText(className);
    myComponentClass = componentClass;
  }

  @Override
  public void actionPerformed(final GuiEditor editor, final List<? extends RadComponent> input, final AnActionEvent e) {
    // the action is also reused as quickfix for NoScrollPaneInspection, so this code should be kept here
    List<RadComponent> selection = FormEditingUtil.remapToActionTargets(input);
    if (!editor.ensureEditable()) {
      return;
    }
    final RadContainer selectionParent = FormEditingUtil.getSelectionParent(selection);
    assert selectionParent != null;

    final Palette palette = Palette.getInstance(editor.getProject());
    final ComponentItem cItem = palette.getItem(myComponentClass);
    assert cItem != null : myComponentClass;
    CommandProcessor.getInstance().executeCommand(
      editor.getProject(),
      () -> {
        RadContainer newContainer = (RadContainer) InsertComponentProcessor.createInsertedComponent(editor, cItem);
        if (newContainer == null) {
          return;
        }

        if (cItem == palette.getPanelItem()) {
          if (selectionParent.getLayoutManager().isGrid()) {
            try {
              newContainer.setLayoutManager(LayoutManagerRegistry.createLayoutManager(selectionParent.getLayoutManager().getName()));
            }
            catch (Exception e1) {
              LOG.error(e1);
              return;
            }
          }
          else {
            newContainer.setLayoutManager(LayoutManagerRegistry.createDefaultGridLayoutManager(editor.getProject()));
          }
        }

        Rectangle rc = new Rectangle(0, 0, 1, 1);
        int minIndex = Integer.MAX_VALUE;
        if (selectionParent.getLayoutManager().isGrid()) {
          rc = FormEditingUtil.getSelectionBounds(selection);
        }
        else if (selectionParent.getLayoutManager().isIndexed()) {
          for(RadComponent c: selection) {
            minIndex = Math.min(minIndex, selectionParent.indexOfComponent(c));
          }
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
        if (minIndex != Integer.MAX_VALUE) {
          selectionParent.addComponent(newContainer, minIndex);
        }
        else {
          selectionParent.addComponent(newContainer);
        }

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

        // if surrounding a single control with JPanel, 1x1 grid in resulting container is sufficient
        // otherwise, copy column properties and row/col spans
        if (newContainer.getComponentClass().equals(JPanel.class) && selection.size() > 1) {
          if (selectionParent.getLayoutManager().isGrid()) {
            newContainer.getGridLayoutManager().copyGridSection(selectionParent, newContainer, rc);
          }
          else {
            // TODO[yole]: correctly handle surround from indexed
            newContainer.setLayout(new GridLayoutManager(rc.height, rc.width));
          }
        }

        for(RadComponent c: selection) {
          if (selectionParent.getLayoutManager().isGrid()) {
            if (selection.size() > 1) {
              c.getConstraints().setRow(c.getConstraints().getRow() - rc.y);
              c.getConstraints().setColumn(c.getConstraints().getColumn() - rc.x);
            }
            else {
              c.getConstraints().setRow(0);
              c.getConstraints().setColumn(0);
              c.getConstraints().setRowSpan(1);
              c.getConstraints().setColSpan(1);
            }
          }
          newContainer.addComponent(c);
        }
        editor.refreshAndSave(true);
      }, null, null);
  }

  @Override
  protected void update(final @NotNull GuiEditor editor, final ArrayList<? extends RadComponent> input, final AnActionEvent e) {
    List<RadComponent> selection = FormEditingUtil.remapToActionTargets(input);
    RadContainer selectionParent = FormEditingUtil.getSelectionParent(selection);
    Palette palette = Palette.getInstance(editor.getProject());
    e.getPresentation().setEnabled(palette.getItem(myComponentClass) != null &&
                                   (selectionParent != null &&
                                   ((!selectionParent.getLayoutManager().isGrid() && selection.size() == 1) ||
                                     isSelectionContiguous(selectionParent, selection)) &&
                                   canWrapSelection(selection)));
  }

  private boolean canWrapSelection(final List<? extends RadComponent> selection) {
    if (myComponentClass.equals(JScrollPane.class.getName())) {
      if (selection.size() > 1) return false;
      JComponent liveComponent = selection.get(0).getDelegee();
      return liveComponent instanceof Scrollable || liveComponent instanceof JPanel;
    }
    return true;
  }

  private static boolean isSelectionContiguous(RadContainer selectionParent,
                                               List<? extends RadComponent> selection) {
    if (!selectionParent.getLayoutManager().isGrid()) {
      return false;
    }
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
