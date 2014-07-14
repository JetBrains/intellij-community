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

import com.intellij.designer.designSurface.OperationContext;
import com.intellij.designer.model.RadComponent;
import com.intellij.designer.utils.Cursors;

import java.awt.*;
import java.util.Collections;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public class DragTracker extends SelectionTracker {
  private static final Cursor myDragCursor = Cursors.getMoveCursor();

  public DragTracker(RadComponent component) {
    super(component);
    setDefaultCursor(Cursors.RESIZE_ALL);
    setDisabledCursor(Cursors.getNoCursor());
  }

  @Override
  protected Cursor getDefaultCursor() {
    return myState == STATE_NONE ? super.getDefaultCursor() : myDragCursor;
  }

  @Override
  protected void handleButtonUp(int button) {
    if (myState == STATE_DRAG_IN_PROGRESS) {
      eraseFeedback();
      executeCommand();
      myState = STATE_NONE;
    }
    else {
      super.handleButtonUp(button);
    }
  }

  @Override
  protected void handleDragInProgress() {
    if (myState == STATE_DRAG_IN_PROGRESS) {
      updateContext();
      updateTargetUnderMouse();
      showFeedback();
      updateCommand();
    }
  }

  @Override
  protected void updateCommand() {
    if (myTargetOperation != null) {
      if (myContext.isMove()) {
        setExecuteEnabled(myContext.isMoveEnabled() && myTargetOperation.canExecute());
      }
      else if (myContext.isAdd()) {
        setExecuteEnabled(myContext.isAddEnabled() && myTargetOperation.canExecute());
      }
      else {
        setExecuteEnabled(false);
      }
    }
    else {
      setExecuteEnabled(false);
    }
  }

  private void updateTargetUnderMouse() {
    if (myContext.getComponents().isEmpty()) {
      return;
    }

    ContainerTargetFilter filter = new ContainerTargetFilter() {
      @Override
      public boolean preFilter(RadComponent component) {
        return !myContext.getComponents().contains(component);
      }

      @Override
      protected void updateContext(RadComponent target) {
        updateContextType(target);
      }
    };
    RadComponent target = myArea.findTarget(myCurrentScreenX, myCurrentScreenY, filter);
    setTarget(target, filter);

    if (target == null) {
      myContext.setType(null);
    }
    else {
      myTargetOperation.setComponents(myContext.getComponents());
    }
  }

  protected void updateContextType(RadComponent target) {
    if (myContext.getComponents().get(0).getParent() == target) {
      myContext.setType(OperationContext.MOVE);
    }
    else {
      myContext.setType(OperationContext.ADD);
    }
  }

  @Override
  protected void updateContext() {
    super.updateContext();

    myContext.setMoveDelta(new Point(moveDeltaWidth(), moveDeltaHeight()));
    myContext.setSizeDelta(new Dimension());
    myContext.setLocation(getLocation());

    if (myContext.getComponents() == null) {
      List<RadComponent> components = calculateContextComponents(RadComponent.getPureSelection(myArea.getSelection()));
      myContext.setComponents(components);

      for (RadComponent component : components) {
        component.processDropOperation(myContext);
      }
    }
  }

  protected List<RadComponent> calculateContextComponents(List<RadComponent> components) {
    RadComponent parent = null;
    for (RadComponent component : components) {
      if (parent == null) {
        parent = component.getParent();
      }
      else if (parent != component.getParent()) {
        components = Collections.emptyList();
        break;
      }
    }
    return components;
  }
}