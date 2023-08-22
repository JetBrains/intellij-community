// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner.designSurface;

import com.intellij.ide.palette.impl.PaletteToolWindowManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.palette.ComponentItem;
import com.intellij.uiDesigner.propertyInspector.InplaceContext;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.radComponents.RadRootContainer;
import com.intellij.util.ui.UIUtil;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;

public final class MainProcessor extends EventProcessor{
  private static final Logger LOG = Logger.getInstance(MainProcessor.class);

  private static final int DRAGGER_SIZE = 10;

  private EventProcessor myCurrentProcessor;
  @NotNull private final InsertComponentProcessor myInsertComponentProcessor;
  @NotNull private final GuiEditor myEditor;
  private boolean myInsertFeedbackEnabled = true;
  private Point myLastMousePosition = new Point(0, 0);

  public MainProcessor(@NotNull final GuiEditor editor){
    myEditor = editor;
    myInsertComponentProcessor = new InsertComponentProcessor(myEditor);
  }

  @Override
  protected void processKeyEvent(final KeyEvent e){
    if (e.getKeyCode() == KeyEvent.VK_SHIFT) {
      if (e.getID() == KeyEvent.KEY_PRESSED) {
        if ((myCurrentProcessor != null && myCurrentProcessor.isDragActive()) ||
            (PaletteToolWindowManager.getInstance(myEditor).getActiveItem(ComponentItem.class) != null &&
             myCurrentProcessor != myInsertComponentProcessor)) {
          myEditor.setDesignTimeInsets(12);
        }
      }
      else {
        myEditor.setDesignTimeInsets(2);
      }
    }
    if (myCurrentProcessor != null) {
      myCurrentProcessor.processKeyEvent(e);
    }
    else if (e.getID() == KeyEvent.KEY_TYPED && Character.isLetterOrDigit(e.getKeyChar()) &&
      (e.getModifiers() & (InputEvent.ALT_MASK | InputEvent.CTRL_MASK | InputEvent.META_MASK)) == 0) {
      final ArrayList<RadComponent> selection = FormEditingUtil.getAllSelectedComponents(myEditor);
      if (selection.size() > 0) {
        final RadComponent component = selection.get(0);
        final InplaceEditingLayer inplaceLayer = myEditor.getInplaceEditingLayer();
        inplaceLayer.startInplaceEditing(component, component.getDefaultInplaceProperty(),
                                         component.getDefaultInplaceEditorBounds(), new InplaceContext(false, e.getKeyChar()));
        e.consume();
      }
    }
  }

  public Point getLastMousePosition() {
    return myLastMousePosition;
  }

