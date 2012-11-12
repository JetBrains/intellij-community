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

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;

/**
 * @author Alexander Lobas
 */
public class SelectionTracker extends TargetingTool {
  private final RadComponent myComponent;
  private boolean mySelected;

  public SelectionTracker(RadComponent component) {
    myComponent = component;
  }

  @Override
  protected void resetState() {
    super.resetState();
    mySelected = false;
  }

  protected Cursor calculateCursor() {
    return myState == STATE_INIT || myState == STATE_DRAG
           ? getDefaultCursor()
           : super.calculateCursor();
  }

  @Override
  protected void handleButtonDown(int button) {
    if (myState == STATE_INIT &&
        (button == MouseEvent.BUTTON1 || button == MouseEvent.BUTTON3) &&
        !myArea.isSelected(myComponent)) {
      performSelection();
    }
    if (button == MouseEvent.BUTTON1) {
      if (myState == STATE_INIT) {
        myState = STATE_DRAG;
      }
    }
    else {
      if (button == MouseEvent.BUTTON3) {
        myState = STATE_NONE;
      }
      else {
        myState = STATE_INVALID;
      }
      handleInvalidInput();
    }
  }

  @Override
  protected void handleButtonUp(int button) {
    if (myState == STATE_DRAG) {
      performSelection();
      myState = STATE_NONE;
    }
  }

  private void performSelection() {
    if (mySelected || myArea.isTree()) {
      return;
    }
    mySelected = true;

    if (myInputEvent.isControlDown()) {
      if (myArea.isSelected(myComponent)) {
        myArea.deselect(myComponent);
      }
      else {
        myArea.appendSelection(myComponent);
      }
    }
    else if (myInputEvent.isShiftDown()) {
      myArea.appendSelection(myComponent);
    }
    else {
      myArea.select(myComponent);
    }
  }

  @Override
  public void keyPressed(KeyEvent event, EditableArea area) throws Exception {
    if (event.getKeyCode() == KeyEvent.VK_ESCAPE) {
      myToolProvider.loadDefaultTool();
    }
  }
}