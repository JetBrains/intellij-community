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
package com.intellij.designer.designSurface;

import com.intellij.designer.designSurface.tools.InputTool;
import com.intellij.designer.designSurface.tools.ToolProvider;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.ui.popup.PopupOwner;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;

/**
 * @author Alexander Lobas
 */
public final class GlassLayer extends JComponent implements PopupOwner, DataProvider {
  private final ToolProvider myToolProvider;
  private final EditableArea myArea;

  public GlassLayer(ToolProvider provider, EditableArea area) {
    myToolProvider = provider;
    myArea = area;
    enableEvents(AWTEvent.KEY_EVENT_MASK | AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK);
  }

  @Override
  protected void processKeyEvent(KeyEvent event) {
    try {
      InputTool tool = myToolProvider.getActiveTool();

      switch (event.getID()) {
        case KeyEvent.KEY_PRESSED:
          tool.keyPressed(event, myArea);
          break;
        case KeyEvent.KEY_TYPED:
          tool.keyTyped(event, myArea);
          break;
        case KeyEvent.KEY_RELEASED:
          tool.keyReleased(event, myArea);
          break;
      }
    }
    catch (Throwable e) {
      handleException(e);
    }
    if (!event.isConsumed()) {
      super.processKeyEvent(event);
    }
  }

  @Override
  protected void processMouseEvent(MouseEvent event) {
    if (event.getID() == MouseEvent.MOUSE_PRESSED) {
      requestFocusInWindow();
    }
    try {
      InputTool tool = myToolProvider.getActiveTool();

      switch (event.getID()) {
        case MouseEvent.MOUSE_PRESSED:
          tool.mouseDown(event, myArea);
          break;
        case MouseEvent.MOUSE_RELEASED:
          tool.mouseUp(event, myArea);
          break;
        case MouseEvent.MOUSE_ENTERED:
          tool.mouseEntered(event, myArea);
          break;
        case MouseEvent.MOUSE_EXITED:
          tool.mouseExited(event, myArea);
          break;
        case MouseEvent.MOUSE_CLICKED:
          if (event.getClickCount() == 2) {
            tool.mouseDoubleClick(event, myArea);
          }
          break;
      }
    }
    catch (Throwable e) {
      handleException(e);
    }
  }

  @Override
  protected void processMouseMotionEvent(MouseEvent event) {
    try {
      InputTool tool = myToolProvider.getActiveTool();

      switch (event.getID()) {
        case MouseEvent.MOUSE_MOVED:
          tool.mouseMove(event, myArea);
          break;
        case MouseEvent.MOUSE_DRAGGED:
          tool.mouseDrag(event, myArea);
          break;
      }
    }
    catch (Throwable e) {
      handleException(e);
    }
  }

  private void handleException(Throwable e) {
    myToolProvider.showError("Edit error: ", e);
  }

  @Override
  public Point getBestPopupPosition() {
    return null;  // TODO: Auto-generated method stub
  }

  @Override
  public Object getData(@NonNls String dataId) {
    return null;  // TODO: Auto-generated method stub
  }
}