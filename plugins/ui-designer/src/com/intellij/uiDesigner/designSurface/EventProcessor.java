// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.designSurface;

import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;

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

  protected static ComponentDropLocation.Direction directionFromKey(final int keyCode) {
    return switch (keyCode) {
      case KeyEvent.VK_RIGHT, KeyEvent.VK_END -> ComponentDropLocation.Direction.RIGHT;
      case KeyEvent.VK_LEFT, KeyEvent.VK_HOME -> ComponentDropLocation.Direction.LEFT;
      case KeyEvent.VK_UP, KeyEvent.VK_PAGE_UP -> ComponentDropLocation.Direction.UP;
      case KeyEvent.VK_DOWN, KeyEvent.VK_PAGE_DOWN -> ComponentDropLocation.Direction.DOWN;
      default -> null;
    };
  }

  private static boolean isMoveToLast(final int keyCode) {
    return keyCode == KeyEvent.VK_HOME || keyCode == KeyEvent.VK_END ||
           keyCode == KeyEvent.VK_PAGE_UP || keyCode == KeyEvent.VK_PAGE_DOWN;
  }

  protected static @Nullable ComponentDropLocation moveDropLocation(final GuiEditor editor, final ComponentDropLocation location,
                                                                    final ComponentDragObject dragObject, final KeyEvent e) {
    ComponentDropLocation.Direction dir = directionFromKey(e.getKeyCode());
    boolean moveToLast = isMoveToLast(e.getKeyCode());
    if (dir != null && location != null) {
      e.consume();
      ComponentDropLocation adjacentLocation;
      if (moveToLast) {
        adjacentLocation = location.getAdjacentLocation(dir);
        ComponentDropLocation lastLocation = location;
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
