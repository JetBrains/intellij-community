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

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public class SelectionTool extends InputTool {
  public SelectionTool() {
    setDisabledCursor(Cursors.getNoCursor());
  }

  private void performSelection(RadComponent component) {
    if (myInputEvent.isControlDown()) {
      if (myArea.isSelected(component)) {
        myArea.deselect(component);
      }
      else {
        myArea.appendSelection(component);
      }
    }
    else if (myInputEvent.isShiftDown()) {
      myArea.appendSelection(component);
    }
    else {
      myArea.select(component);
    }
  }

  @Override
  protected void handleButtonDown(int button) {
    if (myState == STATE_INIT && (button == 1 || button == 3)) {
      RadComponent component = myArea.findTarget(myCurrentScreenX, myCurrentScreenY);
      if (component != null) {
        performSelection(component);
      }
    }
    if (button == 1) {
      if (myState == STATE_INIT) {
        myState = STATE_DRAG;
      }
    }
    else {
      if (button == 3) {
        myState = STATE_NONE;
      }
      else {
        myState = STATE_INVALID;
      }
      setCommand(null);
    }
  }

  @Override
  protected void handleButtonUp(int button) {
    myState = STATE_INIT;
    refreshCursor();
  }

  @Override
  protected void handleMove() {
    if (myState == STATE_INIT) {
      InputTool tracker = myArea.findTargetTool(myCurrentScreenX, myCurrentScreenY);
      if (tracker == null) {
        refreshCursor();
      }
      else {
        myArea.setCursor(tracker.getDefaultCursor());
      }
    }
  }

  protected Cursor calculateCursor() {
    return myState == STATE_INIT || myState == STATE_DRAG
           ? getDefaultCursor()
           : super.calculateCursor();
  }

  @Override
  public void keyPressed(KeyEvent event, EditableArea area) throws Exception {
    if (event.getKeyCode() == KeyEvent.VK_ESCAPE) {
      List<RadComponent> selection = area.getSelection();
      if (!selection.isEmpty()) {
        RadComponent component = selection.get(0).getParent();
        if (component != null) {
          area.select(component);
        }
      }
    }
  }
}