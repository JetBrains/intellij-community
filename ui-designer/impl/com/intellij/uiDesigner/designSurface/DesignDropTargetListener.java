package com.intellij.uiDesigner.designSurface;

import com.intellij.ide.palette.impl.PaletteManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.uiDesigner.CutCopyPasteSupport;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.SimpleTransferable;
import com.intellij.uiDesigner.componentTree.ComponentTree;
import com.intellij.uiDesigner.propertyInspector.UIDesignerToolWindowManager;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.palette.ComponentItem;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.radComponents.RadContainer;

import javax.swing.*;
import java.awt.*;
import java.awt.dnd.*;
import java.util.List;
import java.util.ArrayList;

/**
 * @author yole
 */
class DesignDropTargetListener implements DropTargetListener {
  private static final Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.designSurface.DesignDropTargetListener");

  private DraggedComponentList myDraggedComponentList;
  private ComponentDragObject myComponentDragObject;
  private List<RadComponent> myDraggedComponentsCopy;
  private Point myLastPoint;
  private final GuiEditor myEditor;
  private final GridInsertProcessor myGridInsertProcessor;
  private boolean myUseDragDelta = false;
  private ComponentTree myComponentTree;

  public DesignDropTargetListener(final GuiEditor editor) {
    myEditor = editor;
    myGridInsertProcessor = new GridInsertProcessor(editor);
    myComponentTree = UIDesignerToolWindowManager.getInstance(editor.getProject()).getComponentTree();
  }

  public void dragEnter(DropTargetDragEvent dtde) {
    try {
      DraggedComponentList dcl = DraggedComponentList.fromTransferable(dtde.getTransferable());
      if (dcl != null) {
        myDraggedComponentList = dcl;
        myComponentDragObject = dcl;
        processDragEnter(dcl, dtde.getLocation(), dtde.getDropAction());
        dtde.acceptDrag(dtde.getDropAction());
        myLastPoint = dtde.getLocation();
      }
      else {
        ComponentItem componentItem = SimpleTransferable.getData(dtde.getTransferable(), ComponentItem.class);
        if (componentItem != null) {
          myComponentDragObject = componentItem;
          dtde.acceptDrag(dtde.getDropAction());
          myLastPoint = dtde.getLocation();
        }
      }
    }
    catch (Exception e) {
      LOG.error(e);
    }
  }

