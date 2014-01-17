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
package com.intellij.designer.componentTree;

import com.intellij.designer.designSurface.AbstractEditOperation;
import com.intellij.designer.designSurface.FeedbackTreeLayer;
import com.intellij.designer.designSurface.OperationContext;
import com.intellij.designer.model.RadComponent;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * @author Alexander Lobas
 */
public abstract class TreeEditOperation extends AbstractEditOperation {
  protected RadComponent myTarget;
  protected boolean myInsertBefore;

  public TreeEditOperation(RadComponent container, OperationContext context) {
    super(container, context);
  }

  public static boolean isTarget(RadComponent container, OperationContext context) {
    Point location = context.getLocation();
    RadComponent target = context.getArea().findTarget(location.x, location.y, null);
    if (target == container) {
      FeedbackTreeLayer layer = context.getArea().getFeedbackTreeLayer();
      return !layer.isBeforeLocation(target, location.x, location.y) &&
             !layer.isAfterLocation(target, location.x, location.y);
    }
    return true;
  }

  protected Object[] getChildren() {
    return myContainer.getTreeChildren();
  }

  @Override
  public void showFeedback() {
    Point location = myContext.getLocation();
    FeedbackTreeLayer layer = myContext.getArea().getFeedbackTreeLayer();

    myTarget = myContext.getArea().findTarget(location.x, location.y, null);

    if (myContainer == myTarget) {
      layer.mark(myTarget, FeedbackTreeLayer.INSERT_SELECTION);
    }
    else if (myTarget != null && isChildren(myTarget)) {
      myInsertBefore = layer.isBeforeLocation(myTarget, location.x, location.y);
      layer.mark(myTarget, myInsertBefore ? FeedbackTreeLayer.INSERT_BEFORE : FeedbackTreeLayer.INSERT_AFTER);
    }
    else {
      myTarget = null;
      eraseFeedback();
    }
  }

  protected final boolean isChildren(RadComponent component) {
    return ArrayUtil.indexOf(getChildren(), component) != -1;
  }

  @Override
  public void eraseFeedback() {
    myContext.getArea().getFeedbackTreeLayer().unmark();
  }

  @Override
  public boolean canExecute() {
    if (myTarget == null) {
      return false;
    }
    if (myContext.isMove() && myTarget != myContainer) {
      if (myComponents.contains(myTarget)) {
        return false;
      }

      Object[] children = getChildren();
      int index = ArrayUtil.indexOf(children, myTarget) + (myInsertBefore ? -1 : 1);
      if (0 <= index && index < children.length) {
        return !myComponents.contains(children[index]);
      }
    }
    return true;
  }

  @Override
  public void execute() throws Exception {
    if (myTarget == myContainer) {
      execute(null);
    }
    else if (myInsertBefore) {
      execute(myTarget);
    }
    else {
      Object[] children = getChildren();
      int index = ArrayUtil.indexOf(children, myTarget) + 1;
      if (index < children.length) {
        execute((RadComponent)children[index]);
      }
      else {
        execute(null);
      }
    }
  }

  protected abstract void execute(@Nullable RadComponent insertBefore) throws Exception;
}