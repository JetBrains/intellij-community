// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.uiDesigner.radComponents;

import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.designSurface.*;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;


public class FlowDropLocation implements ComponentDropLocation {
  private int myInsertIndex;
  private final String myInsertBeforeId;
  private final RadContainer myContainer;
  private final int myAlignment;
  private final int myHGap;
  private final int myVGap;

  public FlowDropLocation(RadContainer container, Point location, @JdkConstants.FlowLayoutAlignment int alignment, int hGap, int vGap) {
    myContainer = container;
    myAlignment = alignment;
    myHGap = hGap;
    myVGap = vGap;
    myInsertIndex = myContainer.getComponentCount();
    if (location != null) {
      for(int i=0; i<myContainer.getComponentCount(); i++) {
        Rectangle bounds = myContainer.getComponent(i).getBounds();
        bounds.grow(myHGap, vGap);
        if (bounds.contains(location)) {
          if (location.x < bounds.getCenterX()) {
            myInsertIndex = i;
          }
          else {
            myInsertIndex = i+1;
          }
          break;
        }
        else if (i == 0 && location.x < bounds.x) {
          myInsertIndex = 0;
        }
      }
    }
    if (myInsertIndex < myContainer.getComponentCount()) {
      myInsertBeforeId = myContainer.getComponent(myInsertIndex).getId();
    }
    else {
      myInsertBeforeId = null;
    }
  }

  @Override
  public RadContainer getContainer() {
    return myContainer;
  }

  @Override
  public boolean canDrop(ComponentDragObject dragObject) {
    return true;
  }

  @Override
  public void placeFeedback(FeedbackLayer feedbackLayer, ComponentDragObject dragObject) {
    if (myContainer.getComponentCount() == 0) {
      Dimension initialSize = dragObject.getInitialSize(getContainer());
      int originX;
      if (myAlignment == FlowLayout.CENTER) {
        originX = myContainer.getSize().width / 2 - initialSize.width / 2 - myHGap;
      }
      else if (isRightAlign()) {
        originX = myContainer.getSize().width - initialSize.width - 2 * myHGap;
      }
      else {
        originX = 2 * myHGap;
      }
      int height = Math.min(initialSize.height, myContainer.getBounds().height);
      Rectangle rc = new Rectangle(originX, 2 * myVGap, initialSize.width, height);
      feedbackLayer.putFeedback(myContainer.getDelegee(), rc, myContainer.getDisplayName());
    }
    else if ((myInsertIndex == myContainer.getComponentCount() && !isRightAlign()) ||
        (myInsertIndex == 0 && !isLeftAlign())) {
      Dimension initialSize = dragObject.getInitialSize(getContainer());
      JComponent component = myContainer.getDelegee();
      int minX = component.getComponent(0).getBounds().x;
      int maxX = 0;
      int maxSize = 0;
      int lastTop = myContainer.getDelegee().getInsets().top;
      for(Component child: component.getComponents()) {
        int childX = child.getBounds().x + child.getBounds().width;
        if (childX > maxX) maxX = childX;
        maxSize = Math.max(maxSize, child.getBounds().height);
        lastTop = child.getBounds().y;
      }
      maxSize = Math.max(maxSize, initialSize.height);
      maxSize = Math.min(maxSize, myContainer.getBounds().height);
      final Rectangle rc;
      if (myInsertIndex == 0) {
        rc = new Rectangle(minX - myHGap - initialSize.width, lastTop, initialSize.width, maxSize);
      }
      else {
        int initialWidth = Math.max(8, Math.min(initialSize.width, myContainer.getBounds().width - maxX));
        rc = new Rectangle(maxX, lastTop, initialWidth, maxSize);
      }
      feedbackLayer.putFeedback(myContainer.getDelegee(), rc, myContainer.getDisplayName());
    }
    else if (myInsertIndex == myContainer.getComponentCount()) {
      Rectangle bounds = myContainer.getComponent(myInsertIndex-1).getBounds();
      Rectangle rc = new Rectangle(bounds.x+bounds.width, bounds.y, 8, bounds.height);
      feedbackLayer.putFeedback(myContainer.getDelegee(), rc, VertInsertFeedbackPainter.INSTANCE, myContainer.getDisplayName());
    }
    else {
      Rectangle bounds = myContainer.getComponent(myInsertIndex).getBounds();
      Rectangle rc = new Rectangle(bounds.x-4-myHGap, bounds.y, 8, bounds.height);
      feedbackLayer.putFeedback(myContainer.getDelegee(), rc, VertInsertFeedbackPainter.INSTANCE, myContainer.getDisplayName());
    }
  }

  private boolean isLeftAlign() {
    return myAlignment == FlowLayout.LEFT || myAlignment == FlowLayout.LEADING;
  }

  private boolean isRightAlign() {
    return myAlignment == FlowLayout.RIGHT || myAlignment == FlowLayout.TRAILING;
  }

  @Override
  public void processDrop(GuiEditor editor,
                          RadComponent[] components,
                          GridConstraints[] constraintsToAdjust,
                          ComponentDragObject dragObject) {
    for(int i=0; i<myContainer.getComponentCount(); i++) {
      if (myContainer.getComponent(i).getId().equals(myInsertBeforeId)) {
        myInsertIndex = i;
        break;
      }
    }
    if (myInsertIndex > myContainer.getComponentCount()) {
      myInsertIndex = myContainer.getComponentCount();
    }
    for(RadComponent component: components) {
      myContainer.addComponent(component, myInsertIndex);
      myInsertIndex++;
    }
  }

  @Override
  public @Nullable ComponentDropLocation getAdjacentLocation(Direction direction) {
    return null;
  }
}