  private void processDragEnter(final DraggedComponentList draggedComponentList, final Point location, final int dropAction) {
    final List<RadComponent> dragComponents = draggedComponentList.getComponents();

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
    myDraggedComponentsCopy = CutCopyPasteSupport.copyComponents(myEditor, dragComponents);
    for (int i=0; i<dragComponents.size(); i++) {
      myDraggedComponentsCopy.get(i).setSelected(true);
      final JComponent delegee = myDraggedComponentsCopy.get(i).getDelegee();
      final Point point = SwingUtilities.convertPoint(
        draggedComponentList.getOriginalParents() [i].getDelegee(),
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
      //myEditor.getDragLayer().add(delegee);
    }

    for (final RadComponent c : dragComponents) {
      if (dropAction != DnDConstants.ACTION_COPY) {
        c.setDragBorder(true);
      }
      c.setSelected(false);
    }
  }

  public void dragOver(DropTargetDragEvent dtde) {
    try {
      if (myComponentDragObject == null) {
        dtde.rejectDrag();
        return;
      }
      final int dx = dtde.getLocation().x - myLastPoint.x;
      final int dy = dtde.getLocation().y - myLastPoint.y;

      if (myDraggedComponentsCopy != null && myDraggedComponentList != null) {
        for (RadComponent aMySelection : myDraggedComponentsCopy) {
          aMySelection.shift(dx, dy);
        }
      }

      myLastPoint = dtde.getLocation();
      myEditor.getDragLayer().repaint();

      DropLocation location = myGridInsertProcessor.processDragEvent(dtde.getLocation(), myComponentDragObject);
      if (!location.canDrop(myComponentDragObject) ||
          (myDraggedComponentList != null && FormEditingUtil.isDropOnChild(myDraggedComponentList, location))) {
        myComponentTree.setDropTargetComponent(null);
        dtde.rejectDrag();
      }
      else {
        myComponentTree.setDropTargetComponent(location.getContainer());
        dtde.acceptDrag(dtde.getDropAction());
      }
    }
    catch (Exception e) {
      LOG.error(e);
    }
  }

  public void dropActionChanged(DropTargetDragEvent dtde) {
    DraggedComponentList dcl = DraggedComponentList.fromTransferable(dtde.getTransferable());
    if (dcl != null) {
      setDraggingState(dcl, dtde.getDropAction() != DnDConstants.ACTION_COPY);
    }
  }

  public void dragExit(DropTargetEvent dte) {
    try {
      myComponentTree.setDropTargetComponent(null);
      myUseDragDelta = false;
      if (myDraggedComponentList != null) {
        cancelDrag();
        setDraggingState(myDraggedComponentList, false);
        myEditor.getActiveDecorationLayer().removeFeedback();
        myDraggedComponentList = null;
        myEditor.setDesignTimeInsets(2);
      }
      myDraggedComponentsCopy = null;
    }
    catch (Exception e) {
      LOG.error(e);
    }
  }

  public void drop(DropTargetDropEvent dtde) {
    try {
      myComponentTree.setDropTargetComponent(null);


      DraggedComponentList dcl = DraggedComponentList.fromTransferable(dtde.getTransferable());
      if (dcl != null) {
        if (processDrop(dcl, dtde.getLocation(), dtde.getDropAction())) {
          myEditor.refreshAndSave(true);
        }
      }
      else {
        ComponentItem componentItem = SimpleTransferable.getData(dtde.getTransferable(), ComponentItem.class);
        if (componentItem != null) {
          myEditor.getMainProcessor().setInsertFeedbackEnabled(false);
          new InsertComponentProcessor(myEditor).processComponentInsert(dtde.getLocation(), null, componentItem);
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            public void run() {
              PaletteManager.getInstance(myEditor.getProject()).clearActiveItem();
              myEditor.getActiveDecorationLayer().removeFeedback();
              myEditor.getLayeredPane().setCursor(null);
              myEditor.getGlassLayer().requestFocus();
              myEditor.getMainProcessor().setInsertFeedbackEnabled(true);
            }
          });
        }
      }
      myDraggedComponentsCopy = null;
      myEditor.repaintLayeredPane();
    }
    catch (Exception e) {
      LOG.error(e);
    }
  }

  private boolean processDrop(final DraggedComponentList dcl, final Point dropPoint, final int dropAction) {
    myEditor.getActiveDecorationLayer().removeFeedback();
    final int dropX = dropPoint.x;
    final int dropY = dropPoint.y;
    final ArrayList<RadComponent> dclComponents = dcl.getComponents();
    final int componentCount = dclComponents.size();
    DropLocation location = GridInsertProcessor.getDropLocation(myEditor.getRootContainer(), dropPoint);
    if (FormEditingUtil.isDropOnChild(dcl, location)) {
      setDraggingState(dcl, false);
      return false;
    }
    if (!location.canDrop(dcl)) {
      setDraggingState(dcl, false);
      return false;
    }

    if (!myEditor.ensureEditable()) {
      setDraggingState(dcl, false);
      return false;
    }

    List<RadComponent> droppedComponents;

    RadContainer[] originalParents = dcl.getOriginalParents();

    cancelDrag();
    if (dropAction == DnDConstants.ACTION_COPY) {
      setDraggingState(dcl, false);
      droppedComponents = myDraggedComponentsCopy;
      if (droppedComponents == null) {
        return false;
      }
    }
    else {
      for(int i=0; i<dclComponents.size(); i++) {
        LOG.info("Removing component " + dclComponents.get(i).getId() + " with constraints " + dcl.getOriginalConstraints() [i]);
        originalParents [i].removeComponent(dclComponents.get(i));
      }
      droppedComponents = dclComponents;
    }

    final int[] dx = new int[componentCount];
    final int[] dy = new int[componentCount];
    for (int i = 0; i < componentCount; i++) {
      final RadComponent component = myDraggedComponentsCopy.get(i);
      dx[i] = component.getX() - dropX;
      dy[i] = component.getY() - dropY;
    }

    final RadComponent[] components = droppedComponents.toArray(new RadComponent[componentCount]);
    final GridConstraints[] originalConstraints = dcl.getOriginalConstraints();

    location.processDrop(myEditor, components, originalConstraints, dcl);

    if (dropAction == DnDConstants.ACTION_COPY) {
      for (RadComponent component : droppedComponents) {
        InsertComponentProcessor.createBindingWhenDrop(myEditor, component);
      }
      FormEditingUtil.selectComponents(myEditor, droppedComponents);
    }
    else {
      setDraggingState(dcl, false);
    }

    for (int i = 0; i < originalConstraints.length; i++) {
      if (originalParents[i].getLayoutManager().isGrid()) {
        FormEditingUtil.deleteEmptyGridCells(originalParents[i], originalConstraints[i]);
      }
    }
    return true;
  }

  private void cancelDrag() {
    if (myDraggedComponentsCopy != null) {
      for(RadComponent c: myDraggedComponentsCopy) {
        myEditor.getDragLayer().remove(c.getDelegee());
      }
    }
    myEditor.refresh();
  }

  private static void setDraggingState(final DraggedComponentList draggedComponentList, final boolean dragging) {
    for (RadComponent c: draggedComponentList.getComponents()) {
      c.setDragBorder(dragging);
    }
  }

  public void setUseDragDelta(final boolean useDragDelta) {
    myUseDragDelta = useDragDelta;
  }
}
