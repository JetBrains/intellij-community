/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.designSurface;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionPopupMenu;
import com.intellij.ui.LightColors;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.GuiEditorUtil;
import com.intellij.uiDesigner.actions.GridChangeActionGroup;
import com.intellij.uiDesigner.componentTree.ComponentSelectionListener;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.radComponents.RadContainer;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;

/**
 * @author yole
 */
public class GridCaptionPanel extends JPanel implements ComponentSelectionListener {
  private GuiEditor myEditor;
  private boolean myIsRow;
  private RadContainer myLastContainer;
  private ListSelectionModel mySelectionModel = new DefaultListSelectionModel();

  public GridCaptionPanel(final GuiEditor editor, final boolean isRow) {
    myEditor = editor;
    myIsRow = isRow;
    mySelectionModel.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
    setBackground(Color.LIGHT_GRAY);
    editor.addComponentSelectionListener(this);

    addMouseListener(new MyMouseListener());
  }

  @Override public Dimension getPreferredSize() {
    return new Dimension(15, 15);
  }

  @Override public void paintComponent(Graphics g) {
    super.paintComponent(g);

    final Rectangle bounds = getBounds();

    RadContainer container = getSelectedGridContainer();
    if (container == null) {
      return;
    }
    GridLayoutManager layout = (GridLayoutManager) container.getLayout();
    int[] coords = myIsRow ? layout.getYs() : layout.getXs();
    int[] sizes = myIsRow ? layout.getHeights() : layout.getWidths();

    for(int i=0; i<coords.length; i++) {
      int x = myIsRow ? 0 : coords [i];
      int y = myIsRow ? coords [i] : 0;
      Point pnt = SwingUtilities.convertPoint(container.getDelegee(), x, y, this);

      Rectangle rc = myIsRow
                     ? new Rectangle(bounds.x, pnt.y, bounds.width-1, sizes [i])
                     : new Rectangle(pnt.x, bounds.y, sizes [i], bounds.height-1);

      g.setColor(mySelectionModel.isSelectedIndex(i) ? LightColors.BLUE : LightColors.GREEN);
      g.fillRect(rc.x, rc.y, rc.width, rc.height);
      g.setColor(Color.DARK_GRAY);
      g.drawRect(rc.x, rc.y, rc.width, rc.height);
    }

    g.setColor(Color.DARK_GRAY);
    if (myIsRow) {
      g.drawLine(bounds.width-1, 0, bounds.width-1, bounds.height);
    }
    else {
      g.drawLine(0, bounds.height-1, bounds.width, bounds.height-1);
    }

  }

  @Nullable private RadContainer getSelectedGridContainer() {
    final ArrayList<RadComponent> selection = FormEditingUtil.getSelectedComponents(myEditor);
    RadContainer container = GuiEditorUtil.getSelectionParent(selection);
    if (container == null && myEditor.getRootContainer().getComponentCount() > 0) {
      container = (RadContainer)myEditor.getRootContainer().getComponent(0);
    }
    if (container != null && !container.isGrid()) {
      return null;
    }
    return container;
  }

  public void selectedComponentChanged(GuiEditor source) {
    RadContainer container = getSelectedGridContainer();
    if (container != myLastContainer) {
      myLastContainer = container;
      mySelectionModel.clearSelection();
    }
    repaint();
  }

  private class MyMouseListener extends MouseAdapter {
    @Override public void mouseClicked(MouseEvent e) {
      RadContainer container = getSelectedGridContainer();
      if (container == null) return;
      GridLayoutManager layout = (GridLayoutManager) container.getLayout();
      Point pnt = SwingUtilities.convertPoint(GridCaptionPanel.this, e.getPoint(), container.getDelegee());
      int cell = myIsRow ? layout.getRowAt(pnt.y) : layout.getColumnAt(pnt.x);
      if ((e.getModifiers() & MouseEvent.CTRL_MASK) != 0) {
        mySelectionModel.addSelectionInterval(cell, cell);
      }
      else if ((e.getModifiers() & MouseEvent.SHIFT_MASK) != 0) {
        mySelectionModel.addSelectionInterval(mySelectionModel.getAnchorSelectionIndex(), cell);
      }
      else {
        mySelectionModel.setSelectionInterval(cell, cell);
      }
      /*
      for(RadComponent component: container.getComponents()) {
        GridConstraints c = component.getConstraints();
        if (cell >= c.getCell(myIsRow) && cell < c.getCell(myIsRow) + c.getSpan(myIsRow)) {
          component.setSelected(true);
        }
      }
      */
      repaint();
    }


    @Override public void mouseReleased(MouseEvent e) {
      RadContainer container = getSelectedGridContainer();
      if (container == null) return;
      GridLayoutManager layout = (GridLayoutManager) container.getLayout();
      Point pnt = SwingUtilities.convertPoint(GridCaptionPanel.this, e.getPoint(), container.getDelegee());
      int cell = myIsRow ? layout.getRowAt(pnt.y) : layout.getColumnAt(pnt.x);

      if (e.isPopupTrigger()) {
        GridChangeActionGroup group = new GridChangeActionGroup(myEditor, container, cell,
                                                                myIsRow ? SwingConstants.VERTICAL : SwingConstants.HORIZONTAL);
        final ActionPopupMenu popupMenu = ActionManager.getInstance().createActionPopupMenu(ActionPlaces.UNKNOWN, group);
        popupMenu.getComponent().show(GridCaptionPanel.this, e.getX(), e.getY());
      }
    }
  }
}
