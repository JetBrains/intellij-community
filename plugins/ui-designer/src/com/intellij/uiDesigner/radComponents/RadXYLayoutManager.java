// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.uiDesigner.radComponents;

import com.intellij.openapi.project.Project;
import com.intellij.uiDesigner.UIFormXmlConstants;
import com.intellij.uiDesigner.XmlWriter;
import com.intellij.uiDesigner.core.AbstractLayout;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.designSurface.ComponentDragObject;
import com.intellij.uiDesigner.designSurface.ComponentDropLocation;
import com.intellij.uiDesigner.designSurface.FeedbackLayer;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.propertyInspector.Property;
import com.intellij.uiDesigner.propertyInspector.properties.HGapProperty;
import com.intellij.uiDesigner.propertyInspector.properties.VGapProperty;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;


public class RadXYLayoutManager extends RadLayoutManager {
  public static RadXYLayoutManager INSTANCE = new RadXYLayoutManager();

  @Override
  public @NonNls String getName() {
    return UIFormXmlConstants.LAYOUT_XY;
  }

  @Override
  public LayoutManager createLayout() {
    return new XYLayoutManagerImpl();
  }

  @Override
  public void writeLayout(final XmlWriter writer, final RadContainer radContainer) {
    final AbstractLayout layout = (AbstractLayout)radContainer.getLayout();
    // It has sense to save hpap and vgap even for XY layout. The reason is
    // that XY was previously GRID with non default gaps, so when the user
    // compose XY into the grid again then he will get the same non default gaps.
    writer.addAttribute(UIFormXmlConstants.ATTRIBUTE_HGAP, layout.getHGap());
    writer.addAttribute(UIFormXmlConstants.ATTRIBUTE_VGAP, layout.getVGap());

    // Margins
    final Insets margin = layout.getMargin();
    writer.startElement("margin");
    try {
      writer.writeInsets(margin);
    }
    finally {
      writer.endElement(); // margin
    }
  }

  @Override
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

  @Override
  public @NotNull ComponentDropLocation getDropLocation(RadContainer container, final @Nullable Point location) {
    return new MyDropLocation(container, location != null ? location : new Point(5, 5));
  }

  @Override
  public void addComponentToContainer(final RadContainer container, final RadComponent component, final int index) {
    container.getDelegee().add(component.getDelegee(), component.getConstraints());
  }

  private static class MyDropLocation implements ComponentDropLocation {
    private final RadContainer myContainer;
    private final @NotNull Point myLocation;

    MyDropLocation(final RadContainer container, final @NotNull Point location) {
      myContainer = container;
      myLocation = location;
    }

    @Override
    public RadContainer getContainer() {
      return myContainer;
    }

    @Override
    public boolean canDrop(ComponentDragObject dragObject) {
      if (dragObject.getComponentCount() != 1) {
        return false;
      }
      for(RadComponent component: myContainer.getComponents()) {
        if (!component.isDragging()) {
          return false;
        }
      }

      return true;
    }

    @Override
    public void placeFeedback(FeedbackLayer feedbackLayer, ComponentDragObject dragObject) {
    }

    @Override
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

    @Override
    public @Nullable ComponentDropLocation getAdjacentLocation(Direction direction) {
      return null;
    }
  }

  @Override public Property[] getContainerProperties(final Project project) {
    return new Property[] {
      HGapProperty.getInstance(project),
      VGapProperty.getInstance(project)
    };
  }
}
