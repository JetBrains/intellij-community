package com.intellij.uiDesigner;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.util.ui.UIUtil;
import gnu.trove.TIntArrayList;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.dnd.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.InputEvent;
import java.util.*;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class DragSelectionProcessor extends EventProcessor {
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
  private final MyDragGestureRecognizer myDragGestureRecognizer;
  private final MyDragSourceListener myDragSourceListener = new MyDragSourceListener();

  public DragSelectionProcessor(@NotNull final GuiEditor editor) {
    //noinspection ConstantConditions
    LOG.assertTrue(editor != null);

    myEditor = editor;
    myGridInsertProcessor = new GridInsertProcessor(editor);
    myDragGestureRecognizer = new MyDragGestureRecognizer(DragSource.getDefaultDragSource(),
                                                          myEditor.getActiveDecorationLayer(),
                                                          DnDConstants.ACTION_COPY_OR_MOVE);

    new DropTarget(myEditor.getActiveDecorationLayer(),
                   DnDConstants.ACTION_COPY_OR_MOVE,
                   new MyDropTargetListener(myEditor, myGridInsertProcessor));
  }

  public boolean isDragActive() {
    return myDragStarted;
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
      for (int i = 1; i < myOriginalParents.length; i++) {
        if (myOriginalParents[i] != myOriginalParents[0] ||
            myOriginalConstraints[i].getRow() != myOriginalConstraints[0].getRow()) {
          sameRow = false;
          break;
        }
      }
      if (sameRow) {
        for (GridConstraints constraints : myOriginalConstraints) {
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
    myLastPoint = e.getPoint();
  }

  /**
   * "Drops" selection at the specified <code>point</code>.
   *
   * @param point      point in coordinates of drag layer (it's convenient here).
   * @param copyOnDrop
   * @return true if the selected components were successfully dropped
   */
  private boolean dropSelection(@NotNull final Point point, final boolean copyOnDrop) {
    //noinspection ConstantConditions
    LOG.assertTrue(point != null);
    myGridInsertProcessor.removeFeedbackPainter();
    GridInsertProcessor.GridInsertLocation location = myGridInsertProcessor.getGridInsertLocation(point.x, point.y, myDragRelativeColumn);
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
      for (RadComponent component : mySelection) {
        component.setSelected(true);
        InsertComponentProcessor.createBindingWhenDrop(myEditor, component);
      }
    }

    for (int i = 0; i < myOriginalConstraints.length; i++) {
      if (myOriginalParents[i].isGrid()) {
        FormEditingUtil.deleteEmptyGridCells(myOriginalParents[i], myOriginalConstraints[i]);
      }
    }

    return true;

  }

  private void processMouseReleased(final boolean copyOnDrop) {
    // Try to drop selection at the point of mouse event.
    if (dropSelection(myLastPoint, copyOnDrop)) {
      myEditor.refreshAndSave(true);
    }
    else {
      cancelDrag();
    }

    myEditor.repaintLayeredPane();
  }

  protected boolean cancelOperation() {
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

  protected void processKeyEvent(final KeyEvent e) {
  }

  protected void processMouseEvent(final MouseEvent e) {
    if (e.getID() == MouseEvent.MOUSE_PRESSED) {
      myPressPoint = e.getPoint();
    }
    else if (e.getID() == MouseEvent.MOUSE_RELEASED) {
      if (!myDragStarted && e.isControlDown()) {
        RadComponent component = FormEditingUtil.getRadComponentAt(myEditor, e.getX(), e.getY());
        if (component != null) {
          component.setSelected(!component.isSelected());
        }
      }
    }
    else if (e.getID() == MouseEvent.MOUSE_DRAGGED) {
      if (!myDragStarted) {
        if ((Math.abs(e.getX() - myPressPoint.getX()) > TREMOR || Math.abs(e.getY() - myPressPoint.getY()) > TREMOR)) {
          ArrayList<InputEvent> eventList = new ArrayList<InputEvent>();
          eventList.add(e);
          myDragGestureRecognizer.setTriggerEvent(e);
          DragGestureEvent dge = new DragGestureEvent(myDragGestureRecognizer,
                                                      UIUtil.isControlKeyDown(e) ? DnDConstants.ACTION_COPY : DnDConstants.ACTION_MOVE,
                                                      myPressPoint, eventList);

          myDragStarted = true;
          dge.startDrag(null,
                        DraggedComponentList.pickupSelection(myEditor, e.getX(), e.getY()),
                        myDragSourceListener);
        }
      }
    }
  }

  private class MyDragGestureRecognizer extends DragGestureRecognizer {
    public MyDragGestureRecognizer(DragSource ds, Component c, int sa) {
      super(ds, c, sa);
    }

    protected void registerListeners() {
    }

    protected void unregisterListeners() {
    }

    public void setTriggerEvent(final MouseEvent e) {
      resetRecognizer();
      appendEvent(e);
    }
  }

  private class MyDragSourceListener extends DragSourceAdapter {
    public void dragDropEnd(DragSourceDropEvent dsde) {
      myDragStarted = false;
    }
  }

  private static class MyDropTargetListener implements DropTargetListener {
    private DraggedComponentList myDraggedComponentList;
    private Point myLastPoint;
    private final GuiEditor myEditor;
    private final GridInsertProcessor myGridInsertProcessor;

    public MyDropTargetListener(final GuiEditor editor, final GridInsertProcessor gridInsertProcessor) {
      myEditor = editor;
      myGridInsertProcessor = gridInsertProcessor;
    }

    public void dragEnter(DropTargetDragEvent dtde) {
      DraggedComponentList dcl = DraggedComponentList.fromDropTargetDragEvent(dtde);
      if (dcl != null) {
        myDraggedComponentList = dcl;
        processDragEnter(dcl);
        dtde.acceptDrag(dtde.getDropAction());
        myLastPoint = dtde.getLocation();
      }
    }

    private void processDragEnter(final DraggedComponentList draggedComponentList) {
      // Remove components from their parents.
      final java.util.List<RadComponent> dragComponents = draggedComponentList.getComponents();
      for (final RadComponent c : dragComponents) {
        c.getParent().removeComponent(c);
      }

      // Place selected components to the drag layer.
      for (final RadComponent c : dragComponents) {
        final JComponent delegee = c.getDelegee();
        final Point point = SwingUtilities.convertPoint(
          draggedComponentList.getOriginalParent(c).getDelegee(),
          delegee.getLocation(),
          myEditor.getDragLayer()
        );
        delegee.setLocation(point);
        myEditor.getDragLayer().add(delegee);
        c.shift(draggedComponentList.getDragDeltaX(), draggedComponentList.getDragDeltaY());
      }
    }

    public void dragOver(DropTargetDragEvent dtde) {
      final int dx = (int)(dtde.getLocation().getX() - myLastPoint.x);
      final int dy = (int)(dtde.getLocation().getY() - myLastPoint.y);
      for (RadComponent aMySelection : myDraggedComponentList.getComponents()) {
        aMySelection.shift(dx, dy);
      }

      myLastPoint = dtde.getLocation();
      myEditor.getDragLayer().repaint();

      int action = myGridInsertProcessor.processDragEvent((int)dtde.getLocation().getX(),
                                                          (int)dtde.getLocation().getY(),
                                                          dtde.getDropAction() == DnDConstants.ACTION_COPY,
                                                          myDraggedComponentList.getComponents().size(),
                                                          myDraggedComponentList.getDragRelativeColumn());
      if (action == DnDConstants.ACTION_NONE) {
        dtde.rejectDrag();
      }
      else {
        dtde.acceptDrag(action);
      }
    }

    public void dropActionChanged(DropTargetDragEvent dtde) {
    }

    public void dragExit(DropTargetEvent dte) {
      if (myDraggedComponentList != null) {
        cancelDrag(myDraggedComponentList);
        myDraggedComponentList = null;
      }
    }

    public void drop(DropTargetDropEvent dtde) {
      myGridInsertProcessor.removeFeedbackPainter();
      final int dropX = (int)dtde.getLocation().getX();
      final int dropY = (int)dtde.getLocation().getY();
      final int componentCount = myDraggedComponentList.getComponents().size();
      GridInsertProcessor.GridInsertLocation location = myGridInsertProcessor.getGridInsertLocation(
        dropX, dropY, myDraggedComponentList.getDragRelativeColumn());
      if (!myGridInsertProcessor.isDropInsertAllowed(location, componentCount)) {
        location = null;
      }

      if (!FormEditingUtil.canDrop(myEditor, dropX, dropY, componentCount) &&
          (location == null || location.getMode() == GridInsertProcessor.GridInsertMode.None)) {
        return;
      }

      ArrayList<RadComponent> droppedComponents;

      if (dtde.getDropAction() == DnDConstants.ACTION_COPY) {
        final String serializedComponents = CutCopyPasteSupport.serializeForCopy(myEditor, myDraggedComponentList.getComponents());
        cancelDrag(myDraggedComponentList);

        TIntArrayList xs = new TIntArrayList();
        TIntArrayList ys = new TIntArrayList();
        droppedComponents = new ArrayList<RadComponent>();
        CutCopyPasteSupport.loadComponentsToPaste(myEditor, serializedComponents, xs, ys, droppedComponents);
      }
      else {
        droppedComponents = myDraggedComponentList.getComponents();
      }

      final int[] dx = new int[componentCount];
      final int[] dy = new int[componentCount];
      for (int i = 0; i < componentCount; i++) {
        final RadComponent component = droppedComponents.get(i);
        dx[i] = component.getX() - dropX;
        dy[i] = component.getY() - dropY;
      }

      final RadComponent[] components = droppedComponents.toArray(new RadComponent[componentCount]);
      final GridConstraints[] originalConstraints = myDraggedComponentList.getOriginalConstraints();
      final RadContainer[] originalParents = myDraggedComponentList.getOriginalParents();

      if (location != null && location.getMode() != GridInsertProcessor.GridInsertMode.None) {
        myGridInsertProcessor.processGridInsertOnDrop(location, components, originalConstraints);
      }
      else {
        FormEditingUtil.drop(
          myEditor,
          dropX,
          dropY,
          components,
          dx,
          dy
        );
      }

      if (dtde.getDropAction() == DnDConstants.ACTION_COPY) {
        FormEditingUtil.clearSelection(myEditor.getRootContainer());
        for (RadComponent component : droppedComponents) {
          component.setSelected(true);
          InsertComponentProcessor.createBindingWhenDrop(myEditor, component);
        }
      }

      for (int i = 0; i < originalConstraints.length; i++) {
        if (originalParents[i].isGrid()) {
          FormEditingUtil.deleteEmptyGridCells(originalParents[i], originalConstraints [i]);
        }
      }
    }

    private void cancelDrag(DraggedComponentList draggedComponentList) {
      for (RadComponent c : draggedComponentList.getComponents()) {
        c.getConstraints().restore(draggedComponentList.getOriginalConstraints(c));
        c.setBounds(draggedComponentList.getOriginalBounds(c));
        final RadContainer originalParent = draggedComponentList.getOriginalParent(c);
        if (c.getParent() != originalParent) {
          originalParent.addComponent(c);
        }
      }
      myEditor.refresh();
    }
  }
}
