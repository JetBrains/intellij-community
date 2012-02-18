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

import com.intellij.designer.designSurface.EditableArea;
import com.intellij.designer.model.RadComponent;
import com.intellij.designer.utils.Cursors;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;

/**
 * @author Alexander Lobas
 */
public abstract class AbstractCreationTool extends TargetingTool {
  protected AbstractCreationTool() {
    setDefaultCursor(Cursors.getCopyCursor());
    setDisabledCursor(Cursors.getNoCursor());
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
      handleInvalidInput();
    }
  }

  @Override
  protected void handleButtonUp(int button) {
    if (myState == STATE_DRAG || myState == STATE_DRAG_IN_PROGRESS) {
      eraseFeedback();
      executeCommand();
      selectAddedObjects();
    }

    myState = STATE_NONE;
    handleFinished();
  }

  @Override
  protected void handleMove() {
    updateContext();
    updateTargetUnderMouse();
    showFeedback();
    setCommand();
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

  protected abstract void selectAddedObjects();

  private void updateTargetUnderMouse() {
    ContainerTargetFilter filter = new ContainerTargetFilter();
    RadComponent target = myArea.findTarget(myCurrentScreenX, myCurrentScreenY, filter);
    setTarget(target, filter);
    // TODO
  }

  @Override
  protected void updateContext() {
    super.updateContext();

    if (myState == STATE_DRAG_IN_PROGRESS) {
      myContext.setSizeDelta(new Dimension(myCurrentScreenX - myStartScreenX, myCurrentScreenY - myStartScreenY));
    }

    myContext.setLocation(new Point(myCurrentScreenX, myCurrentScreenY));
  }

  @Override
  public void keyPressed(KeyEvent event, EditableArea area) throws Exception {
    if (event.getKeyCode() == KeyEvent.VK_ESCAPE) {
      myToolProvider.loadDefaultTool();
    }
  }
}