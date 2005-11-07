package com.intellij.uiDesigner;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.util.ui.UIUtil;
import gnu.trove.TIntArrayList;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class DragSelectionProcessor extends EventProcessor{
  private static final Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.DragSelectionProcessor");

  /**
   * We have not start drag/cancel drop if mouse pointer trembles in small area
   */
  private static final int TREMOR = 3;

  private final GuiEditor myEditor;
  private Point myLastPoint;

  private Point myPressPoint;

  /**
   * Arrays of components to be dragged
   */
  private ArrayList<RadComponent> mySelection;
  private GridConstraints[] myOriginalConstraints;
  private Rectangle[] myOriginalBounds;
  private RadContainer[] myOriginalParents;

  private boolean myDragStarted;
  private int myDragRelativeColumn;

  private final GridInsertProcessor myGridInsertProcessor;

  public DragSelectionProcessor(@NotNull final GuiEditor editor){
    //noinspection ConstantConditions
    LOG.assertTrue(editor!=null);

    myEditor = editor;
    myGridInsertProcessor = new GridInsertProcessor(editor);
  }

  private void processStartDrag(final MouseEvent e) {
    // Store selected components
    mySelection = FormEditingUtil.getSelectedComponents(myEditor);
    // sort selection in correct grid order
    Collections.sort(mySelection, new Comparator<RadComponent>() {
      public int compare(final RadComponent o1, final RadComponent o2) {
        if (o1.getParent() == o2.getParent()) {
          int result = o1.getConstraints().getRow() - o2.getConstraints().getRow();
          if (result == 0) {
            result = o1.getConstraints().getColumn() - o2.getConstraints().getColumn();
          }
          return result;
        }
        return 0;
      }
    });

    // Store original constraints and parents. This information is required
    // to restore initial state if drag is canceled.
    myOriginalConstraints = new GridConstraints[mySelection.size()];
    myOriginalBounds = new Rectangle[mySelection.size()];
    myOriginalParents = new RadContainer[mySelection.size()];
    for (int i = 0; i < mySelection.size(); i++) {
      final RadComponent component = mySelection.get(i);
      myOriginalConstraints[i] = component.getConstraints().store();
      myOriginalBounds[i] = component.getBounds();
      myOriginalParents[i] = component.getParent();
    }

    // It's very important to get component under mouse before the components are
    // removed from their parents.
    final RadComponent componentUnderMouse = FormEditingUtil.getRadComponentAt(myEditor, myPressPoint.x, myPressPoint.y);
    int dx = 0;
    int dy = 0;

    myDragRelativeColumn = 0;
    if (mySelection.size() > 1) {
      boolean sameRow = true;
      for(int i=1; i<myOriginalParents.length; i++) {
        if (myOriginalParents [i] != myOriginalParents [0] ||
            myOriginalConstraints [i].getRow() != myOriginalConstraints [0].getRow()) {
          sameRow = false;
          break;
        }
      }
      if (sameRow) {
        for(GridConstraints constraints: myOriginalConstraints) {
          myDragRelativeColumn = Math.max(myDragRelativeColumn,
                                          componentUnderMouse.getConstraints().getColumn() - constraints.getColumn());
        }
      }
    }

    // Remove components from their parents.
    for (final RadComponent c : mySelection) {
      c.getParent().removeComponent(c);
    }

    // Place selected components to the drag layer.
    for (int i = 0; i < mySelection.size(); i++) {
      final RadComponent c = mySelection.get(i);
      final JComponent delegee = c.getDelegee();
      final Point point = SwingUtilities.convertPoint(
        myOriginalParents[i].getDelegee(),
        delegee.getLocation(),
        myEditor.getDragLayer()
      );
      delegee.setLocation(point);
      myEditor.getDragLayer().add(delegee);

      // make sure mouse cursor is inside the component being dragged
      if (c == componentUnderMouse) {
        if (delegee.getX() > e.getX() || delegee.getX() + delegee.getWidth() < e.getX()) {
          dx = e.getX() - (delegee.getX() + delegee.getWidth() / 2);
        }
        if (delegee.getY() > e.getY() || delegee.getY() + delegee.getHeight() < e.getY()) {
          dy = e.getY() - (delegee.getY() + delegee.getHeight() / 2);
        }
      }
    }

    for (RadComponent aMySelection : mySelection) {
      aMySelection.shift(dx, dy);
    }

    myEditor.refresh();
    myLastPoint=e.getPoint();
  }

  /**
   * "Drops" selection at the specified <code>point</code>.
   *
   * @param point point in coordinates of drag layer (it's convenient here).
   *
   * @param copyOnDrop
   * @return true if the selected components were successfully dropped
   */
  private boolean dropSelection(@NotNull final Point point, final boolean copyOnDrop) {
    //noinspection ConstantConditions
    LOG.assertTrue(point!=null);

    GridInsertProcessor.GridInsertLocation location = null;
    myGridInsertProcessor.removeFeedbackPainter();
    location = myGridInsertProcessor.getGridInsertLocation(point.x, point.y, myDragRelativeColumn);
    if (!myGridInsertProcessor.isDropInsertAllowed(location, mySelection.size())) {
      location = null;
    }

    if (!FormEditingUtil.canDrop(myEditor, point.x, point.y, mySelection.size()) &&
        (location == null || location.getMode() == GridInsertProcessor.GridInsertMode.None)) {
      return false;
    }

    if (copyOnDrop) {
      final String serializedComponents = CutCopyPasteSupport.serializeForCopy(myEditor, mySelection);
      cancelDrag();

      TIntArrayList xs = new TIntArrayList();
      TIntArrayList ys = new TIntArrayList();
      mySelection.clear();
      CutCopyPasteSupport.loadComponentsToPaste(myEditor, serializedComponents, xs, ys, mySelection);
    }

    final int[] dx = new int[mySelection.size()];
    final int[] dy = new int[mySelection.size()];
    for (int i = 0; i < mySelection.size(); i++) {
      final RadComponent component = mySelection.get(i);
      dx[i] = component.getX() - point.x;
      dy[i] = component.getY() - point.y;
    }

    final RadComponent[] components = mySelection.toArray(new RadComponent[mySelection.size()]);
    if (location != null && location.getMode() != GridInsertProcessor.GridInsertMode.None) {
      myGridInsertProcessor.processGridInsertOnDrop(location, components, myOriginalConstraints);
    }
    else {
      FormEditingUtil.drop(
        myEditor,
        point.x,
        point.y,
        components,
        dx,
        dy
      );
    }

    if (copyOnDrop) {
      FormEditingUtil.clearSelection(myEditor.getRootContainer());
      for(RadComponent component: mySelection) {
        component.setSelected(true);
        InsertComponentProcessor.createBindingWhenDrop(myEditor, component);
      }
    }

    for(int i=0; i<myOriginalConstraints.length; i++) {
      if (myOriginalParents [i].isGrid()) {
        FormEditingUtil.deleteEmptyGridCells(myOriginalParents [i], myOriginalConstraints [i]);
      }
    }

    return true;
  }

  private void processMouseReleased(final boolean copyOnDrop){
    // Try to drop selection at the point of mouse event.
    if(dropSelection(myLastPoint, copyOnDrop)){
      myEditor.refreshAndSave(true);
    } else {
      cancelDrag();
    }

    myEditor.repaintLayeredPane();
  }

  protected boolean cancelOperation(){
    if (!myDragStarted) {
      return true;
    }
    // Try to drop selection at the point of mouse event.
    cancelDrag();
    myGridInsertProcessor.removeFeedbackPainter();
    myEditor.repaintLayeredPane();
    return true;
  }

  /**
   * Cancels drag of component. The method returns all dragged components
   * to their original places and returns them their original sizes.
   */
  private void cancelDrag() {
    for (int i = 0; i < mySelection.size(); i++) {
      final RadComponent c = mySelection.get(i);
      c.getConstraints().restore(myOriginalConstraints[i]);
      c.setBounds(myOriginalBounds[i]);
      final RadContainer originalParent = myOriginalParents[i];
      if (c.getParent() != originalParent) {
        originalParent.addComponent(c);
      }
    }
    myEditor.refresh();
  }

  protected void processKeyEvent(final KeyEvent e) { }

  protected void processMouseEvent(final MouseEvent e){
    if(e.getID()==MouseEvent.MOUSE_PRESSED){
      myPressPoint = e.getPoint();
    }
    else if(e.getID()==MouseEvent.MOUSE_RELEASED){
      if (myDragStarted) {
        processMouseReleased(UIUtil.isControlKeyDown(e));
      }
      else if (e.isControlDown()) {
        RadComponent component = FormEditingUtil.getRadComponentAt(myEditor, e.getX(), e.getY());
        if (component != null) {
          component.setSelected(!component.isSelected());
        }
      }
    }
    else if(e.getID()==MouseEvent.MOUSE_DRAGGED){
      if (!myDragStarted) {
        if ((Math.abs(e.getX() - myPressPoint.getX()) > TREMOR || Math.abs(e.getY() - myPressPoint.getY()) > TREMOR)) {
          processStartDrag(e);
          myDragStarted = true;
        }
      }

      if (myDragStarted) {
        processMouseDragged(e);
      }
    }
  }

  private void processMouseDragged(final MouseEvent e){
    // Move components in the drag layer.
    final int dx = e.getX() - myLastPoint.x;
    final int dy = e.getY() - myLastPoint.y;
    for (RadComponent aMySelection : mySelection) {
      aMySelection.shift(dx, dy);
    }

    myLastPoint=e.getPoint();
    myEditor.getDragLayer().repaint();

    setCursor(myGridInsertProcessor.processMouseMoveEvent(e.getX(), e.getY(), e.isControlDown(),
                                                          mySelection.size(), myDragRelativeColumn));
  }
}