  @Override
  protected void processMouseEvent(final MouseEvent e){
    myLastMousePosition = e.getPoint();

    if (myCurrentProcessor != null && myCurrentProcessor.isDragActive()) {
      return;
    }

    // Here is a good place to handle right and wheel mouse clicking. All mouse
    // motion events should go further
    if (e.isPopupTrigger()) {
      RadComponent component = FormEditingUtil.getRadComponentAt(myEditor.getRootContainer(), e.getX(), e.getY());
      if (component != null && !component.isSelected()) {
        FormEditingUtil.selectSingleComponent(myEditor, component);
      }

      final ActionManager actionManager = ActionManager.getInstance();
      final ActionPopupMenu popupMenu = actionManager.createActionPopupMenu(
        ActionPlaces.GUI_DESIGNER_EDITOR_POPUP,
        (ActionGroup)actionManager.getAction(IdeActions.GROUP_GUI_DESIGNER_EDITOR_POPUP)
      );
      popupMenu.getComponent().show(e.getComponent(), e.getX(), e.getY());
      return;
    }

    final int id = e.getID();
    if(
      (MouseEvent.BUTTON2 == e.getButton() || MouseEvent.BUTTON3 == e.getButton()) &&
      (
        MouseEvent.MOUSE_PRESSED == id ||
        MouseEvent.MOUSE_RELEASED == id ||
        MouseEvent.MOUSE_CLICKED == id
      )
    ){
      return;
    }

    // Handle all left mouse events and all motion events
    final RadComponent componentAt = FormEditingUtil.getRadComponentAt(myEditor.getRootContainer(), e.getX(), e.getY());
    if (componentAt != null) {
      final Point p1 = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), componentAt.getDelegee());
      final Component deepestComponentAt = SwingUtilities.getDeepestComponentAt(componentAt.getDelegee(), p1.x, p1.y);
      final Point p2 = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), deepestComponentAt);

      EventProcessor processor = componentAt.getEventProcessor(e);
      if (processor != null) {
        myCurrentProcessor = processor;
      }
      else {
        final Component source = deepestComponentAt != null ? deepestComponentAt : componentAt.getDelegee();
        componentAt.processMouseEvent(new MouseEvent(source,
          id,
          e.getWhen(),
          e.getModifiers(),
          p2.x,
          p2.y,
          e.getClickCount(),
          e.isPopupTrigger(),
          e.getButton()
        ));
      }
    }

    Cursor cursor = Cursor.getDefaultCursor();
    if(id==MouseEvent.MOUSE_MOVED){
      if (PaletteToolWindowManager.getInstance(myEditor).getActiveItem(ComponentItem.class) != null) {
        if (myInsertFeedbackEnabled) {
          cursor = myInsertComponentProcessor.processMouseMoveEvent(e);
        }
      }
      else if (myCurrentProcessor != null) {
        myCurrentProcessor.processMouseEvent(e);
      }
      else {
        final RadComponent component = FormEditingUtil.getRadComponentAt(myEditor.getRootContainer(), e.getX(), e.getY());
        if (component != null) {
          final Point point = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), component.getDelegee());
          final int resizeMask = Painter.getResizeMask(component, point.x, point.y);
          if (resizeMask != 0) {
            cursor = Cursor.getPredefinedCursor(Painter.getResizeCursor(resizeMask));
          }
          updateDragger(e);
        }
      }
    }
    else if (id == MouseEvent.MOUSE_PRESSED) {
      processMousePressed(e);
    }
    else if (id == MouseEvent.MOUSE_RELEASED) {
      // not every press sets processor so its not a redundant 'if'
      if (myCurrentProcessor != null) {
        myCurrentProcessor.processMouseEvent(e);
        myCurrentProcessor = null;
      }
    }
    else if(id == MouseEvent.MOUSE_CLICKED){
      processMouseClicked(e);
    }
    else if (id == MouseEvent.MOUSE_EXITED) {
      myEditor.getActiveDecorationLayer().removeFeedback();
    }

    if (!e.isConsumed() && myCurrentProcessor != null) {
      myCurrentProcessor.processMouseEvent(e);
    }

    if (myCurrentProcessor != null && myCurrentProcessor.isDragActive()) {
      myEditor.getLayeredPane().setCursor(null);
    }
    else {
      if(myCurrentProcessor!=null && myCurrentProcessor.getCursor()!=null){
        cursor=myCurrentProcessor.getCursor();
      }
      myEditor.getLayeredPane().setCursor(cursor);
    }
  }

  private void updateDragger(final MouseEvent e) {
    final RadComponent component = FormEditingUtil.getRadComponentAt(myEditor.getRootContainer(), e.getX(), e.getY());

    LOG.assertTrue(component != null);

    // Dragger
    final RadComponent oldDraggerHost = FormEditingUtil.getDraggerHost(myEditor);
    RadComponent newDraggerHost = null;
    for (RadComponent c = component; c != null && !(c instanceof RadRootContainer); c = c.getParent()) {
      if (c.isSelected()) {
        newDraggerHost = c;
        break;
      }
    }

    boolean keepOldHost = false;

    if (oldDraggerHost != null && oldDraggerHost.isSelected()) {
      final Point p = SwingUtilities.convertPoint(oldDraggerHost.getDelegee(), 0, 0, e.getComponent());
      final int deltaX = e.getX() - p.x;
      final int deltaY = e.getY() - p.y;
      if(
        deltaX > -DRAGGER_SIZE && deltaX < oldDraggerHost.getWidth() &&
        deltaY > -DRAGGER_SIZE && deltaY < oldDraggerHost.getHeight()
      ){
        keepOldHost = true;
        newDraggerHost = null;
      }
    }

    boolean shouldRepaint = false;

    if (oldDraggerHost != null && !keepOldHost && oldDraggerHost != newDraggerHost){
      oldDraggerHost.setDragger(false);
      shouldRepaint = true;
    }

    if (newDraggerHost != null){
      newDraggerHost.setDragger(true);
      shouldRepaint = true;
    }

    if (shouldRepaint) {
      myEditor.repaintLayeredPane();
    }
  }

  private void removeDragger() {
    final RadComponent oldDraggerHost = FormEditingUtil.getDraggerHost(myEditor);
    if (oldDraggerHost != null) {
      oldDraggerHost.setDragger(false);
      myEditor.repaintLayeredPane();
    }
  }

  private void processMousePressed(final MouseEvent e){
    if(myCurrentProcessor != null){
      if (myCurrentProcessor.needMousePressed()) {
        myCurrentProcessor.processMouseEvent(e);
        return;
      }
      // Sun sometimes skips mouse released events...
      myCurrentProcessor.cancelOperation();
      myCurrentProcessor = null;
    }

    RadComponent component = null;
    final RadComponent draggerHost = FormEditingUtil.getDraggerHost(myEditor);
    // Try to understand whether we pressed inside dragger area
    if(draggerHost != null){
      final JComponent delegee = draggerHost.getDelegee();
      final Point p = SwingUtilities.convertPoint(delegee, 0, 0, e.getComponent());
      if(
        p.x - MainProcessor.DRAGGER_SIZE <= e.getX() && e.getX() <= p.x &&
        p.y - MainProcessor.DRAGGER_SIZE <= e.getY() && e.getY() <= p.y
      ){
        component = draggerHost;
      }
    }

    // If user clicked not inside dragger then we have find RadComponent at the click point
    if(component == null){
      component = FormEditingUtil.getRadComponentAt(myEditor.getRootContainer(), e.getX(), e.getY());
    }

    if (component == null) {
      return;
    }

    final ComponentItem selectedItem = PaletteToolWindowManager.getInstance(myEditor).getActiveItem(ComponentItem.class);
    if (selectedItem != null) {
      myInsertComponentProcessor.setSticky(UIUtil.isControlKeyDown(e));
      myCurrentProcessor = myInsertComponentProcessor;
      return;
    }

    if (!UIUtil.isControlKeyDown(e) && !e.isShiftDown()) {
      if (!component.isSelected() || FormEditingUtil.getSelectedComponents(myEditor).size() != 1) {
        FormEditingUtil.selectSingleComponent(myEditor, component);
      }
    }

    final Point point = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), component.getDelegee());
    final int resizeMask = Painter.getResizeMask(component, point.x, point.y);
    LOG.debug("MainProcessor.processMousePressed: resizeMask at (" + point.x + "," + point.y + ") is " + resizeMask);

    if (resizeMask != 0) {
      if (component.getParent() != null) {
        component = component.getParent().getActionTargetComponent(component);
      }
      myCurrentProcessor = new ResizeProcessor(myEditor, component, resizeMask);
    }
    else if (component instanceof RadRootContainer || e.isShiftDown()) {
      myCurrentProcessor = new GroupSelectionProcessor(myEditor, component);
    }
    else if (!e.isShiftDown()) {
      myCurrentProcessor = new DragSelectionProcessor(myEditor);
    }

    updateDragger(e);
  }

  private void processMouseClicked(final MouseEvent e){
    if(e.getClickCount() != 2){ // inplace editing starts with double click
      return;
    }
    myEditor.getInplaceEditingLayer().startInplaceEditing(e.getX(), e.getY());
  }

  @Override
  protected boolean cancelOperation(){
    if (myCurrentProcessor != null) {
      if (myCurrentProcessor.cancelOperation()){
        myCurrentProcessor = null;
        myEditor.getLayeredPane().setCursor(Cursor.getDefaultCursor());
        myEditor.getActiveDecorationLayer().removeFeedback();
        return true;
      }
    }
    else if (PaletteToolWindowManager.getInstance(myEditor).getActiveItem(ComponentItem.class) != null) {
      cancelPaletteInsert();
      return true;
    }
    return false;
  }

  void cancelPaletteInsert() {
    PaletteToolWindowManager.getInstance(myEditor).clearActiveItem();
    myEditor.getLayeredPane().setCursor(Cursor.getDefaultCursor());
    myEditor.getActiveDecorationLayer().removeFeedback();
  }

  public void setInsertFeedbackEnabled(final boolean enabled) {
    myInsertFeedbackEnabled = enabled;
  }

  public void startPasteProcessor(final ArrayList<RadComponent> componentsToPaste, final IntList xs, final IntList ys) {
    removeDragger();
    myEditor.hideIntentionHint();
    myCurrentProcessor = new PasteProcessor(myEditor, componentsToPaste, xs, ys);
    myCurrentProcessor.processMouseEvent(new MouseEvent(myEditor, MouseEvent.MOUSE_MOVED, 0, 0,
                                                        myLastMousePosition.x, myLastMousePosition.y,
                                                        1, false));
  }

  public void startInsertProcessor(@NotNull final ComponentItem componentToInsert, final ComponentDropLocation location) {
    removeDragger();
    myEditor.hideIntentionHint();
    myInsertComponentProcessor.setComponentToInsert(componentToInsert);
    myInsertComponentProcessor.setLastLocation(location);
    myCurrentProcessor = myInsertComponentProcessor;
  }

  public void stopCurrentProcessor() {
    myCurrentProcessor = null;
    myEditor.getLayeredPane().setCursor(null);
  }

  public boolean isProcessorActive() {
    return myCurrentProcessor != null;
  }
}