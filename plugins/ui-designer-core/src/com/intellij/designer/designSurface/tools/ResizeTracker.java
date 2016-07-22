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

import com.intellij.designer.DesignerBundle;
import com.intellij.designer.designSurface.EditOperation;
import com.intellij.designer.designSurface.EditableArea;
import com.intellij.designer.designSurface.OperationContext;
import com.intellij.designer.model.RadComponent;
import com.intellij.designer.utils.Cursors;
import com.intellij.designer.utils.Position;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public class ResizeTracker extends InputTool {
  private final int myDirection;
  private final String myDescription;
  private OperationContext myContext;
  private List<EditOperation> myOperations;
  private boolean myShowFeedback;

  public ResizeTracker(int direction, Object type, @Nullable String description) {
    myDirection = direction;
    myDescription = description;
    myContext = new OperationContext(type);
    myContext.setResizeDirection(direction);
    setDefaultCursor(Cursors.getResizeCursor(direction));
    setDisabledCursor(Cursors.getNoCursor());
  }

  @Override
  @Nullable
  protected String getDescription() {
    return myDescription;
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
    if (button == MouseEvent.BUTTON1) {
      if (myState == STATE_INIT) {
        myState = STATE_DRAG;
      }
    }
    else {
      myState = STATE_INVALID;
      eraseFeedback();
      setExecuteEnabled(false);
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
      updateCommand();
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
    if (myExecuteEnabled) {
      List<EditOperation> operations = new ArrayList<>();
      for (EditOperation operation : getOperations()) {
        if (operation.canExecute()) {
          operations.add(operation);
        }
      }
      myToolProvider.execute(operations, DesignerBundle.message("command.tool_operation"));
    }
  }

  private void updateCommand() {
    for (EditOperation operation : getOperations()) {
      if (operation.canExecute()) {
        setExecuteEnabled(true);
        return;
      }
    }
    setExecuteEnabled(false);
  }

  private void updateContext() {
    myContext.setArea(myArea);
    myContext.setInputEvent(myInputEvent);
    myContext.setModifiers(myModifiers);

    Point move = new Point();
    Dimension size = new Dimension();

    int moveDeltaWidth = moveDeltaWidth();
    if ((myDirection & Position.WEST) != 0) {
      move.x += moveDeltaWidth;
      size.width -= moveDeltaWidth;
    }
    else if ((myDirection & Position.EAST) != 0) {
      size.width += moveDeltaWidth;
    }

    int moveDeltaHeight = moveDeltaHeight();
    if ((myDirection & Position.NORTH) != 0) {
      move.y += moveDeltaHeight;
      size.height -= moveDeltaHeight;
    }
    else if ((myDirection & Position.SOUTH) != 0) {
      size.height += moveDeltaHeight;
    }

    myContext.setMoveDelta(move);
    myContext.setSizeDelta(size);
    myContext.setLocation(getLocation());
  }

  private List<EditOperation> getOperations() {
    if (myOperations == null) {
      myContext.setComponents(new ArrayList<>(myArea.getSelection()));
      myOperations = new ArrayList<>();

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

  @Override
  public void keyPressed(KeyEvent event, EditableArea area) throws Exception {
    boolean changedModifiers = event.getModifiers() != myModifiers;
    super.keyPressed(event, area);

    if (event.getKeyCode() == KeyEvent.VK_ESCAPE) {
      myToolProvider.loadDefaultTool();
    }
    else if (changedModifiers) {
      handleKeyEvent();
    }
  }

  @Override
  public void keyReleased(KeyEvent event, EditableArea area) throws Exception {
    boolean changedModifiers = event.getModifiers() != myModifiers;
    super.keyReleased(event, area);

    if (changedModifiers) {
      handleKeyEvent();
    }
  }

  private void handleKeyEvent() {
    if (myContext != null) {
      updateContext();
      showFeedback();
      updateCommand();
    }
  }
}