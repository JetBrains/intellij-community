/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.radComponents;

import com.intellij.openapi.module.Module;
import com.intellij.uiDesigner.UIFormXmlConstants;
import com.intellij.uiDesigner.XmlWriter;
import com.intellij.uiDesigner.designSurface.ComponentDragObject;
import com.intellij.uiDesigner.core.AbstractLayout;
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

  @Override public boolean canDrop(@Nullable Point location, ComponentDragObject dragObject) {
    return true;
  }

  @Override public void drop(@Nullable Point location, RadComponent[] components, ComponentDragObject dragObject) {
    for(RadComponent component: components) {
      addComponent(component);
    }
  }

  public Rectangle getDropFeedbackRectangle(Point location, final int componentCount) {
    JComponent component = getDelegee();
    int maxX = 0;
    for(Component child: component.getComponents()) {
      int childX = child.getBounds().x + child.getBounds().width;
      if (childX > maxX) maxX = childX;
    }
    return new Rectangle(maxX, getDelegee().getInsets().top, getPreferredSize().height, getPreferredSize().height);
  }

  protected void addToDelegee(final RadComponent component){
    getDelegee().add(component.getDelegee(), component.getConstraints());
  }

  public void write(final XmlWriter writer) {
    writer.startElement(UIFormXmlConstants.ELEMENT_TOOLBAR);
    try {
      writeNoLayout(writer);
    } finally {
      writer.endElement();
    }
  }
}
