/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.designSurface;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.LightColors;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.GridChangeUtil;
import com.intellij.uiDesigner.propertyInspector.properties.PreferredSizeProperty;
import com.intellij.uiDesigner.actions.GridChangeActionGroup;
import com.intellij.uiDesigner.componentTree.ComponentSelectionListener;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.radComponents.RadContainer;
import com.intellij.ide.DeleteProvider;
import com.intellij.ide.dnd.*;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeListener;
import javax.swing.event.ChangeEvent;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.util.ArrayList;

/**
 * @author yole
 */
public class GridCaptionPanel extends JPanel implements ComponentSelectionListener, DataProvider {
  private GuiEditor myEditor;
  private boolean myIsRow;
  private RadContainer mySelectedContainer;
  private DefaultListSelectionModel mySelectionModel = new DefaultListSelectionModel();
  private int myResizeLine = -1;
  private int myDropInsertLine = -1;
  private PreferredSizeProperty myPreferredSizeProperty = new PreferredSizeProperty();
  private LineFeedbackPainter myFeedbackPainter = new LineFeedbackPainter();
  private DeleteProvider myDeleteProvider = new MyDeleteProvider();
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
    setFocusable(true);

    DnDManager.getInstance(editor.getProject()).registerSource(new MyDnDSource(), this);
    DnDManager.getInstance(editor.getProject()).registerTarget(new MyDnDTarget(), this);
  }

  @Override public Dimension getPreferredSize() {
    return new Dimension(12, 12);
  }

  @Override public void paintComponent(Graphics g) {
    super.paintComponent(g);
    Graphics2D g2d = (Graphics2D) g;

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

      g.setColor(getCaptionColor(i));
      g.fillRect(rc.x, rc.y, rc.width, rc.height);

      int sizePolicy = layout.getCellSizePolicy(myIsRow, i);
      if ((sizePolicy & GridConstraints.SIZEPOLICY_WANT_GROW) != 0) {
        Stroke oldStroke = g2d.getStroke();
        g2d.setStroke(new BasicStroke(2.0f));
        g.setColor(Color.BLUE);
        if (myIsRow) {
          int midPoint = (int) rc.getCenterX();
          g.drawLine(midPoint+1, rc.y+1, midPoint+1, rc.y+rc.height-1);
        }
        else {
          int midPoint = (int) rc.getCenterY();
          g.drawLine(rc.x+1, midPoint+1, rc.x+rc.width-1, midPoint+1);
        }
        g2d.setStroke(oldStroke);
      }

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

    if (myDropInsertLine >= 0) {
      int[] lines = myIsRow ? layout.getHorizontalGridLines() : layout.getVerticalGridLines();
      int coord = lines [myDropInsertLine];
      if (myIsRow) {
        coord = SwingUtilities.convertPoint(container.getDelegee(), 0, coord, this).y;
      }
      else {
        coord = SwingUtilities.convertPoint(container.getDelegee(), coord, 0, this).x;
      }
      Stroke oldStroke = g2d.getStroke();
      g2d.setStroke(new BasicStroke(2.0f));
      g.setColor(Color.BLUE);
      if (myIsRow) {
        g.drawLine(bounds.x+1, coord, bounds.x+bounds.width-1, coord);
      }
      else {
        g.drawLine(coord, bounds.y+1, coord, bounds.y+bounds.height-1);
      }

      g2d.setStroke(oldStroke);
    }

  }

  private Color getCaptionColor(final int i) {
    if (mySelectionModel.isSelectedIndex(i)) {
      return LightColors.BLUE;
    }
    if (GridChangeUtil.canDeleteCell(mySelectedContainer, i, myIsRow, false)) {
      return Color.PINK;
    }
    return LightColors.GREEN;
  }

  @Nullable private RadContainer getSelectedGridContainer() {
    final ArrayList<RadComponent> selection = FormEditingUtil.getSelectedComponents(myEditor);
    if (selection.size() == 1 && selection.get(0) instanceof RadContainer) {
      RadContainer container = (RadContainer) selection.get(0);
      if (container.getLayoutManager().isGrid()) {
        return container;
      }
    }
    RadContainer container = FormEditingUtil.getSelectionParent(selection);
    if (container == null && myEditor.getRootContainer().getComponentCount() > 0) {
      final RadComponent topComponent = myEditor.getRootContainer().getComponent(0);
      if (topComponent instanceof RadContainer) {
        container = (RadContainer) topComponent;
      }
    }
    if (container != null && !container.getLayoutManager().isGrid()) {
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

  @Nullable public Object getData(String dataId) {
    if (dataId.equals(DataConstantsEx.DELETE_ELEMENT_PROVIDER)) {
      return myDeleteProvider;
    }
    return myEditor.getData(dataId);
  }

  private int getOrientation() {
    return myIsRow ? SwingConstants.VERTICAL : SwingConstants.HORIZONTAL;
  }

  public void attachToScrollPane(final JScrollPane scrollPane) {
    scrollPane.getViewport().addChangeListener(new ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        repaint();
      }
    });
  }

  private int getCellAt(Point pnt) {
    if (mySelectedContainer == null) return -1;
    GridLayoutManager layout = (GridLayoutManager) mySelectedContainer.getLayout();
    pnt = SwingUtilities.convertPoint(this, pnt, mySelectedContainer.getDelegee());
    return myIsRow ? layout.getRowAt(pnt.y) : layout.getColumnAt(pnt.x);
  }

  private class MyMouseListener extends MouseAdapter implements MouseMotionListener {
    private static final int MINIMUM_RESIZED_SIZE = 8;

    @Override public void mouseExited(MouseEvent e) {
      setCursor(Cursor.getDefaultCursor());
    }

    @Override public void mousePressed(MouseEvent e) {
      if (mySelectedContainer == null) return;
      requestFocus();
      Point pnt = SwingUtilities.convertPoint(GridCaptionPanel.this, e.getPoint(),
                                              mySelectedContainer.getDelegee());
      GridLayoutManager layout = (GridLayoutManager) mySelectedContainer.getLayout();
      myResizeLine = myIsRow
                     ? layout.getHorizontalGridLineNear(pnt.y, 4)
                     : layout.getVerticalGridLineNear(pnt.x, 4);

      checkShowPopupMenu(e);
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
      repaint();
    }

    @Override public void mouseReleased(MouseEvent e) {
      setCursor(Cursor.getDefaultCursor());
      myEditor.getActiveDecorationLayer().removeFeedback();

      if (myResizeLine > 0) {
        Point pnt = SwingUtilities.convertPoint(GridCaptionPanel.this, e.getPoint(),
                                                mySelectedContainer.getDelegee());
        doResize(pnt);
        myResizeLine = -1;
      }

      checkShowPopupMenu(e);
    }

    private void checkShowPopupMenu(final MouseEvent e) {
      int cell = getCellAt(e.getPoint());

      if (cell >= 0 && e.isPopupTrigger()) {
        GridChangeActionGroup group = new GridChangeActionGroup(myEditor, mySelectedContainer, cell, getOrientation());
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

      if (!myEditor.ensureEditable()) {
        return;
      }

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
        Point pnt = e.getPoint();
        Rectangle rc;
        if (myIsRow) {
          rc = new Rectangle(0, pnt.y, layer.getSize().width, 1);
        }
        else {
          rc = new Rectangle(pnt.x, 0, 1, layer.getSize().height);
        }
        layer.putFeedback(GridCaptionPanel.this, rc, myFeedbackPainter);
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

  private class MyDeleteProvider implements DeleteProvider {
    public void deleteElement(DataContext dataContext) {
      int selectedIndex = mySelectionModel.getMinSelectionIndex();
      if (selectedIndex >= 0) {
        FormEditingUtil.deleteRowOrColumn(myEditor, mySelectedContainer, selectedIndex, getOrientation());
      }
    }

    public boolean canDeleteElement(DataContext dataContext) {
      if (mySelectedContainer == null || mySelectionModel.isSelectionEmpty()) {
        return false;
      }
      GridLayoutManager layout = (GridLayoutManager) mySelectedContainer.getLayout();
      if (myIsRow) {
        return layout.getRowCount() > 1;
      }
      return layout.getColumnCount() > 1;
    }
  }

  private class MyDnDSource implements DnDSource {
    public boolean canStartDragging(DnDAction action, Point dragOrigin) {
      int[] selectedCells = getSelectedCells(dragOrigin);
      for(int cell: selectedCells) {
        if (!canDragCell(cell)) {
          return false;
        }
      }
      return true;
    }

    private int[] getSelectedCells(final Point dragOrigin) {
      ArrayList<Integer> selection = new ArrayList<Integer>();
      RadContainer container = getSelectedGridContainer();
      if (container == null) {
        return new int[0];
      }
      GridLayoutManager layout = (GridLayoutManager) container.getLayout();
      int[] coords = myIsRow ? layout.getYs() : layout.getXs();
      for(int i=0; i<coords.length; i++) {
        if (mySelectionModel.isSelectedIndex(i)) {
          selection.add(i);
        }
      }
      if (selection.size() == 0) {
        int cell = getCellAt(dragOrigin);
        if (cell >= 0) {
          return new int[] { cell };
        }
      }
      int[] result = new int[selection.size()];
      for(int i=0; i<selection.size(); i++) {
        result [i] = selection.get(i).intValue();
      }
      return result;
    }

    private boolean canDragCell(final int cell) {
      if (mySelectedContainer == null) return false;
      for(RadComponent c: mySelectedContainer.getComponents()) {
        if (c.getConstraints().contains(myIsRow, cell) && c.getConstraints().getSpan(myIsRow) > 1) {
          return false;
        }
      }
      return true;
    }

    public DnDDragStartBean startDragging(DnDAction action, Point dragOrigin) {
      return new DnDDragStartBean(new MyDragBean(myIsRow, getSelectedCells(dragOrigin)));
    }

    @Nullable
    public Pair<Image, Point> createDraggedImage(DnDAction action, Point dragOrigin) {
      return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public void dragDropEnd() {
    }

    public void dropActionChanged(final int gestureModifiers) {
    }
  }

  private class MyDnDTarget implements DnDTarget {
    public boolean update(DnDEvent aEvent) {
      aEvent.setDropPossible(false, null);
      if (mySelectedContainer == null) {
        return false;
      }
      if (!(aEvent.getAttachedObject() instanceof MyDragBean)) {
        return false;
      }
      MyDragBean bean = (MyDragBean) aEvent.getAttachedObject();
      if (bean.isRow != myIsRow || bean.cells.length == 0) {
        return false;
      }
      int gridLine = getDropGridLine(aEvent);
      setDropInsertLine(gridLine);
      aEvent.setDropPossible(gridLine >= 0, null);
      return false;
    }

    private int getDropGridLine(final DnDEvent aEvent) {
      GridLayoutManager layout = (GridLayoutManager) mySelectedContainer.getLayout();
      final Point point = aEvent.getPointOn(mySelectedContainer.getDelegee());
      return myIsRow ? layout.getHorizontalGridLineNear(point.y, 20) : layout.getVerticalGridLineNear(point.x, 20);
    }

    public void drop(DnDEvent aEvent) {
      if (!(aEvent.getAttachedObject() instanceof MyDragBean)) {
        return;
      }
      MyDragBean dragBean = (MyDragBean) aEvent.getAttachedObject();
      int targetCell = getDropGridLine(aEvent);
      if (targetCell < 0) return;
      GridChangeUtil.moveCells(mySelectedContainer, myIsRow, dragBean.cells, targetCell);
      mySelectionModel.clearSelection();
      mySelectedContainer.revalidate();
      myEditor.refreshAndSave(true);
      setDropInsertLine(-1);
    }

    public void cleanUpOnLeave() {
      setDropInsertLine(-1);
    }

    public void updateDraggedImage(Image image, Point dropPoint, Point imageOffset) {
    }

    private void setDropInsertLine(final int i) {
      if (myDropInsertLine != i) {
        myDropInsertLine = i;
        repaint();
      }
    }
  }

  private static class MyDragBean {
    public boolean isRow;
    public int[] cells;

    public MyDragBean(final boolean row, final int[] cells) {
      isRow = row;
      this.cells = cells;
    }
  }
}
