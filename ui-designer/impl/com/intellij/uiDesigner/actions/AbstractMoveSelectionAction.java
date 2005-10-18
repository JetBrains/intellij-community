package com.intellij.uiDesigner.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.RadAtomicComponent;
import com.intellij.uiDesigner.RadComponent;
import com.intellij.uiDesigner.GuiEditor;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
abstract class AbstractMoveSelectionAction extends AnAction{
  private static final Logger LOG=Logger.getInstance("#com.intellij.uiDesigner.actions.MoveSelectionToRightAction");

  private final GuiEditor myEditor;

  public AbstractMoveSelectionAction(final GuiEditor editor) {
    LOG.assertTrue(editor != null);
    myEditor = editor;
  }

  public final void actionPerformed(final AnActionEvent e) {
    final ArrayList<RadComponent> selectedComponents = FormEditingUtil.getSelectedComponents(myEditor);
    final JComponent rootContainerDelegee = myEditor.getRootContainer().getDelegee();
    if(selectedComponents.size() == 0){
      final int[] minX = new int[]{Integer.MAX_VALUE};
      final int[] minY = new int[]{Integer.MAX_VALUE};
      final RadComponent[] componentToBeSelected = new RadComponent[1];
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
                componentToBeSelected[0] = component;
              }
            }
            return true;
          }
        }
      );
      if(componentToBeSelected[0] != null){
        componentToBeSelected[0].setSelected(true);
      }
      return;
    }
    final RadComponent selectedComponent = selectedComponents.get(0);

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
    FormEditingUtil.clearSelection(myEditor.getRootContainer());
    components.get(nextSelectedIndex).setSelected(true);
  }

  protected abstract int calcDistance(Point source, Point point);
}
