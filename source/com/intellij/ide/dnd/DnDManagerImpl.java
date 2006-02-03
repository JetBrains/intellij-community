/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ide.dnd;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.awt.RelativeRectangle;
import com.intellij.util.ui.GeometryUtil;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;
import java.awt.dnd.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class DnDManagerImpl extends DnDManager implements ProjectComponent, DnDEvent.DropTargetHighlightingType {
  private static final Logger LOG = Logger.getInstance("com.intellij.ide.dnd.DnDManager");

  static final @NonNls String SOURCE_KEY = "DnD Source";
  static final @NonNls String TARGET_KEY = "DnD Target";

  private DnDEventImpl myCurrentEvent;
  private DnDEvent myLastHighlightedEvent;

  private static DnDTarget NULL_TARGET = new NullTarget();

  private DnDTarget myLastProcessedTarget = NULL_TARGET;
  private DragSourceContext myCurrentDragContext;

  private Component myLastProcessedOverComponent;
  private Point myLastProcessedPoint;
  private String myLastMessage;
  private DnDEvent myLastProcessedEvent;

  private DragSourceListener myDragSourceListener = new MyDragSourceListener();
  private DragGestureListener myDragGestureListener = new MyDragGestureListnener();
  private DropTargetListener myDropTargetListener = new MyDropTargetListener();

  private Timer myTooltipTimer = new Timer(ToolTipManager.sharedInstance().getInitialDelay(), new ActionListener() {
    public void actionPerformed(ActionEvent e) {
      onTimer();
    }
  });
  private Runnable myHightlighterShowRequest;
  private Rectangle myLastHighlightedRec;
  private int myLastProcessedAction;

  public void projectOpened() {
  }

  public void projectClosed() {
  }

  public String getComponentName() {
    return "FabriqueDnDManager";
  }

  public void initComponent() {
    myTooltipTimer.start();
  }

  public void disposeComponent() {
    myTooltipTimer.stop();
  }

  public void registerSource(DnDSource source, JComponent component) {
    component.putClientProperty(SOURCE_KEY, source);
    final DragSource defaultDragSource = DragSource.getDefaultDragSource();
    defaultDragSource.createDefaultDragGestureRecognizer(component, DnDConstants.ACTION_COPY_OR_MOVE, myDragGestureListener);
  }

  public void unregisterSource(DnDSource source, JComponent component) {
    component.putClientProperty(SOURCE_KEY, null);
  }

  public void registerTarget(DnDTarget target, JComponent component) {
    component.putClientProperty(TARGET_KEY, target);
    new DropTarget(component, DnDConstants.ACTION_COPY_OR_MOVE, myDropTargetListener);
  }

  public void unregisterTarget(DnDTarget target, JComponent component) {
    component.putClientProperty(TARGET_KEY, null);
  }

  private void updateCurrentEvent(Component aComponentOverDragging, Point aPoint, int nativeAction) {
    LOG.debug("updateCurrentEvent: " + aComponentOverDragging);
    if (myCurrentDragContext == null) return;

    myCurrentEvent.updateAction(getDnDActionForPlatformAction(nativeAction));
    myCurrentEvent.setPoint(aPoint);
    myCurrentEvent.setHandlerComponent(aComponentOverDragging);

    boolean samePoint = myCurrentEvent.getPoint().equals(myLastProcessedPoint);
    boolean sameComponent = myCurrentEvent.getCurrentOverComponent().equals(myLastProcessedOverComponent);
    boolean sameAction = (nativeAction == myLastProcessedAction);

    LOG.debug("updateCurrentEvent: point:" + aPoint);
    LOG.debug("updateCurrentEvent: action:" + nativeAction);

    if (samePoint && sameComponent && sameAction) {
      return;
    }

    DnDTarget target = getTarget(aComponentOverDragging);
    DnDTarget immediateTarget = target;
    Component eachParent = aComponentOverDragging;

    LOG.debug("updateCurrentEvent: action:" + nativeAction);

    while (true) {
      boolean canGoToParent = update(target);

      if (myCurrentEvent.isDropPossible()) {
        if (myCurrentEvent.wasDelegated()) {
          target = myCurrentEvent.getDelegatedTarget();
        }
        break;
      }

      if (!canGoToParent) {
        break;
      }

      eachParent = findAllowedParentComponent(eachParent);
      if (eachParent == null) {
        break;
      }

      target = getTarget(eachParent);
    }

    LOG.debug("updateCurrentEvent: target:" + target);
    LOG.debug("updateCurrentEvent: immediateTarget:" + immediateTarget);

    if (!myCurrentEvent.isDropPossible() && !immediateTarget.equals(target)) {
      update(immediateTarget);
    }

    updateCursor();

    final Container current = (Container)myCurrentEvent.getCurrentOverComponent();
    final Point point = myCurrentEvent.getPointOn(getLayeredPane(current));
    Rectangle inPlaceRect = new Rectangle(point.x - 5, point.y - 5, 5, 5);

    if (!myCurrentEvent.equals(myLastProcessedEvent)) {
      hideCurrentHighlighter();
    }

    boolean sameTarget = myLastProcessedTarget.equals(target);
    if (sameTarget) {
      if (myCurrentEvent.isDropPossible()) {
        if (!myLastProcessedPoint.equals(myCurrentEvent.getPoint())) {
          if (!Highlighters.isVisibleExcept(TEXT | ERROR_TEXT)) {
            hideCurrentHighlighter();
            restartTimer();
            queueTooltip(myCurrentEvent, getLayeredPane(current), inPlaceRect);
          }
        }
      }
      else {
        if (!myLastProcessedPoint.equals(myCurrentEvent.getPoint())) {
          hideCurrentHighlighter();
          restartTimer();
          queueTooltip(myCurrentEvent, getLayeredPane(current), inPlaceRect);
        }
      }
    }
    else {
      hideCurrentHighlighter();
      myLastProcessedTarget.cleanUpOnLeave();
      myCurrentEvent.clearDropHandler();
      restartTimer();

      if (!myCurrentEvent.isDropPossible()) {
        queueTooltip(myCurrentEvent, getLayeredPane(current), inPlaceRect);
      }
    }

    myLastProcessedTarget = target;
    myLastProcessedPoint = myCurrentEvent.getPoint();
    myLastProcessedOverComponent = myCurrentEvent.getCurrentOverComponent();
    myLastProcessedAction = myCurrentEvent.getAction().getActionId();
    myLastProcessedEvent = (DnDEvent)myCurrentEvent.clone();
  }

  private void updateCursor() {
    Cursor cursor;
    if (myCurrentEvent.isDropPossible()) {
      cursor = myCurrentEvent.getCursor();
      if (cursor == null) {
        cursor = myCurrentEvent.getAction().getCursor();
      }

    }
    else {
      cursor = myCurrentEvent.getAction().getRejectCursor();
    }

    myCurrentDragContext.setCursor(cursor);
  }

  private void restartTimer() {
    myTooltipTimer.restart();
  }

  private boolean update(DnDTarget target) {
    LOG.debug("update target:" + target);

    myCurrentEvent.clearDelegatedTarget();
    final boolean canGoToParent = target.update(myCurrentEvent);


    String message;
    if (isMessageProvided(myCurrentEvent)) {
      message = myCurrentEvent.getExpectedDropResult();
    }
    else {
      message = "";
    }

    //final WindowManager wm = WindowManager.getInstance();
    //final StatusBar statusBar = wm.getStatusBar(target.getProject());
    //statusBar.setInfo(message);

    if (myLastMessage != null && !myLastMessage.equals(message)) {
      hideCurrentHighlighter();
    }
    myLastMessage = message;

    return canGoToParent;
  }

  private static Component findAllowedParentComponent(Component aComponentOverDragging) {
    Component eachParent = aComponentOverDragging;
    while (true) {
      eachParent = eachParent.getParent();
      if (eachParent == null) {
        return null;
      }

      final DnDTarget target = getTarget(eachParent);
      if (target != NULL_TARGET) {
        return eachParent;
      }
    }
  }

  private static DnDSource getSource(Component component) {
    if (component instanceof JComponent) {
      return (DnDSource)((JComponent)component).getClientProperty(SOURCE_KEY);
    }
    return null;
  }

  private static DnDTarget getTarget(Component component) {
    if (component instanceof JComponent) {
      DnDTarget target = (DnDTarget)((JComponent)component).getClientProperty(TARGET_KEY);
      if (target != null) return target;
    }

    return NULL_TARGET;
  }

  void showHighlighter(final Component aComponent, final int aType, final DnDEvent aEvent) {
    final Rectangle bounds = aComponent.getBounds();
    final Container parent = aComponent.getParent();

    showHighlighter(parent, aEvent, bounds, aType);
  }

  void showHighlighter(final RelativeRectangle rectangle, final int aType, final DnDEvent aEvent) {
    final JLayeredPane layeredPane = getLayeredPane(rectangle.getPoint().getComponent());
    final Rectangle bounds = rectangle.getRectangleOn(layeredPane);

    showHighlighter(layeredPane, aEvent, bounds, aType);
  }

  void showHighlighter(JLayeredPane layeredPane, final RelativeRectangle rectangle, final int aType, final DnDEvent event) {
    final Rectangle bounds = rectangle.getRectangleOn(layeredPane);
    showHighlighter(layeredPane, event, bounds, aType);
  }

  private boolean isEventBeingHighlighted(DnDEvent event) {
    return event.equals(getLastHighlightedEvent());
  }

  private void showHighlighter(final Component parent, final DnDEvent aEvent, final Rectangle bounds, final int aType) {
    final JLayeredPane layeredPane = getLayeredPane(parent);
    if (layeredPane == null) {
      return;
    }

    if (isEventBeingHighlighted(aEvent)) {
      if (GeometryUtil.isWithin(myLastHighlightedRec, aEvent.getPointOn(layeredPane))) {
        return;
      }
    }

    final Rectangle rectangle = SwingUtilities.convertRectangle(parent, bounds, layeredPane);
    setLastHighlightedEvent((DnDEvent)((DnDEventImpl)aEvent).clone(), rectangle);

    Highlighters.hide();

    Highlighters.show(aType, layeredPane, rectangle, aEvent);

    if (isMessageProvided(aEvent)) {
      queueTooltip(aEvent, layeredPane, rectangle);
    }
    else {
      Highlighters.hide(TEXT | ERROR_TEXT);
    }
  }

  private void queueTooltip(final DnDEvent aEvent, final JLayeredPane aLayeredPane, final Rectangle aRectangle) {
    myHightlighterShowRequest = new Runnable() {
      public void run() {
        if (myCurrentEvent != aEvent) return;
        Highlighters.hide(TEXT | ERROR_TEXT);
        if (aEvent.isDropPossible()) {
          Highlighters.show(TEXT, aLayeredPane, aRectangle, aEvent);
        }
        else {
          Highlighters.show(ERROR_TEXT, aLayeredPane, aRectangle, aEvent);
        }
      }
    };
  }

  private static boolean isMessageProvided(final DnDEvent aEvent) {
    return aEvent.getExpectedDropResult() != null && aEvent.getExpectedDropResult().trim().length() > 0;
  }

  void hideCurrentHighlighter() {
    Highlighters.hide();
    myHightlighterShowRequest = null;
    setLastHighlightedEvent(null, null);
  }

  private void onTimer() {
    if (myHightlighterShowRequest != null) {
      myHightlighterShowRequest.run();
      myHightlighterShowRequest = null;
    }
  }

  private static JLayeredPane getLayeredPane(Component aComponent) {
    if (aComponent == null) return null;

    if (aComponent instanceof JLayeredPane) {
      return (JLayeredPane)aComponent;
    }

    if (aComponent instanceof JFrame) {
      return ((JFrame)aComponent).getRootPane().getLayeredPane();
    }

    if (aComponent instanceof JDialog) {
      return ((JDialog)aComponent).getRootPane().getLayeredPane();
    }

    final JFrame frame = ((JFrame)SwingUtilities.getWindowAncestor(aComponent));

    if (frame == null) {
      return null;
    }

    return frame.getLayeredPane();
  }

  private static class NullTarget implements DnDTarget {
    public boolean update(DnDEvent aEvent) {
      aEvent.setDropPossible(false, "You cannot drop anything here");
      return false;
    }

    public void drop(DnDEvent aEvent) {
    }

    public void cleanUpOnLeave() {
    }
  }

  DnDEvent getCurrentEvent() {
    return myCurrentEvent;
  }

  private DnDEvent getLastHighlightedEvent() {
    return myLastHighlightedEvent;
  }

  private void setLastHighlightedEvent(DnDEvent lastHighlightedEvent, Rectangle aRectangle) {
    myLastHighlightedEvent = lastHighlightedEvent;
    myLastHighlightedRec = aRectangle;
  }

  private void resetCurrentEvent(@NonNls String s) {
    myCurrentEvent = null;
    LOG.debug("Reset Current Event: " + s);
  }

  private class MyDragGestureListnener implements DragGestureListener {
    public void dragGestureRecognized(DragGestureEvent dge) {
      final DnDSource source = getSource(dge.getComponent());
      if (source == null) return;

      DnDAction action = getDnDActionForPlatformAction(dge.getDragAction());
      if (source.canStartDragging(action, dge.getDragOrigin())) {

        if (myCurrentEvent == null) {
          // Actually, under Linux it is possible to get 2 or more dragGestureRecognized calls for single drag
          // operation. To reproduce:
          // 1. Do D-n-D in Styles tree
          // 2. Make an attempt to do D-n-D in Services tree
          // 3. Do D-n-D in Styles tree again.

          LOG.debug("Starting dragging for " + action);
          hideCurrentHighlighter();
          final DnDDragStartBean dnDDragStartBean = source.startDragging(action, dge.getDragOrigin());
          myCurrentEvent = new DnDEventImpl(DnDManagerImpl.this, action, dnDDragStartBean.getAttachedObject(), dnDDragStartBean.getPoint());
          myCurrentEvent.setOrgPoint(dge.getDragOrigin());

          dge.startDrag(DragSource.DefaultCopyDrop, myCurrentEvent, myDragSourceListener);

          // check if source is also a target
          //        DnDTarget target = getTarget(dge.getComponent());
          //        if( target != null ) {
          //          target.update(myCurrentEvent);
          //        }
        }
      }
    }

  }

  private static DnDAction getDnDActionForPlatformAction(int platformAction) {
    DnDAction action = null;
    switch (platformAction) {
      case DnDConstants.ACTION_COPY :
        action = DnDAction.ADD;
        break;
      case DnDConstants.ACTION_MOVE :
        action = DnDAction.MOVE;
        break;
      case DnDConstants.ACTION_LINK:
        action = DnDAction.BIND;
        break;
      default:
        break;
    }

    return action;
  }

  private class MyDragSourceListener implements DragSourceListener {
    public void dragEnter(DragSourceDragEvent dsde) {
      LOG.debug("dragEnter:" + dsde.getDragSourceContext().getComponent());
      myCurrentDragContext = dsde.getDragSourceContext();
    }

    public void dragOver(DragSourceDragEvent dsde) {
      LOG.debug("dragOver:" + dsde.getDragSourceContext().getComponent());
      myCurrentDragContext = dsde.getDragSourceContext();
    }

    public void dropActionChanged(DragSourceDragEvent dsde) {
    }

    public void dragDropEnd(DragSourceDropEvent dsde) {
      myLastProcessedTarget.cleanUpOnLeave();
      resetCurrentEvent("dragDropEnd:" + dsde.getDragSourceContext().getComponent());
      Highlighters.hide(TEXT | ERROR_TEXT);
    }

    public void dragExit(DragSourceEvent dse) {
      LOG.debug("Stop dragging1");
      onDragExit();
    }
  }

  private class MyDropTargetListener implements DropTargetListener {
    public void drop(final DropTargetDropEvent dtde) {
      try {
        final Component component = dtde.getDropTargetContext().getComponent();
        updateCurrentEvent(component, dtde.getLocation(), dtde.getDropAction());

        if (myCurrentEvent.isDropPossible()) {
          dtde.acceptDrop(dtde.getDropAction());

          // do not wrap this into WriteAction!
          doDrop(component);

          if (myCurrentEvent.shouldRemoveHighlightings()) {
            hideCurrentHighlighter();
          }
          dtde.dropComplete(true);
        }
        else {
          dtde.rejectDrop();
        }
      }
      catch (Throwable e) {
        LOG.error(e);
        dtde.rejectDrop();
      }
      finally {
        resetCurrentEvent("Stop dragging2");
      }
    }

    private void doDrop(Component component) {
      if (myCurrentEvent.canHandleDrop()) {
        myCurrentEvent.handleDrop();
      }
      else {
        getTarget(component).drop(myCurrentEvent);
      }
    }

    public void dragOver(DropTargetDragEvent dtde) {
      updateCurrentEvent(dtde.getDropTargetContext().getComponent(), dtde.getLocation(), dtde.getDropAction());
    }

    public void dragExit(DropTargetEvent dte) {
      onDragExit();
    }

    public void dragEnter(DropTargetDragEvent dtde) {

    }

    public void dropActionChanged(DropTargetDragEvent dtde) {
      updateCurrentEvent(dtde.getDropTargetContext().getComponent(), dtde.getLocation(), dtde.getDropAction());
    }
  }

  private void onDragExit() {
    if (myCurrentDragContext != null) {
      Cursor cursor = DragSource.DefaultCopyNoDrop;
      myCurrentDragContext.setCursor(cursor);
    }

    myLastProcessedTarget.cleanUpOnLeave();
    hideCurrentHighlighter();
    //FabriqueStatusBar.setStatusText("");
    myHightlighterShowRequest = null;
  }

}
