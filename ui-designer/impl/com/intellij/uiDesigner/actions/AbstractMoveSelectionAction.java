package com.intellij.uiDesigner.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Ref;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.GuiEditorUtil;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.radComponents.RadAtomicComponent;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.radComponents.RadContainer;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.Point;
import java.util.ArrayList;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
abstract class AbstractMoveSelectionAction extends AnAction{
  private static final Logger LOG=Logger.getInstance("#com.intellij.uiDesigner.actions.MoveSelectionToRightAction");

  private final GuiEditor myEditor;
  private final boolean myExtend;

  public AbstractMoveSelectionAction(@NotNull final GuiEditor editor, boolean extend) {
    myEditor = editor;
    myExtend = extend;
  }

  public final void actionPerformed(final AnActionEvent e) {
    final ArrayList<RadComponent> selectedComponents = FormEditingUtil.getSelectedComponents(myEditor);
    final JComponent rootContainerDelegee = myEditor.getRootContainer().getDelegee();
    if(selectedComponents.size() == 0){
      moveToFirstComponent(rootContainerDelegee);
      return;
    }
    final RadComponent selectedComponent = selectedComponents.get(0);

    if (moveSelectionByGrid(selectedComponent)) {
      return;
    }

    // 1. We need to get coordinates of all editor's component in the same
    // coordinate system. For example, in the RadRootContainer rootContainerDelegee's coordinate system.

    final ArrayList<RadComponent> components = new ArrayList<RadComponent>();
    final ArrayList<Point> points = new ArrayList<Point>();
    FormEditingUtil.iterate(
      myEditor.getRootContainer(),
      new FormEditingUtil.ComponentVisitor<RadComponent>() {
        public boolean visit(final RadComponent component) {
          if (component instanceof RadAtomicComponent) {
            if(selectedComponent.equals(component)){
              return true;
            }
            if (!GuiEditorUtil.isComponentSwitchedInView(component)) {
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
    if(components.size() == 0){
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

    LOG.assertTrue(nextSelectedIndex != -1);
    final RadComponent component = components.get(nextSelectedIndex);
    selectOrExtend(component);
  }

  private void selectOrExtend(final RadComponent component) {
    if (myExtend) {
      GuiEditorUtil.selectComponent(component);
    }
    else {
      GuiEditorUtil.selectSingleComponent(component);
    }
  }

  private void moveToFirstComponent(final JComponent rootContainerDelegee) {
    final int[] minX = new int[]{Integer.MAX_VALUE};
    final int[] minY = new int[]{Integer.MAX_VALUE};
    final Ref<RadComponent> componentToBeSelected = new Ref<RadComponent>();
    FormEditingUtil.iterate(
      myEditor.getRootContainer(),
      new FormEditingUtil.ComponentVisitor<RadComponent>() {
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
      GuiEditorUtil.selectComponent(componentToBeSelected.get());
    }
  }

  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setEnabled(!myEditor.getMainProcessor().isProcessorActive());
  }

  private boolean moveSelectionByGrid(final RadComponent selectedComponent) {
    final RadContainer parent = selectedComponent.getParent();
    if (parent == null || !parent.isGrid()) {
      return false;
    }

    final GridLayoutManager grid = (GridLayoutManager) parent.getLayout();
    int row = selectedComponent.getConstraints().getRow();
    int column = selectedComponent.getConstraints().getColumn();

    do {
      row += getRowMoveDelta();
      column += getColumnMoveDelta();
      if (row < 0 || row >= grid.getRowCount() || column < 0 || column >= grid.getColumnCount()) {
        return false;
      }

      final RadComponent component = parent.getComponentAtGrid(row, column);
      if (component != null && component != selectedComponent) {
        selectOrExtend(component);
        return true;
      }
    } while(true);
  }

  protected abstract int calcDistance(Point source, Point point);

  protected int getColumnMoveDelta() {
    return 0;
  }

  protected int getRowMoveDelta() {
    return 0;
  }
}
