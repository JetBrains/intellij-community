package com.intellij.uiDesigner;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.uiDesigner.componentTree.ComponentSelectionListener;
import com.intellij.uiDesigner.palette.ComponentItem;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class MainProcessor extends EventProcessor{
  private static final Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.MainProcessor");

  public static final int DRAGGER_SIZE = 10;

  private EventProcessor myCurrentProcessor;

  private final GuiEditor myEditor;

  public MainProcessor(final GuiEditor editor){
    if (editor == null){
      throw new IllegalArgumentException("editor cannot be null");
    }
    myEditor = editor;
    myEditor.addComponentSelectionListener(new MyComponentSelectionListener());
  }

  protected void processKeyEvent(final KeyEvent e){
    if (!myEditor.isEditable()) {
      return;
    }

    if (myCurrentProcessor != null) {
      myCurrentProcessor.processKeyEvent(e);
    }
  }

  protected void processMouseEvent(final MouseEvent e){
    // Here is a good place to handle right and wheel mouse clicking. All mouse
    // motion events should go further
    final int id = e.getID();
    if(
      (MouseEvent.BUTTON2 == e.getButton() || MouseEvent.BUTTON3 == e.getButton()) &&
      (
        MouseEvent.MOUSE_PRESSED == id ||
        MouseEvent.MOUSE_RELEASED == id ||
        MouseEvent.MOUSE_CLICKED == id
      )
    ){
      if (e.isPopupTrigger()) {
        final ActionManager actionManager = ActionManager.getInstance();
        final ActionPopupMenu popupMenu = actionManager.createActionPopupMenu(
          ActionPlaces.GUI_DESIGNER_EDITOR_POPUP,
          (ActionGroup)actionManager.getAction(IdeActions.GROUP_GUI_DESIGNER_EDITOR_POPUP)
        );
        popupMenu.getComponent().show(e.getComponent(), e.getX(), e.getY());
      }
      return;
    }

    // Handle all left mouse events and all motion events
    final RadComponent componentAt = FormEditingUtil.getRadComponentAt(myEditor, e.getX(), e.getY());
    if (componentAt != null) {
      final Point p1 = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), componentAt.getDelegee());
      final Component deepestComponentAt = SwingUtilities.getDeepestComponentAt(componentAt.getDelegee(), p1.x, p1.y);
      final Point p2 = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), deepestComponentAt);

      componentAt.processMouseEvent(new MouseEvent(
        deepestComponentAt,
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

    Cursor cursor = Cursor.getDefaultCursor();
    if(id==MouseEvent.MOUSE_MOVED){
      if (myEditor.getPalettePanel().getActiveItem() != null) {
        cursor = FormEditingUtil.getDropCursor(myEditor, e.getX(), e.getY(), 1);
      }
      else {
        final RadComponent component = FormEditingUtil.getRadComponentAt(myEditor, e.getX(), e.getY());
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

    if (!e.isConsumed() && myCurrentProcessor != null) {
      myCurrentProcessor.processMouseEvent(e);
    }

    if(myCurrentProcessor!=null && myCurrentProcessor.getCursor()!=null){
      cursor=myCurrentProcessor.getCursor();
    }
    myEditor.getLayeredPane().setCursor(cursor);
  }

  private void updateDragger(final MouseEvent e){
    final RadComponent component = FormEditingUtil.getRadComponentAt(myEditor, e.getX(), e.getY());
    
    LOG.assertTrue(component != null);

    // Dragger
    final RadComponent oldDraggerHost = FormEditingUtil.getDraggerHost(myEditor);
    RadComponent newDraggerHost = null;
    for (RadComponent c = component; !(c instanceof RadRootContainer); c = c.getParent()){
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

  private void processMousePressed(final MouseEvent e){
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
      component = FormEditingUtil.getRadComponentAt(myEditor, e.getX(), e.getY());
    }

    if (component == null) {
      return;
    }

    if (!myEditor.isEditable()) {
      return;
    }

    final ComponentItem selectedItem = myEditor.getPalettePanel().getActiveItem();
    if (selectedItem != null) {
      myCurrentProcessor = new InsertComponentProcessor(myEditor, myEditor.getPalettePanel(), e.isControlDown());
      return;
    }

    if (e.isControlDown()) {
      component.setSelected(!(component.isSelected()));
    }
    else if (e.isShiftDown()) {
      // Do not select component is shift is pressed
    }
    else {
      if (!component.isSelected()) {
        FormEditingUtil.clearSelection(myEditor.getRootContainer());
        component.setSelected(true);
      }
    }

    if(myCurrentProcessor != null){
      // Sun sometimes skips mouse released events...
      myCurrentProcessor.cancelOperation();
      myCurrentProcessor = null;
    }

    final Point point = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), component.getDelegee());
    final int resizeMask = Painter.getResizeMask(component, point.x, point.y);

    if (resizeMask != 0) {
      if (component.getParent().isXY()) {
        myCurrentProcessor = new ResizeProcessor(myEditor, component, resizeMask);
      }
    }
    else if (component instanceof RadRootContainer || ((component instanceof RadContainer) && e.isShiftDown())) {
      myCurrentProcessor = new GroupSelectionProcessor(myEditor, (RadContainer)component);
    }
    else if (!e.isShiftDown()) {
      myCurrentProcessor = new DragSelectionProcessor(myEditor);
    }

    updateDragger(e);
  }

  private void processMouseClicked(final MouseEvent e){
    if (!myEditor.isEditable()) {
      return;
    }

    if(e.getClickCount() != 2){ // inplace editing starts with double click
      return;
    }
    myEditor.getInplaceEditingLayer().startInplaceEditing(e.getX(), e.getY());
  }

  protected boolean cancelOperation(){
    if (myCurrentProcessor != null) {
      if (myCurrentProcessor.cancelOperation()){
        myCurrentProcessor = null;
        myEditor.getLayeredPane().setCursor(Cursor.getDefaultCursor());
        return true;
      }
    }
    else if (myEditor.getPalettePanel().getActiveItem() != null) {
      myEditor.getPalettePanel().clearActiveItem();
      myEditor.getLayeredPane().setCursor(Cursor.getDefaultCursor());
      return true;
    }
    return false;
  }

  private final class MyComponentSelectionListener implements ComponentSelectionListener{
    public void selectedComponentChanged(final GuiEditor source) {
      // TODO[vova] stop inplace editing
    }
  }
}