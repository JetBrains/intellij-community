// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner.designSurface;

import com.intellij.ide.palette.impl.PaletteToolWindowManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.uiDesigner.CutCopyPasteSupport;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.SimpleTransferable;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.componentTree.ComponentTree;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.palette.ComponentItem;
import com.intellij.uiDesigner.propertyInspector.DesignerToolWindowManager;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.radComponents.RadContainer;

import javax.swing.*;
import java.awt.*;
import java.awt.dnd.*;
import java.util.ArrayList;
import java.util.List;


class DesignDropTargetListener implements DropTargetListener {
  private static final Logger LOG = Logger.getInstance(DesignDropTargetListener.class);

  private DraggedComponentList myDraggedComponentList;
  private ComponentDragObject myComponentDragObject;
  private List<RadComponent> myDraggedComponentsCopy;
  private Point myLastPoint;
  private final GuiEditor myEditor;
  private final GridInsertProcessor myGridInsertProcessor;
  private boolean myUseDragDelta = false;

  DesignDropTargetListener(final GuiEditor editor) {
    myEditor = editor;
    myGridInsertProcessor = new GridInsertProcessor(editor);
  }

  @Override
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
          myComponentDragObject = new ComponentItemDragObject(componentItem);
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

  @Override
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

      ComponentDropLocation location = myGridInsertProcessor.processDragEvent(dtde.getLocation(), myComponentDragObject);
      ComponentTree componentTree = DesignerToolWindowManager.getInstance(myEditor).getComponentTree();

      if (!location.canDrop(myComponentDragObject) ||
          (myDraggedComponentList != null && FormEditingUtil.isDropOnChild(myDraggedComponentList, location))) {
        if (componentTree != null) {
          componentTree.setDropTargetComponent(null);
        }
        dtde.rejectDrag();
      }
      else {
        if (componentTree != null) {
          componentTree.setDropTargetComponent(location.getContainer());
        }
        dtde.acceptDrag(dtde.getDropAction());
      }
    }
    catch (Exception e) {
      LOG.error(e);
    }
  }

  @Override
  public void dropActionChanged(DropTargetDragEvent dtde) {
    DraggedComponentList dcl = DraggedComponentList.fromTransferable(dtde.getTransferable());
    if (dcl != null) {
      setDraggingState(dcl, dtde.getDropAction() != DnDConstants.ACTION_COPY);
    }
  }

  @Override
  public void dragExit(DropTargetEvent dte) {
    try {
      ComponentTree componentTree = DesignerToolWindowManager.getInstance(myEditor).getComponentTree();
      if (componentTree != null) {
        componentTree.setDropTargetComponent(null);
      }
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

  @Override
  public void drop(final DropTargetDropEvent dtde) {
    try {
      ComponentTree componentTree = DesignerToolWindowManager.getInstance(myEditor).getComponentTree();
      if (componentTree != null) {
        componentTree.setDropTargetComponent(null);
      }


      final DraggedComponentList dcl = DraggedComponentList.fromTransferable(dtde.getTransferable());
      if (dcl != null) {
        CommandProcessor.getInstance().executeCommand(myEditor.getProject(),
                                                      () -> {
                                                        if (processDrop(dcl, dtde.getLocation(), dtde.getDropAction())) {
                                                          myEditor.refreshAndSave(true);
                                                        }
                                                      }, UIDesignerBundle.message("command.drop.components"), null);
      }
      else {
        ComponentItem componentItem = SimpleTransferable.getData(dtde.getTransferable(), ComponentItem.class);
        if (componentItem != null) {
          myEditor.getMainProcessor().setInsertFeedbackEnabled(false);
          new InsertComponentProcessor(myEditor).processComponentInsert(dtde.getLocation(), componentItem);
          ApplicationManager.getApplication().invokeLater(() -> {
            PaletteToolWindowManager.getInstance(myEditor).clearActiveItem();
            myEditor.getActiveDecorationLayer().removeFeedback();
            myEditor.getLayeredPane().setCursor(null);
            IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(myEditor.getGlassLayer(), true));
            myEditor.getMainProcessor().setInsertFeedbackEnabled(true);
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
    final ArrayList<RadComponent> dclComponents = dcl.getComponents();
    final int componentCount = dclComponents.size();
    ComponentDropLocation location = GridInsertProcessor.getDropLocation(myEditor.getRootContainer(), dropPoint);
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

    final RadComponent[] components = droppedComponents.toArray(new RadComponent[componentCount]);
    final GridConstraints[] originalConstraints = dcl.getOriginalConstraints();

    location.processDrop(myEditor, components, originalConstraints, dcl);

    if (dropAction == DnDConstants.ACTION_COPY) {
      for (RadComponent component : droppedComponents) {
        InsertComponentProcessor.createBindingWhenDrop(myEditor, component, false);
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
