/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.radComponents;

import com.intellij.uiDesigner.designSurface.*;
import com.intellij.uiDesigner.core.GridConstraints;

import javax.swing.*;
import java.awt.Component;
import java.awt.Rectangle;
import java.awt.Point;

/**
 * @author yole
 */
public class FlowDropLocation implements DropLocation {
  private int myInsertIndex;
  private String myInsertBeforeId;
  private RadContainer myContainer;
  private int myHGap;
  private boolean mySquareForLast;

  public FlowDropLocation(final RadContainer container, final Point location, final int hGap, final int vGap, final boolean squareForLast) {
    myHGap = hGap;
    myContainer = container;
    mySquareForLast = squareForLast;
    myInsertIndex = myContainer.getDelegee().getComponentCount();
    if (location != null) {
      for(int i=0; i<myContainer.getDelegee().getComponentCount(); i++) {
        Rectangle bounds = myContainer.getDelegee().getComponent(i).getBounds();
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
    if (myInsertIndex < myContainer.getDelegee().getComponentCount()) {
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
    if (myInsertIndex == myContainer.getDelegee().getComponentCount()) {
      JComponent component = myContainer.getDelegee();
      int maxX = 0;
      int lastSize = myContainer.getPreferredSize().height;
      int lastTop = myContainer.getDelegee().getInsets().top;
      for(Component child: component.getComponents()) {
        int childX = child.getBounds().x + child.getBounds().width;
        if (childX > maxX) maxX = childX;
        lastSize = child.getBounds().height;
        lastTop = child.getBounds().y;
      }
      final Rectangle rc = new Rectangle(maxX, lastTop, lastSize, lastSize);
      if (mySquareForLast) {
        feedbackLayer.putFeedback(myContainer.getDelegee(), rc);
      }
      else {
        rc.setSize(8, lastSize);
        feedbackLayer.putFeedback(myContainer.getDelegee(), rc, VertInsertFeedbackPainter.INSTANCE);
      }
    }
    else {
      Rectangle bounds = myContainer.getDelegee().getComponent(myInsertIndex).getBounds();
      Rectangle rc = new Rectangle(bounds.x-4-myHGap, bounds.y, 8, bounds.height);
      feedbackLayer.putFeedback(myContainer.getDelegee(), rc, VertInsertFeedbackPainter.INSTANCE);
    }
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
}
