/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

  @Nullable
  public static Cursor getResizeCursor(int direction) {
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