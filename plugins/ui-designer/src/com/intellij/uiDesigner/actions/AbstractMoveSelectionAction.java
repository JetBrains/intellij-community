// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.Ref;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.radComponents.RadAtomicComponent;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.radComponents.RadContainer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;

@ApiStatus.Internal
public abstract class AbstractMoveSelectionAction extends AnAction implements DumbAware {
  private static final Logger LOG = Logger.getInstance(MoveSelectionToRightAction.class);

  private final GuiEditor myEditor;
  private final boolean myExtend;
  private final boolean myMoveToLast;

  AbstractMoveSelectionAction(final @NotNull GuiEditor editor, boolean extend, final boolean moveToLast) {
    myEditor = editor;
    myExtend = extend;
    myMoveToLast = moveToLast;
  }

  @Override
  public final void actionPerformed(final @NotNull AnActionEvent e) {
    final ArrayList<RadComponent> selectedComponents = FormEditingUtil.getSelectedComponents(myEditor);
    final JComponent rootContainerDelegee = myEditor.getRootContainer().getDelegee();
    if(selectedComponents.isEmpty()){
      moveToFirstComponent(rootContainerDelegee);
      return;
    }
    RadComponent selectedComponent = myEditor.getSelectionLead();
    if (selectedComponent == null || !selectedComponents.contains(selectedComponent)) {
      selectedComponent = selectedComponents.get(0);
    }

    if (moveSelectionByGrid(selectedComponent) || myMoveToLast) {
      return;
    }

    // 1. We need to get coordinates of all editor's component in the same
    // coordinate system. For example, in the RadRootContainer rootContainerDelegee's coordinate system.

    final ArrayList<RadComponent> components = new ArrayList<>();
    final ArrayList<Point> points = new ArrayList<>();
    final RadComponent selectedComponent1 = selectedComponent;
    FormEditingUtil.iterate(
      myEditor.getRootContainer(),
      new FormEditingUtil.ComponentVisitor<RadComponent>() {
        @Override
        public boolean visit(final RadComponent component) {
          if (component instanceof RadAtomicComponent) {
            if(selectedComponent1.equals(component)){
              return true;
            }
            if (!FormEditingUtil.isComponentSwitchedInView(component)) {
              return true;
            }
            components.add(component);
            final JComponent _delegee = component.getDelegee();
            final Point p = SwingUtilities.convertPoint(
              _delegee,
              new Point(0, 0),
              rootContainerDelegee
            );
            p.x += _delegee.getWidth() / 2;
            p.y += _delegee.getHeight() / 2;
            points.add(p);
          }
          return true;
        }
      }
    );
    if(components.isEmpty()){
      return;
    }

    // 2.
    final Point source = SwingUtilities.convertPoint(
      selectedComponent.getDelegee(),
      new Point(0, 0),
      rootContainerDelegee
    );
    source.x += selectedComponent.getDelegee().getWidth() / 2;
    source.y += selectedComponent.getDelegee().getHeight() / 2;
    int min = Integer.MAX_VALUE;
    int nextSelectedIndex = -1;
    for(int i = points.size() - 1; i >= 0; i--){
      final int distance = calcDistance(source, points.get(i));
      if(distance < min){
        min = distance;
        nextSelectedIndex = i;
      }
    }
    if(min == Integer.MAX_VALUE){
      return;
    }

    final RadComponent component = components.get(nextSelectedIndex);
    selectOrExtend(component);
  }

  private void selectOrExtend(final RadComponent component) {
    if (myExtend) {
      FormEditingUtil.selectComponent(myEditor, component);
    }
    else {
      FormEditingUtil.selectSingleComponent(myEditor, component);
    }
  }

  private void moveToFirstComponent(final JComponent rootContainerDelegee) {
    final int[] minX = new int[]{Integer.MAX_VALUE};
    final int[] minY = new int[]{Integer.MAX_VALUE};
    final Ref<RadComponent> componentToBeSelected = new Ref<>();
    FormEditingUtil.iterate(
      myEditor.getRootContainer(),
      new FormEditingUtil.ComponentVisitor<RadComponent>() {
        @Override
        public boolean visit(final RadComponent component) {
          if (component instanceof RadAtomicComponent) {
            final JComponent _delegee = component.getDelegee();
            final Point p = SwingUtilities.convertPoint(
              _delegee,
              new Point(0, 0),
              rootContainerDelegee
            );
            if(minX[0] > p.x || minY[0] > p.y){
              minX[0] = p.x;
              minY[0] = p.y;
              componentToBeSelected.set(component);
            }
          }
          return true;
        }
      }
    );
    if(!componentToBeSelected.isNull()){
      FormEditingUtil.selectComponent(myEditor, componentToBeSelected.get());
    }
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabled(!myEditor.getMainProcessor().isProcessorActive());
  }

  private boolean moveSelectionByGrid(final RadComponent selectedComponent) {
    final RadContainer parent = selectedComponent.getParent();
    if (parent == null || !parent.getLayoutManager().isGrid()) {
      return false;
    }

    int row = selectedComponent.getConstraints().getRow();
    int column = selectedComponent.getConstraints().getColumn();

    RadComponent lastComponent = null;
    do {
      row += getRowMoveDelta();
      column += getColumnMoveDelta();
      if (row < 0 || row >= parent.getGridRowCount() || column < 0 || column >= parent.getGridColumnCount()) {
        if (myMoveToLast) {
          break;
        }
        return false;
      }

      final RadComponent component = parent.getComponentAtGrid(row, column);
      if (component != null && component != selectedComponent) {
        if (myMoveToLast) {
          if (myExtend) {
            FormEditingUtil.selectComponent(myEditor, component);
          }
          lastComponent = component;
        }
        else {
          selectOrExtend(component);
          return true;
        }
      }
    } while(true);

    if (lastComponent != null) {
      selectOrExtend(lastComponent);
      return true;
    }
    return false;
  }

  protected abstract int calcDistance(Point source, Point point);

  protected int getColumnMoveDelta() {
    return 0;
  }

  protected int getRowMoveDelta() {
    return 0;
  }
}
