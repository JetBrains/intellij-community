/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.radComponents;

import com.intellij.openapi.project.Project;
import com.intellij.uiDesigner.UIFormXmlConstants;
import com.intellij.uiDesigner.XmlWriter;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.designSurface.ComponentDragObject;
import com.intellij.uiDesigner.designSurface.DropLocation;
import com.intellij.uiDesigner.designSurface.FeedbackLayer;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.propertyInspector.Property;
import com.intellij.uiDesigner.propertyInspector.properties.HGapProperty;
import com.intellij.uiDesigner.propertyInspector.properties.VGapProperty;
import com.intellij.uiDesigner.snapShooter.SnapshotContext;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;

/**
 * @author yole
 */
public class RadBorderLayoutManager extends RadLayoutManager {
  public String getName() {
    return UIFormXmlConstants.LAYOUT_BORDER;
  }

  public LayoutManager createLayout() {
    return new BorderLayout();
  }

  public void writeLayout(final XmlWriter writer, final RadContainer radContainer) {
    BorderLayout layout = (BorderLayout) radContainer.getLayout();
    writer.addAttribute(UIFormXmlConstants.ATTRIBUTE_HGAP, layout.getHgap());
    writer.addAttribute(UIFormXmlConstants.ATTRIBUTE_VGAP, layout.getVgap());
  }

  public void addComponentToContainer(final RadContainer container, final RadComponent component, final int index) {
    if (component.getCustomLayoutConstraints() == null) {
      if (container.getDelegee().getComponentCount() == 0) {
        component.setCustomLayoutConstraints(BorderLayout.CENTER);
      }
      else {
        throw new RuntimeException("can't add component without constraints to container with BorderLayout");
      }
    }
    container.getDelegee().add(component.getDelegee(), component.getCustomLayoutConstraints(), index);
  }

  public void writeChildConstraints(final XmlWriter writer, final RadComponent child) {
    writer.addAttribute(UIFormXmlConstants.ATTRIBUTE_BORDER_CONSTRAINT, (String) child.getCustomLayoutConstraints());
  }

  @NotNull @Override
  public DropLocation getDropLocation(RadContainer container, final Point location) {
    return new MyDropLocation(container, getQuadrantAt(container, location));
  }

  private static String getQuadrantAt(final RadContainer container, final Point location) {
    if (location == null) {
      return BorderLayout.CENTER;
    }

    Dimension size = container.getDelegee().getSize();
    if (location.x < size.width / 3) {
      return BorderLayout.WEST;
    }
    if (location.y < size.height / 3) {
      return BorderLayout.NORTH;
    }
    if (location.x > size.width * 2 / 3) {
      return BorderLayout.EAST;
    }
    if (location.y > size.height * 2 / 3) {
      return BorderLayout.SOUTH;
    }

    return BorderLayout.CENTER;
  }


  @Override public void changeContainerLayout(RadContainer container) throws IncorrectOperationException {
    ArrayList<RadComponent> componentsInBorder = new ArrayList<RadComponent>();

    boolean borderHorz = true;
    if (container.getComponentCount() == 1) {
      componentsInBorder.add(container.getComponent(0));
    }
    else if (container.getLayoutManager().isIndexed()) {
      for(RadComponent c: container.getComponents()) {
        if (!(c instanceof RadHSpacer) && !(c instanceof RadVSpacer)) {
          componentsInBorder.add(c);
        }
      }
    }
    else if (container.getLayoutManager().isGrid()) {
      if (container.getGridRowCount() == 1) {
        copyGridLine(container, componentsInBorder, true);
      }
      else if (container.getGridColumnCount() == 1) {
        copyGridLine(container, componentsInBorder, false);
        borderHorz = false;
      }
    }

    if ((container.getComponentCount() > 0 && componentsInBorder.size() == 0) || componentsInBorder.size() > 3) {
      throw new IncorrectOperationException("Component layout is too complex to convert to BorderLayout");
    }

    for(int i=container.getComponentCount()-1; i >= 0; i--) {
      container.removeComponent(container.getComponent(i));
    }

    super.changeContainerLayout(container);

    if (componentsInBorder.size() == 1) {
      componentsInBorder.get(0).setCustomLayoutConstraints(BorderLayout.CENTER);
    }
    else if (componentsInBorder.size() > 1) {
      componentsInBorder.get(0).setCustomLayoutConstraints(borderHorz ? BorderLayout.WEST : BorderLayout.NORTH);
      componentsInBorder.get(1).setCustomLayoutConstraints(BorderLayout.CENTER);
      if (componentsInBorder.size() > 2) {
        componentsInBorder.get(2).setCustomLayoutConstraints(borderHorz ? BorderLayout.EAST : BorderLayout.SOUTH);
      }
    }

    for(RadComponent c: componentsInBorder) {
      container.addComponent(c);
    }
  }

