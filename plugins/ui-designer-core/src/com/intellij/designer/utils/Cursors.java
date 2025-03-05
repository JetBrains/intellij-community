// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.designer.utils;

import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * @author Alexander Lobas
 */
public final class Cursors {
  public static final Cursor RESIZE_ALL = Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR);
  public static final Cursor CROSS = Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR);
  public static Cursor NO_CURSOR;

  public static Cursor getSystemNoCursor() {
    try {
      return Cursor.getSystemCustomCursor("MoveNoDrop.32x32");
    }
    catch (Exception ex) {
      return Cursor.getDefaultCursor();
    }
  }

  // TODO: all platform???
  public static Cursor getNoCursor() {
    if (NO_CURSOR == null) {
      Toolkit toolkit = Toolkit.getDefaultToolkit();
      Image image = toolkit.createImage(Cursors.class.getResource("no-cursor.gif"));
      NO_CURSOR = toolkit.createCustomCursor(image, new Point(), "No_Cursor");
    }
    return NO_CURSOR;
  }

  // TODO: replace on better cursor (self image)
  public static Cursor getMoveCursor() {
    try {
      return Cursor.getSystemCustomCursor("MoveDrop.32x32");
    }
    catch (Exception ex) {
      return Cursor.getDefaultCursor();
    }
  }

  // TODO: replace on better cursor (self image)
  public static Cursor getCopyCursor() {
    try {
      return Cursor.getSystemCustomCursor("CopyDrop.32x32");
    }
    catch (Exception ex) {
      return Cursor.getDefaultCursor();
    }
  }

  public static @Nullable Cursor getResizeCursor(int direction) {
    int cursor;
    if (direction == Position.NORTH_WEST) {
      cursor = Cursor.NW_RESIZE_CURSOR;
    }
    else if (direction == Position.NORTH) {
      cursor = Cursor.N_RESIZE_CURSOR;
    }
    else if (direction == Position.NORTH_EAST) {
      cursor = Cursor.NE_RESIZE_CURSOR;
    }
    else if (direction == Position.WEST) {
      cursor = Cursor.W_RESIZE_CURSOR;
    }
    else if (direction == Position.EAST) {
      cursor = Cursor.E_RESIZE_CURSOR;
    }
    else if (direction == Position.SOUTH_WEST) {
      cursor = Cursor.SW_RESIZE_CURSOR;
    }
    else if (direction == Position.SOUTH) {
      cursor = Cursor.S_RESIZE_CURSOR;
    }
    else if (direction == Position.SOUTH_EAST) {
      cursor = Cursor.SE_RESIZE_CURSOR;
    }
    else {
      return null;
    }
    return Cursor.getPredefinedCursor(cursor);
  }
}