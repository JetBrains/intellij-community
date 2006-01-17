package com.intellij.uiDesigner.designSurface;

import com.intellij.uiDesigner.*;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.palette.ComponentItem;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.ide.palette.impl.PaletteManager;

import javax.swing.*;
import java.awt.*;
import java.awt.dnd.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
class DesignDropTargetListener implements DropTargetListener {
  private DraggedComponentList myDraggedComponentList;
  private Point myLastPoint;
  private final GuiEditor myEditor;
  private final GridInsertProcessor myGridInsertProcessor;
  private boolean myUseDragDelta = false;

  public DesignDropTargetListener(final GuiEditor editor) {
    myEditor = editor;
    myGridInsertProcessor = new GridInsertProcessor(editor);
  }

  public void dragEnter(DropTargetDragEvent dtde) {
    DraggedComponentList dcl = DraggedComponentList.fromTransferable(dtde.getTransferable());
    if (dcl != null) {
      myDraggedComponentList = dcl;
      processDragEnter(dcl, dtde.getLocation());
      dtde.acceptDrag(dtde.getDropAction());
      myLastPoint = dtde.getLocation();
    }
    else {
      ComponentItem componentItem = SimpleTransferable.getData(dtde.getTransferable(), ComponentItem.class);
      if (componentItem != null) {
        dtde.acceptDrag(dtde.getDropAction());
        myLastPoint = dtde.getLocation();
      }
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

    int dragSize = 1;
    int dragCol = 0;
    if (myDraggedComponentList != null) {
      for (RadComponent aMySelection : myDraggedComponentList.getComponents()) {
        aMySelection.shift(dx, dy);
      }
      dragSize = myDraggedComponentList.getComponents().size();
      dragCol = myDraggedComponentList.getDragRelativeColumn();
    }

    myLastPoint = dtde.getLocation();
    myEditor.getDragLayer().repaint();

    int action = myGridInsertProcessor.processDragEvent(dtde.getLocation().x,
                                                        dtde.getLocation().y,
                                                        dtde.getDropAction() == DnDConstants.ACTION_COPY,
                                                        dragSize,
                                                        dragCol);
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
    }
    else {
      ComponentItem componentItem = SimpleTransferable.getData(dtde.getTransferable(), ComponentItem.class);
      if (componentItem != null) {
        new InsertComponentProcessor(myEditor).processComponentInsert(dtde.getLocation(), componentItem);
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            PaletteManager.getInstance(myEditor.getProject()).clearActiveItem();
          }
        });
      }
    }
    myEditor.repaintLayeredPane();
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
        (location == null || location.getMode() == GridInsertMode.None)) {
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

    if (location != null && location.getMode() != GridInsertMode.None) {
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

  public void setUseDragDelta(final boolean useDragDelta) {
    myUseDragDelta = useDragDelta;
  }
}
