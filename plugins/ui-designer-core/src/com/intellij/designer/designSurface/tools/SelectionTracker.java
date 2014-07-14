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
import com.intellij.openapi.util.SystemInfo;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;

/**
 * @author Alexander Lobas
 */
public class SelectionTracker extends TargetingTool {
  protected final RadComponent myComponent;
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
      // Control clicking on a Mac is used to simulate right clicks: do not treat this as
      // a selection reset (since it makes it impossible to pull up the context menu with
      // a multi-selection: the right click action causes the selection to be replaced
      // with the single item under the mouse)
      if (SystemInfo.isMac && myInputEvent != null && myInputEvent.isControlDown()) {
        return;
      }

      performSelection();
      myState = STATE_NONE;
    }
  }

  @Override
  public void keyPressed(KeyEvent event, EditableArea area) throws Exception {
    super.keyPressed(event, area);
    if (event.getKeyCode() == KeyEvent.VK_ESCAPE) {
      myToolProvider.loadDefaultTool();
    }
  }

  private void performSelection() {
    if (mySelected || myArea.isTree()) {
      return;
    }
    mySelected = true;
    performSelection(this, myComponent);
  }

  public static void performSelection(InputTool tool, RadComponent component) {
    if ((SystemInfo.isMac ? tool.myInputEvent.isMetaDown() : tool.myInputEvent.isControlDown())) {
      if (tool.myArea.isSelected(component)) {
        tool.myArea.deselect(component);
      }
      else {
        tool.myArea.appendSelection(component);
      }
    }
    else if (tool.myInputEvent.isShiftDown()) {
      tool.myArea.appendSelection(component);
    }
    else {
      tool.myArea.select(component);
    }
  }
}