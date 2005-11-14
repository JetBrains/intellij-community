package com.intellij.uiDesigner.designSurface;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.uiDesigner.CutCopyPasteSupport;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.RadComponent;
import com.intellij.uiDesigner.RadContainer;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.dnd.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class DragSelectionProcessor extends EventProcessor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.designSurface.DragSelectionProcessor");

  /**
   * We have not start drag/cancel drop if mouse pointer trembles in small area
   */
  private static final int TREMOR = 3;

  private final GuiEditor myEditor;

  private Point myPressPoint;

  private boolean myDragStarted;
  private boolean myUseDragDelta = false;

  private final MyDragGestureRecognizer myDragGestureRecognizer;
  private final MyDragSourceListener myDragSourceListener = new MyDragSourceListener();

  public DragSelectionProcessor(@NotNull final GuiEditor editor) {
    //noinspection ConstantConditions
    LOG.assertTrue(editor != null);

    myEditor = editor;
    final GridInsertProcessor gridInsertProcessor = new GridInsertProcessor(editor);
    myDragGestureRecognizer = new MyDragGestureRecognizer(DragSource.getDefaultDragSource(),
                                                          myEditor.getActiveDecorationLayer(),
                                                          DnDConstants.ACTION_COPY_OR_MOVE);

    new DropTarget(myEditor.getActiveDecorationLayer(),
                   DnDConstants.ACTION_COPY_OR_MOVE,
                   new MyDropTargetListener(myEditor, gridInsertProcessor));
  }

  public boolean isDragActive() {
    return myDragStarted;
  }

  protected boolean cancelOperation() {
    if (!myDragStarted) {
      return true;
    }
    // Try to drop selection at the point of mouse event.
    //cancelDrag();
    myEditor.setDesignTimeInsets(2);
    myEditor.getActiveDecorationLayer().removeFeedback();
    myEditor.repaintLayeredPane();
    return true;
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
          myUseDragDelta = true;
          dge.startDrag(null,
                        DraggedComponentList.pickupSelection(myEditor, e.getX(), e.getY()),
                        myDragSourceListener);
        }
      }
    }
  }

  private static class MyDragGestureRecognizer extends DragGestureRecognizer {
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
    public void dropActionChanged(DragSourceDragEvent dsde) {
      final int shiftDownMask = (dsde.getGestureModifiersEx() & KeyEvent.SHIFT_DOWN_MASK);
      if (shiftDownMask != 0) {
        myEditor.setDesignTimeInsets(12);
      }
      else {
        myEditor.setDesignTimeInsets(2);
      }
    }

    public void dragDropEnd(DragSourceDropEvent dsde) {
      myDragStarted = false;
      myUseDragDelta = false;
      myEditor.setDesignTimeInsets(2);
    }
  }

  private class MyDropTargetListener implements DropTargetListener {
    private DraggedComponentList myDraggedComponentList;
    private Point myLastPoint;
    private final GuiEditor myEditor;
    private final GridInsertProcessor myGridInsertProcessor;

    public MyDropTargetListener(final GuiEditor editor, final GridInsertProcessor gridInsertProcessor) {
      myEditor = editor;
      myGridInsertProcessor = gridInsertProcessor;
    }

    public void dragEnter(DropTargetDragEvent dtde) {
      DraggedComponentList dcl = DraggedComponentList.fromTransferable(dtde.getTransferable());
      if (dcl != null) {
        myDraggedComponentList = dcl;
        processDragEnter(dcl, dtde.getLocation());
        dtde.acceptDrag(dtde.getDropAction());
        myLastPoint = dtde.getLocation();
      }
    }

    private void processDragEnter(final DraggedComponentList draggedComponentList, final Point location) {
      // Remove components from their parents.
      final List<RadComponent> dragComponents = draggedComponentList.getComponents();
      for (final RadComponent c : dragComponents) {
        c.getParent().removeComponent(c);
      }

      Rectangle allBounds = null;
      if (!draggedComponentList.hasDragDelta() || !myUseDragDelta) {
        final RadContainer[] originalParents = draggedComponentList.getOriginalParents();
        final Rectangle[] originalBounds = draggedComponentList.getOriginalBounds();
        for(int i=0; i<originalParents.length; i++) {
          Rectangle rc = SwingUtilities.convertRectangle(originalParents [i].getDelegee(),
                                                         originalBounds [i],
                                                         myEditor.getDragLayer());
          if (allBounds == null) {
            allBounds = rc;
          }
          else {
            allBounds = allBounds.union(rc);
          }
        }
      }

      // Place selected components to the drag layer.
      for (final RadComponent c : dragComponents) {
        final JComponent delegee = c.getDelegee();
        final Point point = SwingUtilities.convertPoint(
          draggedComponentList.getOriginalParent(c).getDelegee(),
          delegee.getLocation(),
          myEditor.getDragLayer()
        );
        if (draggedComponentList.hasDragDelta() && myUseDragDelta) {
          delegee.setLocation((int) point.getX() + draggedComponentList.getDragDeltaX(),
                              (int) point.getY() + draggedComponentList.getDragDeltaY());
        }
        else {
          assert allBounds != null;
          delegee.setLocation((int) (point.getX() - allBounds.getX() + location.getX()),
                              (int) (point.getY() - allBounds.getY() + location.getY()));
        }
        myEditor.getDragLayer().add(delegee);
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
      myUseDragDelta = false;
      if (myDraggedComponentList != null) {
        cancelDrag(myDraggedComponentList);
        myDraggedComponentList = null;
        myEditor.setDesignTimeInsets(2);
      }
    }

    public void drop(DropTargetDropEvent dtde) {
      DraggedComponentList dcl = DraggedComponentList.fromTransferable(dtde.getTransferable());
      if (dcl != null) {
        if (processDrop(dcl, dtde.getLocation(), dtde.getDropAction())) {
          myEditor.refreshAndSave(true);
        }
        myEditor.repaintLayeredPane();
      }
    }

    private boolean processDrop(final DraggedComponentList dcl, final Point dropPoint, final int dropAction) {
      myEditor.getActiveDecorationLayer().removeFeedback();
      final int dropX = (int)dropPoint.getX();
      final int dropY = (int)dropPoint.getY();
      final int componentCount = dcl.getComponents().size();
      GridInsertLocation location = GridInsertProcessor.getGridInsertLocation(
        myEditor, dropX, dropY, dcl.getDragRelativeColumn());
      if (!myGridInsertProcessor.isDropInsertAllowed(location, componentCount)) {
        location = null;
      }

      if (!FormEditingUtil.canDrop(myEditor, dropX, dropY, componentCount) &&
          (location == null || location.getMode() == GridInsertLocation.GridInsertMode.None)) {
        return false;
      }

      ArrayList<RadComponent> droppedComponents;

      if (dropAction == DnDConstants.ACTION_COPY) {
        final String serializedComponents = CutCopyPasteSupport.serializeForCopy(myEditor, dcl.getComponents());
        cancelDrag(dcl);

        droppedComponents = CutCopyPasteSupport.deserializeComponents(myEditor, serializedComponents);
        if (droppedComponents == null) {
          return false;
        }
      }
      else {
        droppedComponents = dcl.getComponents();
      }

      final int[] dx = new int[componentCount];
      final int[] dy = new int[componentCount];
      for (int i = 0; i < componentCount; i++) {
        final RadComponent component = droppedComponents.get(i);
        dx[i] = component.getX() - dropX;
        dy[i] = component.getY() - dropY;
      }

      final RadComponent[] components = droppedComponents.toArray(new RadComponent[componentCount]);
      final GridConstraints[] originalConstraints = dcl.getOriginalConstraints();
      final RadContainer[] originalParents = dcl.getOriginalParents();

      if (location != null && location.getMode() != GridInsertLocation.GridInsertMode.None) {
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

      if (dropAction == DnDConstants.ACTION_COPY) {
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
      return true;
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
