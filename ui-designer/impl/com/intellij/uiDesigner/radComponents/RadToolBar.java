/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.radComponents;

import com.intellij.openapi.module.Module;
import com.intellij.uiDesigner.UIFormXmlConstants;
import com.intellij.uiDesigner.XmlWriter;
import com.intellij.uiDesigner.designSurface.*;
import com.intellij.uiDesigner.core.AbstractLayout;
import com.intellij.uiDesigner.core.GridConstraints;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * @author yole
 */
public class RadToolBar extends RadContainer {
  public RadToolBar(final Module module, final String id) {
    super(module, JToolBar.class, id);
  }

  @Override @Nullable
  protected AbstractLayout createInitialLayout() {
    return null;
  }

  @Override @Nullable
  public DropLocation getDropLocation(@Nullable Point location) {
    int insertIndex = getDelegee().getComponentCount();
    if (location != null) {
      for(int i=0; i<getDelegee().getComponentCount(); i++) {
        Rectangle bounds = getDelegee().getComponent(i).getBounds();
        if (bounds.contains(location)) {
          if (location.x < bounds.getCenterX()) {
            insertIndex = i;
          }
          else {
            insertIndex = i+1;
          }
          break;
        }
      }
    }
    String insertBeforeId = null;
    if (insertIndex < getDelegee().getComponentCount()) {
      insertBeforeId = getComponent(insertIndex).getId();
    }
    return new AddButtonDropLocation(insertIndex, insertBeforeId);
  }

  protected void addToDelegee(final int index, final RadComponent component) {
    getDelegee().add(component.getDelegee(), component.getConstraints(), index);
  }

  public void write(final XmlWriter writer) {
    writer.startElement(UIFormXmlConstants.ELEMENT_TOOLBAR);
    try {
      writeNoLayout(writer);
    } finally {
      writer.endElement();
    }
  }

  private class AddButtonDropLocation implements DropLocation {
    private int myInsertIndex;
    private String myInsertBeforeId;

    public AddButtonDropLocation(final int insertIndex, final String insertBeforeId) {
      myInsertBeforeId = insertBeforeId;
      myInsertIndex = insertIndex;
    }

    public RadContainer getContainer() {
      return RadToolBar.this;
    }

    public boolean canDrop(ComponentDragObject dragObject) {
      return true;
    }

    public void placeFeedback(FeedbackLayer feedbackLayer, ComponentDragObject dragObject) {
      if (myInsertIndex == getDelegee().getComponentCount()) {
        JComponent component = getDelegee();
        int maxX = 0;
        int lastHeight = getPreferredSize().height;
        for(Component child: component.getComponents()) {
          int childX = child.getBounds().x + child.getBounds().width;
          if (childX > maxX) maxX = childX;
          lastHeight = child.getBounds().height;
        }
        final Rectangle rc = new Rectangle(maxX, getDelegee().getInsets().top, lastHeight, lastHeight);
        feedbackLayer.putFeedback(getDelegee(), rc);
      }
      else {
        Rectangle bounds = getDelegee().getComponent(myInsertIndex).getBounds();
        Rectangle rc = new Rectangle(bounds.x-4, bounds.y, 8, bounds.height);
        feedbackLayer.putFeedback(getDelegee(), rc, VertInsertFeedbackPainter.INSTANCE);
      }
    }

    public void processDrop(GuiEditor editor,
                            RadComponent[] components,
                            GridConstraints[] constraintsToAdjust,
                            ComponentDragObject dragObject) {
      for(int i=0; i<getComponentCount(); i++) {
        if (getComponent(i).getId().equals(myInsertBeforeId)) {
          myInsertIndex = i;
          break;
        }
      }
      if (myInsertIndex > getComponentCount()) {
        myInsertIndex = getComponentCount();
      }
      for(RadComponent component: components) {
        addComponent(component, myInsertIndex);
        myInsertIndex++;
      }
    }
  }
}