  private static void copyGridLine(final RadContainer container, final ArrayList<RadComponent> componentsInBorder, boolean isRow) {
    int cell = 0;
    while(cell < container.getGridCellCount(!isRow)) {
      RadComponent c = container.getComponentAtGrid(isRow, 0, cell);
      if (c == null)
        cell++;
      else {
        if (!(c instanceof RadHSpacer) && !(c instanceof RadVSpacer)) {
          componentsInBorder.add(c);
        }
        cell += c.getConstraints().getSpan(!isRow);
      }
    }
  }

  @Override public Property[] getContainerProperties(final Project project) {
    return new Property[] {
      HGapProperty.getInstance(project),
      VGapProperty.getInstance(project)
    };
  }

  @Override public void createSnapshotLayout(final SnapshotContext context,
                                             final JComponent parent,
                                             final RadContainer container,
                                             final LayoutManager layout) {
    BorderLayout borderLayout = (BorderLayout) layout;
    container.setLayout(new BorderLayout(borderLayout.getHgap(), borderLayout.getVgap()));
  }

  @Override public void addSnapshotComponent(final JComponent parent,
                                             final JComponent child,
                                             final RadContainer container,
                                             final RadComponent component) {
    BorderLayout borderLayout = (BorderLayout) parent.getLayout();
    final Object constraints = borderLayout.getConstraints(child);
    if (constraints != null) {
      // sometimes the container sets the layout manager to BorderLayout but
      // overrides the layout() method so that the component constraints are not used
      component.setCustomLayoutConstraints(constraints);
      container.addComponent(component);
    }
  }

  private static class MyDropLocation implements DropLocation {
    private RadContainer myContainer;
    private String myQuadrant;

    public MyDropLocation(final RadContainer container, final String quadrant) {
      myQuadrant = quadrant;
      myContainer = container;
    }

    public RadContainer getContainer() {
      return myContainer;
    }

    public boolean canDrop(ComponentDragObject dragObject) {
      return dragObject.getComponentCount() == 1 &&
             ((BorderLayout) myContainer.getLayout()).getLayoutComponent(myQuadrant) == null;
    }

    public void placeFeedback(FeedbackLayer feedbackLayer, ComponentDragObject dragObject) {
      feedbackLayer.putFeedback(myContainer.getDelegee(), getFeedbackRect(myQuadrant),
                                myContainer.getDisplayName() + " (" + myQuadrant.toLowerCase() + ")");
    }

    private Rectangle getFeedbackRect(final String quadrant) {
      Dimension size = myContainer.getDelegee().getSize();
      if (quadrant.equals(BorderLayout.WEST)) {
        return new Rectangle(0, 0, size.width/3, size.height);
      }
      if (quadrant.equals(BorderLayout.NORTH)) {
        return new Rectangle(0, 0, size.width, size.height/3);
      }
      if (quadrant.equals(BorderLayout.EAST)) {
        return new Rectangle(size.width*2/3, 0, size.width/3, size.height);
      }
      if (quadrant.equals(BorderLayout.SOUTH)) {
        return new Rectangle(0, size.height*2/3, size.width, size.height/3);
      }
      return new Rectangle(size.width/3, size.height/3, size.width/3, size.height/3);
    }

    public void processDrop(GuiEditor editor,
                            RadComponent[] components,
                            GridConstraints[] constraintsToAdjust,
                            ComponentDragObject dragObject) {
      components [0].setCustomLayoutConstraints(myQuadrant);
      myContainer.addComponent(components [0]);
    }

    @Nullable
    public DropLocation getAdjacentLocation(Direction direction) {
      return null;
    }
  }
}

