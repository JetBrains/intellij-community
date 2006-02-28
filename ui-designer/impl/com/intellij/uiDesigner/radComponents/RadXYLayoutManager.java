/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.radComponents;

import com.intellij.uiDesigner.XmlWriter;
import com.intellij.uiDesigner.designSurface.DropLocation;
import com.intellij.uiDesigner.designSurface.ComponentDragObject;
import com.intellij.uiDesigner.designSurface.FeedbackLayer;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.core.AbstractLayout;
import com.intellij.uiDesigner.core.GridConstraints;
import org.jetbrains.annotations.NonNls;

import java.awt.*;

/**
 * @author yole
 */
public class RadXYLayoutManager extends RadLayoutManager {
  public static RadXYLayoutManager INSTANCE = new RadXYLayoutManager();

  public @NonNls String getName() {
    return "XYLayout";
  }

  public LayoutManager createLayout() {
    return new XYLayoutManagerImpl();
  }

  public void writeLayout(final XmlWriter writer, final RadContainer radContainer) {
    final AbstractLayout layout = (AbstractLayout)radContainer.getLayout();
    // It has sense to save hpap and vgap even for XY layout. The reason is
    // that XY was previously GRID with non default gaps, so when the user
    // compose XY into the grid again then he will get the same non default gaps.
    writer.addAttribute("hgap", layout.getHGap());
    writer.addAttribute("vgap", layout.getVGap());

    // Margins
    final Insets margin = layout.getMargin();
    writer.startElement("margin");
    try {
      writer.addAttribute("top", margin.top);
      writer.addAttribute("left", margin.left);
      writer.addAttribute("bottom", margin.bottom);
      writer.addAttribute("right", margin.right);
    }
    finally {
      writer.endElement(); // margin
    }
  }

  public void writeChildConstraints(final XmlWriter writer, final RadComponent child) {
    // Constraints of XY layout
    writer.startElement("xy");
    try{
      writer.addAttribute("x", child.getX());
      writer.addAttribute("y", child.getY());
      writer.addAttribute("width", child.getWidth());
      writer.addAttribute("height", child.getHeight());
    }finally{
      writer.endElement(); // xy
    }
  }

  @Override public DropLocation getDropLocation(RadContainer container, final Point location) {
    return new MyDropLocation(container, location);
  }

  private static class MyDropLocation implements DropLocation {
    private final RadContainer myContainer;
    private final Point myLocation;

    public MyDropLocation(final RadContainer container, final Point location) {
      myContainer = container;
      myLocation = location;
    }

    public RadContainer getContainer() {
      return myContainer;
    }

    public boolean canDrop(ComponentDragObject dragObject) {
      return myLocation != null && myContainer.getComponentCount() == 0 && dragObject.getComponentCount() == 1;
    }

    public void placeFeedback(FeedbackLayer feedbackLayer, ComponentDragObject dragObject) {
    }

    public void processDrop(GuiEditor editor,
                            RadComponent[] components,
                            GridConstraints[] constraintsToAdjust,
                            ComponentDragObject dragObject) {
      int patchX = 0;
      int patchY = 0;

      for (int i = 0; i < components.length; i++) {
        final RadComponent c = components[i];

        final Point p = new Point(myLocation);
        Point delta = dragObject.getDelta(i);
        if (delta != null) {
          p.translate(delta.x, delta.y);
        }
        c.setLocation(p);

        patchX = Math.min(patchX, p.x);
        patchY = Math.min(patchY, p.y);

        myContainer.addComponent(c);
      }

      // shift components if necessary to make sure that no component has negative x or y
      if (patchX < 0 || patchY < 0) {
        for(RadComponent component : components) {
          component.shift(-patchX, -patchY);
        }
      }
    }
  }
}
