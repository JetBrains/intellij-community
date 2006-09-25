/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.radComponents;

import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.designSurface.*;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * @author yole
 */
public class FlowDropLocation implements DropLocation {
  private int myInsertIndex;
  private String myInsertBeforeId;
  private RadContainer myContainer;
  private final int myAlignment;
  private int myHGap;
  private final int myVGap;

  public FlowDropLocation(RadContainer container, Point location, int alignment, int hGap, int vGap) {
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
    myInsertBeforeId = null;
    if (myInsertIndex < myContainer.getComponentCount()) {
      myInsertBeforeId = myContainer.getComponent(myInsertIndex).getId();
    }
  }

  public RadContainer getContainer() {
    return myContainer;
  }

  public boolean canDrop(ComponentDragObject dragObject) {
    return true;
  }

  public void placeFeedback(FeedbackLayer feedbackLayer, ComponentDragObject dragObject) {
    if (myContainer.getComponentCount() == 0) {
      Dimension initialSize = dragObject.getInitialSize(myContainer.getDelegee());
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
      Dimension initialSize = dragObject.getInitialSize(myContainer.getDelegee());
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
        rc = new Rectangle(maxX, lastTop, initialSize.width, maxSize);
      }
      feedbackLayer.putFeedback(myContainer.getDelegee(), rc, myContainer.getDisplayName());
    }
    else if (myInsertIndex == myContainer.getComponentCount() && isRightAlign()) {
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

  @Nullable
  public DropLocation getAdjacentLocation(Direction direction) {
    return null;
  }
}
