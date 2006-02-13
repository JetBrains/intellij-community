/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.designSurface;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionPopupMenu;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.LightColors;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.GuiEditorUtil;
import com.intellij.uiDesigner.propertyInspector.properties.PreferredSizeProperty;
import com.intellij.uiDesigner.actions.GridChangeActionGroup;
import com.intellij.uiDesigner.componentTree.ComponentSelectionListener;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.radComponents.RadContainer;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.util.ArrayList;

/**
 * @author yole
 */
public class GridCaptionPanel extends JPanel implements ComponentSelectionListener {
  private GuiEditor myEditor;
  private boolean myIsRow;
  private RadContainer mySelectedContainer;
  private ListSelectionModel mySelectionModel = new DefaultListSelectionModel();
  private int myResizeLine = -1;
  private PreferredSizeProperty myPreferredSizeProperty = new PreferredSizeProperty();
  private LineFeedbackPainter myFeedbackPainter = new LineFeedbackPainter();
  private static final Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.designSurface.GridCaptionPanel");

  public GridCaptionPanel(final GuiEditor editor, final boolean isRow) {
    myEditor = editor;
    myIsRow = isRow;
    mySelectionModel.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
    setBackground(Color.LIGHT_GRAY);
    editor.addComponentSelectionListener(this);

    final MyMouseListener listener = new MyMouseListener();
    addMouseListener(listener);
    addMouseMotionListener(listener);
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
    if (selection.size() == 1 && selection.get(0) instanceof RadContainer) {
      RadContainer container = (RadContainer) selection.get(0);
      if (container.isGrid()) {
        return container;
      }
    }
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
    if (container != mySelectedContainer) {
      mySelectedContainer = container;
      mySelectionModel.clearSelection();
    }
    repaint();
  }

  private class MyMouseListener extends MouseAdapter implements MouseMotionListener {
    private static final int MINIMUM_RESIZED_SIZE = 8;

    @Override public void mouseExited(MouseEvent e) {
      setCursor(Cursor.getDefaultCursor());
    }

    @Override public void mousePressed(MouseEvent e) {
      if (mySelectedContainer == null) return;
      Point pnt = SwingUtilities.convertPoint(GridCaptionPanel.this, e.getPoint(),
                                              mySelectedContainer.getDelegee());
      GridLayoutManager layout = (GridLayoutManager) mySelectedContainer.getLayout();
      myResizeLine = myIsRow
        ? layout.getHorizontalGridLineNear(pnt.y, 4)
        : layout.getVerticalGridLineNear(pnt.x, 4);
    }

