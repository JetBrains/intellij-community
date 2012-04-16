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
package com.intellij.designer.designSurface;

import com.intellij.designer.designSurface.feedbacks.LineInsertFeedback;
import com.intellij.designer.designSurface.feedbacks.RectangleFeedback;
import com.intellij.designer.model.RadComponent;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public abstract class FlowBaseOperation extends AbstractEditOperation {
  protected final RadComponent myContainer;
  protected final boolean myHorizontal;
  protected RectangleFeedback myFirstInsertFeedback;
  protected LineInsertFeedback myInsertFeedback;
  protected Rectangle myBounds;
  protected RadComponent myChildTarget;
  protected boolean myInsertBefore;

  public FlowBaseOperation(RadComponent container, OperationContext context, boolean horizontal) {
    super(context);
    myContainer = container;
    myHorizontal = horizontal;
  }

  protected void createFeedback() {
    if (myFirstInsertFeedback == null) {
      FeedbackLayer layer = myContext.getArea().getFeedbackLayer();
      myBounds = myContainer.getBounds(layer);

      myFirstInsertFeedback = new RectangleFeedback(Color.green, 2);
      myFirstInsertFeedback.setBounds(myBounds);

      myInsertFeedback = new LineInsertFeedback(Color.green, !myHorizontal);
      myInsertFeedback.size(myBounds.width, myBounds.height);

      if (myContainer.getChildren().isEmpty()) {
        layer.add(myFirstInsertFeedback);
      }
      else {
        layer.add(myInsertFeedback);
      }
      layer.repaint();
    }
  }

  @Override
  public void showFeedback() {
    createFeedback();

    if (!myContainer.getChildren().isEmpty()) {
      FeedbackLayer layer = myContext.getArea().getFeedbackLayer();
      Point location = myContext.getLocation();
      myChildTarget = null;

      if (myHorizontal) {
        for (RadComponent child : myContainer.getChildren()) {
          Rectangle childBounds = getBounds(child, layer);
          if (childBounds.x <= location.x && location.x <= childBounds.getMaxX()) {
            myChildTarget = child;
            break;
          }
        }
      }
      else {
        for (RadComponent child : myContainer.getChildren()) {
          Rectangle childBounds = getBounds(child, layer);
          if (childBounds.y <= location.y && location.y <= childBounds.getMaxY()) {
            myChildTarget = child;
            break;
          }
        }
      }
      if (myChildTarget == null) {
        myChildTarget = getSideChildTarget();
      }

      Rectangle targetBounds = getBounds(myChildTarget, layer);
      if (myHorizontal) {
        myInsertBefore = location.x < targetBounds.getCenterX();

        if (myInsertBefore) {
          myInsertFeedback.setLocation(targetBounds.x, myBounds.y);
        }
        else {
          myInsertFeedback.setLocation(targetBounds.x + targetBounds.width, myBounds.y);
        }
      }
      else {
        myInsertBefore = location.y < targetBounds.getCenterY();

        if (myInsertBefore) {
          myInsertFeedback.setLocation(myBounds.x, targetBounds.y);
        }
        else {
          myInsertFeedback.setLocation(myBounds.x, targetBounds.y + targetBounds.height);
        }
      }

      layer.repaint();
    }
  }

  protected Rectangle getBounds(RadComponent component, FeedbackLayer layer) {
    return component.getBounds(layer);
  }

  private RadComponent getSideChildTarget() {
    Point location = myContext.getLocation();
    List<RadComponent> children = myContainer.getChildren();
    RadComponent lastChild = children.get(children.size() - 1);
    Rectangle childBounds = lastChild.getBounds(myContext.getArea().getFeedbackLayer());

    if (myHorizontal) {
      if (location.x >= childBounds.getMaxX()) {
        return lastChild;
      }
      return children.get(0);
    }
    if (location.y >= childBounds.getMaxY()) {
      return lastChild;
    }
    return children.get(0);
  }

  @Override
  public void eraseFeedback() {
    if (myFirstInsertFeedback != null) {
      FeedbackLayer layer = myContext.getArea().getFeedbackLayer();
      layer.remove(myFirstInsertFeedback);
      layer.remove(myInsertFeedback);
      layer.repaint();
      myFirstInsertFeedback = null;
      myInsertFeedback = null;
    }
  }

  @Override
  public boolean canExecute() {
    if (myContext.isMove()) {
      if (myComponents.contains(myChildTarget)) {
        return false;
      }

      List<RadComponent> children = myContainer.getChildren();
      int index = children.indexOf(myChildTarget) + (myInsertBefore ? -1 : 1);
      if (0 <= index && index < children.size()) {
        return !myComponents.contains(children.get(index));
      }
    }
    return true;
  }

  @Override
  public void execute() throws Exception {
    if (myChildTarget == null || myInsertBefore) {
      execute(myChildTarget);
    }
    else {
      List<RadComponent> children = myContainer.getChildren();
      int index = children.indexOf(myChildTarget) + 1;
      if (index < children.size()) {
        execute(children.get(index));
      }
      else {
        execute(null);
      }
    }
  }

  protected abstract void execute(@Nullable RadComponent insertBefore) throws Exception;
}