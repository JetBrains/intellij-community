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

import com.intellij.designer.designSurface.EditOperation;
import com.intellij.designer.designSurface.FeedbackTreeLayer;
import com.intellij.designer.designSurface.OperationContext;
import com.intellij.designer.model.RadComponent;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Collections;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public abstract class TreeEditOperation implements EditOperation {
  protected final RadComponent myHost;
  protected final OperationContext myContext;
  protected List<RadComponent> myComponents;

  public static boolean isTarget(RadComponent host, OperationContext context) {
    Point location = context.getLocation();
    RadComponent target = context.getArea().findTarget(location.x, location.y, null);
    if (target == host) {
      FeedbackTreeLayer layer = context.getArea().getFeedbackTreeLayer();
      return !layer.isBeforeLocation(target, location.x, location.y) &&
             !layer.isAfterLocation(target, location.x, location.y);
    }
    return true;
  }

  public TreeEditOperation(RadComponent host, OperationContext context) {
    myHost = host;
    myContext = context;
  }

  @Override
  public void setComponent(RadComponent component) {
    myComponents = Collections.singletonList(component);
  }

  @Override
  public void setComponents(List<RadComponent> components) {
    myComponents = components;
  }

  @Override
  public void showFeedback() {
    Point location = myContext.getLocation();
    RadComponent target = myContext.getArea().findTarget(location.x, location.y, null);
    FeedbackTreeLayer layer = myContext.getArea().getFeedbackTreeLayer();

    if (myHost == target) {
      layer.mark(target, FeedbackTreeLayer.INSERT_SELECTION);
    }
    else if (target != null && isChildren(target)) {
      layer.mark(target,
                 layer.isBeforeLocation(target, location.x, location.y) ?
                 FeedbackTreeLayer.INSERT_BEFORE : FeedbackTreeLayer.INSERT_AFTER);
    } else {
      eraseFeedback();
    }
  }

  private boolean isChildren(RadComponent component) {
    for (Object child : myHost.getTreeChildren()) {
      if (child == component) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void eraseFeedback() {
    myContext.getArea().getFeedbackTreeLayer().mark(null, FeedbackTreeLayer.INSERT_AFTER);
  }

  @Override
  public boolean canExecute() {
    RadComponent reference = getReference();
    if (reference == null) {
      return false;
    }
    return canExecute(myHost == reference ? null : reference);
  }

  protected boolean canExecute(RadComponent insertBefore) {
    return true;
  }

  @Override
  public void execute() throws Exception {
    RadComponent reference = getReference();
    execute(myHost == reference ? null : reference);
  }

  protected abstract void execute(RadComponent insertBefore) throws Exception;

  @Nullable
  private RadComponent getReference() {
    Point location = myContext.getLocation();
    RadComponent target = myContext.getArea().findTarget(location.x, location.y, null);

    if (myHost == target) {
      return myHost;
    }
    if (target != null) {
      Object[] children = myHost.getTreeChildren();
      int index = -1;

      for (int i = 0; i < children.length; i++) {
        if (children[i] == target) {
          index = i + 1;
          break;
        }
      }
      if (index == -1) {
        return null;
      }
      if (myContext.getArea().getFeedbackTreeLayer().isBeforeLocation(target, location.x, location.y)) {
        return target;
      }
      if (index < children.length) {
        return (RadComponent)children[index];
      }
      return myHost;
    }
    return null;
  }
}