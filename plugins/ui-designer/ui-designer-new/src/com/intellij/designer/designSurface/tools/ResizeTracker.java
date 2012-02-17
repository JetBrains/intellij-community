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
import com.intellij.designer.designSurface.OperationContext;
import com.intellij.designer.model.RadComponent;
import com.intellij.designer.utils.Cursors;
import com.intellij.designer.utils.Position;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public class ResizeTracker extends InputTool {
  private OperationContext myContext;
  private List<EditOperation> myOperations;
  private boolean myShowFeedback;
  private final int myDirection;

  public ResizeTracker(int direction, Object type) {
    myDirection = direction;
    myContext = new OperationContext(type);
    myContext.setResizeDirection(direction);
    setDefaultCursor(Cursors.getResizeCursor(direction));
    setDisabledCursor(Cursors.getNoCursor());
  }

  @Override
  public void deactivate() {
    eraseFeedback();
    myContext = null;
    myOperations = null;
    super.deactivate();
  }

  @Override
  protected Cursor calculateCursor() {
    if (myState == STATE_DRAG) {
      return getDefaultCursor();
    }
    return super.calculateCursor();
  }

  @Override
  protected void handleButtonDown(int button) {
    if (button == 1) {
      if (myState == STATE_INIT) {
        myState = STATE_DRAG;
      }
    }
    else {
      myState = STATE_INVALID;
      eraseFeedback();
      setCommand(null);
    }
  }

  @Override
  protected void handleButtonUp(int button) {
    if (myState == STATE_DRAG_IN_PROGRESS) {
      myState = STATE_NONE;
      eraseFeedback();
      executeCommand();
    }
  }

  @Override
  protected void handleDragStarted() {
    if (myState == STATE_DRAG) {
      myState = STATE_DRAG_IN_PROGRESS;
    }
  }

  @Override
  protected void handleDragInProgress() {
    if (myState == STATE_DRAG_IN_PROGRESS) {
      updateContext();
      showFeedback();
      setCommand();
    }
  }

  private void showFeedback() {
    for (EditOperation operation : getOperations()) {
      operation.showFeedback();
    }
    myShowFeedback = true;
  }

  private void eraseFeedback() {
    if (myShowFeedback) {
      myShowFeedback = false;
      for (EditOperation operation : getOperations()) {
        operation.eraseFeedback();
      }
    }
  }

  private void executeCommand() {
    if (myCommand != null) {
      try {
        for (EditOperation operation : getOperations()) {
          if (operation.canExecute()) {
            operation.execute();
          }
        }
      }
      catch (Exception e) {
        myToolProvider.showError("Execute command: ", e);
      }
    }
  }

  private void setCommand() {
    for (EditOperation operation : getOperations()) {
      if (operation.canExecute()) {
        setCommand(this);
        return;
      }
    }
    setCommand(null);
  }

  private void updateContext() {
    myContext.setArea(myArea);
    myContext.setInputEvent(myInputEvent);

    Point corner = new Point();
    Dimension resize = new Dimension();

    int moveDeltaHeight = myCurrentScreenY - myStartScreenY;
    if ((myDirection & Position.NORTH) != 0) {
      corner.y += moveDeltaHeight;
      resize.height -= moveDeltaHeight;
    }
    else if ((myDirection & Position.SOUTH) != 0) {
      resize.height += moveDeltaHeight;
    }

    int moveDeltaWidth = myCurrentScreenX - myStartScreenX;
    if ((myDirection & Position.WEST) != 0) {
      corner.x += moveDeltaWidth;
      resize.width -= moveDeltaWidth;
    }
    else if ((myDirection & Position.EAST) != 0) {
      resize.width += moveDeltaWidth;
    }

    myContext.setMoveDelta(corner);
    myContext.setSizeDelta(resize);
    myContext.setLocation(new Point(myCurrentScreenX, myCurrentScreenY));
  }

  private List<EditOperation> getOperations() {
    if (myOperations == null) {
      myContext.setComponents(new ArrayList<RadComponent>(myArea.getSelection()));
      myOperations = new ArrayList<EditOperation>();

      for (RadComponent component : myContext.getComponents()) {
        EditOperation operation;
        RadComponent parent = component.getParent();
        if (parent == null) {
          operation = myArea.processRootOperation(myContext);
        }
        else {
          operation = parent.getLayout().processChildOperation(myContext);
        }
        if (operation != null) {
          myOperations.add(operation);
          operation.setComponent(component);
        }
      }
    }
    return myOperations;
  }
}