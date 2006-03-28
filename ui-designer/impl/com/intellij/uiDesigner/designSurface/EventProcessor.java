package com.intellij.uiDesigner.designSurface;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public abstract class EventProcessor {
  private Cursor myCursor;

  public final Cursor getCursor(){
    return myCursor;
  }

  public final void setCursor(final Cursor cursor){
    myCursor = cursor;
  }

  protected abstract void processKeyEvent(KeyEvent e);

  protected abstract void processMouseEvent(MouseEvent e);
  
  /**
   * @return true if processor cancelled its operation; false otherwise
   */ 
  protected abstract boolean cancelOperation();

  public boolean isDragActive() {
    return false;
  }

  public boolean needMousePressed() {
    return false;
  }

  protected static DropLocation.Direction directionFromKey(final int keyCode) {
    switch(keyCode) {
      case KeyEvent.VK_RIGHT: return DropLocation.Direction.RIGHT;
      case KeyEvent.VK_LEFT: return DropLocation.Direction.LEFT;
      case KeyEvent.VK_UP: return DropLocation.Direction.UP;
      case KeyEvent.VK_DOWN: return DropLocation.Direction.DOWN;
      case KeyEvent.VK_END: return DropLocation.Direction.RIGHT;
      case KeyEvent.VK_HOME: return DropLocation.Direction.LEFT;
      case KeyEvent.VK_PAGE_UP: return DropLocation.Direction.UP;
      case KeyEvent.VK_PAGE_DOWN: return DropLocation.Direction.DOWN;
      default: return null;
    }
  }

  private static boolean isMoveToLast(final int keyCode) {
    return keyCode == KeyEvent.VK_HOME || keyCode == KeyEvent.VK_END ||
           keyCode == KeyEvent.VK_PAGE_UP || keyCode == KeyEvent.VK_PAGE_DOWN;
  }

  protected static DropLocation moveDropLocation(final GuiEditor editor, final DropLocation location,
                                                 final ComponentDragObject dragObject, final KeyEvent e) {
    DropLocation.Direction dir = directionFromKey(e.getKeyCode());
    boolean moveToLast = isMoveToLast(e.getKeyCode());
    if (dir != null && location != null) {
      DropLocation adjacentLocation;
      if (moveToLast) {
        adjacentLocation = location.getAdjacentLocation(dir);
        DropLocation lastLocation = location;
        while(adjacentLocation != null) {
          if (adjacentLocation.canDrop(dragObject)) {
            lastLocation = adjacentLocation;
          }
          adjacentLocation = adjacentLocation.getAdjacentLocation(dir);
        }
        adjacentLocation = lastLocation;
      }
      else {
        adjacentLocation = location.getAdjacentLocation(dir);
        while(adjacentLocation != null && !adjacentLocation.canDrop(dragObject)) {
          adjacentLocation = adjacentLocation.getAdjacentLocation(dir);
        }
      }

      if (adjacentLocation != null && adjacentLocation.canDrop(dragObject)) {
        adjacentLocation.placeFeedback(editor.getActiveDecorationLayer(), dragObject);
        return adjacentLocation;
      }
    }
    return location;
  }

}
