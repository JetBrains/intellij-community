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

import com.intellij.designer.designSurface.ComponentTargetFilter;
import com.intellij.designer.designSurface.EditOperation;
import com.intellij.designer.designSurface.OperationContext;
import com.intellij.designer.model.RadComponent;
import com.intellij.designer.model.RadLayout;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;

/**
 * @author Alexander Lobas
 */
public abstract class TargetingTool extends InputTool {
  protected OperationContext myContext = new OperationContext();
  protected EditOperation myTargetOperation;
  protected RadComponent myTarget;
  private boolean myShowFeedback;

  @Override
  public void deactivate() {
    eraseFeedback();
    myContext = new OperationContext();
    myTargetOperation = null;
    myTarget = null;
    super.deactivate();
  }

  protected void handleInvalidInput() {
    eraseFeedback();
    setExecuteEnabled(false);
  }

  @Override
  protected void handleDragStarted() {
    if (myState == STATE_DRAG) {
      myState = STATE_DRAG_IN_PROGRESS;
    }
  }

  @Override
  protected void handleAreaExited() {
    setTarget(null, null);
    setExecuteEnabled(false);
  }

  protected void showFeedback() {
    if (myTargetOperation != null) {
      myTargetOperation.showFeedback();
    }
    myShowFeedback = true;
  }

  protected void eraseFeedback() {
    if (myShowFeedback) {
      myShowFeedback = false;
      if (myTargetOperation != null) {
        myTargetOperation.eraseFeedback();
      }
    }
  }

  protected void executeCommand() {
    if (myExecuteEnabled) {
      myToolProvider.execute(Collections.singletonList(myTargetOperation), myContext.getMessage());
    }
  }

  protected void updateCommand() {
    setExecuteEnabled(myTargetOperation != null && myTargetOperation.canExecute());
  }

  protected void updateContext() {
    myContext.setArea(myArea);
    myContext.setInputEvent(myInputEvent);
  }

  protected void setTarget(@Nullable RadComponent target, @Nullable ContainerTargetFilter filter) {
    if (target != myTarget) {
      if (myTargetOperation != null) {
        eraseFeedback();
      }

      myTarget = target;
      myTargetOperation = filter == null ? null : filter.getOperation();
    }
  }

  protected class ContainerTargetFilter implements ComponentTargetFilter {
    private EditOperation myOperation;

    public EditOperation getOperation() {
      return myOperation;
    }

    @Override
    public boolean preFilter(RadComponent component) {
      return true;
    }

    @Override
    public boolean resultFilter(RadComponent target) {
      updateContext(target);

      if (myTarget == target) {
        return true;
      }

      RadLayout layout = target.getLayout();
      if (layout != null) {
        myOperation = layout.processChildOperation(myContext);
      }

      return myOperation != null;
    }

    protected void updateContext(RadComponent target) {
    }
  }
}