    @Override public void mouseClicked(MouseEvent e) {
      int cell = getCellAt(e.getPoint());
      if (cell == -1) return;
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

    private int getCellAt(Point pnt) {
      if (mySelectedContainer == null) return -1;
      GridLayoutManager layout = (GridLayoutManager) mySelectedContainer.getLayout();
      pnt = SwingUtilities.convertPoint(GridCaptionPanel.this, pnt, mySelectedContainer.getDelegee());
      return myIsRow ? layout.getRowAt(pnt.y) : layout.getColumnAt(pnt.x);
    }


    @Override public void mouseReleased(MouseEvent e) {
      setCursor(Cursor.getDefaultCursor());
      myEditor.getActiveDecorationLayer().removeFeedback();

      if (myResizeLine >= 0) {
        Point pnt = SwingUtilities.convertPoint(GridCaptionPanel.this, e.getPoint(),
                                                mySelectedContainer.getDelegee());
        doResize(pnt);
        myResizeLine = -1;
      }

      int cell = getCellAt(e.getPoint());

      if (cell >= 0 && e.isPopupTrigger()) {
        GridChangeActionGroup group = new GridChangeActionGroup(myEditor, mySelectedContainer, cell,
                                                                myIsRow ? SwingConstants.VERTICAL : SwingConstants.HORIZONTAL);
        final ActionPopupMenu popupMenu = ActionManager.getInstance().createActionPopupMenu(ActionPlaces.UNKNOWN, group);
        popupMenu.getComponent().show(GridCaptionPanel.this, e.getX(), e.getY());
      }
    }

    private void doResize(final Point pnt) {
      GridLayoutManager layout = (GridLayoutManager) mySelectedContainer.getLayout();
      int[] coords = layout.getCoords(myIsRow);
      int prevCoord = coords [myResizeLine-1];
      int newCoord = myIsRow ? pnt.y : pnt.x;
      if (newCoord < prevCoord + MINIMUM_RESIZED_SIZE) {
        return;
      }
      int newSize = newCoord - prevCoord;

      if (mySelectedContainer.getParent().isXY()  && myResizeLine == coords.length) {
        final JComponent parentDelegee = mySelectedContainer.getDelegee();
        Dimension containerSize = parentDelegee.getSize();
        if (myIsRow) {
          containerSize.height = newCoord;
        }
        else {
          containerSize.width = newCoord;
        }
        parentDelegee.setSize(containerSize);
        parentDelegee.revalidate();
      }
      else {
        for(RadComponent component: mySelectedContainer.getComponents()) {
          GridConstraints c = component.getConstraints();
          if (c.getCell(myIsRow) == myResizeLine-1 && c.getSpan(myIsRow) == 1) {
            Dimension preferredSize = new Dimension(c.myPreferredSize);
            if (myIsRow) {
              preferredSize.height = newSize;
              if (preferredSize.width == -1) {
                preferredSize.width = component.getDelegee().getPreferredSize().width;
              }
            }
            else {
              preferredSize.width = newSize;
              if (preferredSize.height == -1) {
                preferredSize.height = component.getDelegee().getPreferredSize().height;
              }
            }
            try {
              myPreferredSizeProperty.setValue(component, preferredSize);
            }
            catch (Exception e) {
              LOG.error(e);
            }
          }
        }
      }

      myEditor.refreshAndSave(false);
    }

    public void mouseMoved(MouseEvent e) {
      if (mySelectedContainer == null) return;
      Point pnt = SwingUtilities.convertPoint(GridCaptionPanel.this, e.getPoint(),
                                              mySelectedContainer.getDelegee());
      GridLayoutManager layout = (GridLayoutManager) mySelectedContainer.getLayout();
      int gridLine = myIsRow
                     ? layout.getHorizontalGridLineNear(pnt.y, 4)
                     : layout.getVerticalGridLineNear(pnt.x, 4);

      // first grid line may not be dragged
      if (gridLine <= 0) {
        setCursor(Cursor.getDefaultCursor());
      }
      else if (myIsRow) {
        setCursor(Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR));
      }
      else {
        setCursor(Cursor.getPredefinedCursor(Cursor.W_RESIZE_CURSOR));
      }
    }

    public void mouseDragged(MouseEvent e) {
      if (myResizeLine != -1) {
        final ActiveDecorationLayer layer = myEditor.getActiveDecorationLayer();
        Point pnt = SwingUtilities.convertPoint(GridCaptionPanel.this, e.getPoint(), layer);
        Rectangle rc;
        if (myIsRow) {
          rc = new Rectangle(0, pnt.y, layer.getSize().width, 1);
        }
        else {
          rc = new Rectangle(pnt.x, 0, 1, layer.getSize().height);
        }
        layer.putFeedback(rc, myFeedbackPainter);
      }
    }
  }

  private static class LineFeedbackPainter implements FeedbackPainter {

    public void paintFeedback(Graphics2D g, Rectangle rc) {
      g.setColor(LightColors.YELLOW);
      if (rc.width == 1) {
        g.drawLine(rc.x, rc.y, rc.x, rc.y+rc.height);
      }
      else {
        g.drawLine(rc.x, rc.y, rc.x+rc.width, rc.y);
      }
    }
  }
}
