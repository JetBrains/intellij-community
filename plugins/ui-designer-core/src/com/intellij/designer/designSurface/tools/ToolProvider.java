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
package com.intellij.designer.designSurface.tools;

import com.intellij.designer.designSurface.EditOperation;
import com.intellij.designer.designSurface.EditableArea;
import com.intellij.designer.designSurface.ZoomProvider;
import com.intellij.designer.propertyTable.InplaceContext;
import com.intellij.util.ThrowableRunnable;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public abstract class ToolProvider implements ZoomProvider {
  private InputTool myTool;
  private EditableArea myArea;
  private MouseEvent myEvent;

  public void processKeyEvent(KeyEvent event, EditableArea area) {
      if (myTool != null) {
      try {
        switch (event.getID()) {
          case KeyEvent.KEY_PRESSED:
            myTool.keyPressed(event, area);
            break;
          case KeyEvent.KEY_TYPED:
            myTool.keyTyped(event, area);
            break;
          case KeyEvent.KEY_RELEASED:
            myTool.keyReleased(event, area);
            break;
        }
      }
      catch (Throwable e) {
        showError("Edit operation", e);
      }
    }
  }

  public void processMouseEvent(MouseEvent event, EditableArea area) {
    if (myTool != null) {
      try {
        switch (event.getID()) {
          case MouseEvent.MOUSE_PRESSED:
            myTool.mouseDown(event, area);
            if (event.isPopupTrigger()) {
              myTool.mousePopup(event, area);
            }
            break;
          case MouseEvent.MOUSE_RELEASED:
            myTool.mouseUp(event, area);
            if (event.isPopupTrigger()) {
              myTool.mousePopup(event, area);
            }
            break;
          case MouseEvent.MOUSE_ENTERED:
            myTool.mouseEntered(event, area);
            break;
          case MouseEvent.MOUSE_EXITED:
            myTool.mouseExited(event, area);
            break;
          case MouseEvent.MOUSE_CLICKED:
            if (event.getClickCount() == 2) {
              myTool.mouseDoubleClick(event, area);
            }
            if (event.isPopupTrigger()) {
              myTool.mousePopup(event, area);
            }
            break;
          case MouseEvent.MOUSE_MOVED:
            myTool.mouseMove(event, area);
            break;
          case MouseEvent.MOUSE_DRAGGED:
            myTool.mouseDrag(event, area);
            break;
        }
      }
      catch (Throwable e) {
        showError("Edit operation", e);
      }
    }
  }

  public void setEvent(MouseEvent event) {
    myEvent = event;
  }

  public void setArea(@Nullable EditableArea area) {
    myArea = area;
  }

  public abstract void showError(@NonNls String message, Throwable e);

  public InputTool getActiveTool() {
    return myTool;
  }

  public void setActiveTool(InputTool tool) {
    if (myTool != null) {
      myTool.deactivate();
    }

    myTool = tool;

    if (myTool != null) {
      myTool.setToolProvider(this);
      myTool.activate();

      // hack: update cursor
      if (myArea != null) {
        myTool.setArea(myArea);
        myTool.refreshCursor();
        try {
          myTool.mouseMove(myEvent, myArea);
        }
        catch (Exception e) {
          showError("Edit operation", e);
        }
      }
    }
  }

  public abstract void loadDefaultTool();

  public abstract boolean execute(ThrowableRunnable<Exception> operation, String command, boolean updateProperties);

  public abstract void executeWithReparse(ThrowableRunnable<Exception> operation, String command);

  public abstract void execute(List<EditOperation> operations, String command);

  public abstract void startInplaceEditing(@Nullable InplaceContext inplaceContext);

  public abstract void hideInspections();
}