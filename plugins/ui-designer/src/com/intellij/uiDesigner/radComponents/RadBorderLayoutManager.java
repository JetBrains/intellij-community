// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.uiDesigner.radComponents;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.uiDesigner.UIFormXmlConstants;
import com.intellij.uiDesigner.XmlWriter;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.designSurface.ComponentDragObject;
import com.intellij.uiDesigner.designSurface.ComponentDropLocation;
import com.intellij.uiDesigner.designSurface.FeedbackLayer;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.propertyInspector.InplaceContext;
import com.intellij.uiDesigner.propertyInspector.Property;
import com.intellij.uiDesigner.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.propertyInspector.PropertyRenderer;
import com.intellij.uiDesigner.propertyInspector.editors.ComboBoxPropertyEditor;
import com.intellij.uiDesigner.propertyInspector.properties.HGapProperty;
import com.intellij.uiDesigner.propertyInspector.properties.VGapProperty;
import com.intellij.uiDesigner.propertyInspector.renderers.LabelPropertyRenderer;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;


public class RadBorderLayoutManager extends RadLayoutManager {
  @Override
  public String getName() {
    return UIFormXmlConstants.LAYOUT_BORDER;
  }

  @Override
  public LayoutManager createLayout() {
    return new BorderLayout();
  }

  @Override
  public void writeLayout(final XmlWriter writer, final RadContainer radContainer) {
    BorderLayout layout = (BorderLayout) radContainer.getLayout();
    writer.addAttribute(UIFormXmlConstants.ATTRIBUTE_HGAP, layout.getHgap());
    writer.addAttribute(UIFormXmlConstants.ATTRIBUTE_VGAP, layout.getVgap());
  }

  @Override
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

  @Override
  public void writeChildConstraints(final XmlWriter writer, final RadComponent child) {
    writer.addAttribute(UIFormXmlConstants.ATTRIBUTE_BORDER_CONSTRAINT, (String) child.getCustomLayoutConstraints());
  }

  @Override
  public @NotNull ComponentDropLocation getDropLocation(RadContainer container, final Point location) {
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
    ArrayList<RadComponent> componentsInBorder = new ArrayList<>();

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

    if ((container.getComponentCount() > 0 && componentsInBorder.isEmpty()) || componentsInBorder.size() > 3) {
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

  private static void copyGridLine(final RadContainer container, final ArrayList<? super RadComponent> componentsInBorder, boolean isRow) {
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

  @Override
  public Property[] getComponentProperties(final Project project, final RadComponent component) {
    return new Property[] {
      BorderSideProperty.INSTANCE
    };
  }

  @Override
  public boolean canMoveComponent(final RadComponent c, final int rowDelta, final int colDelta, final int rowSpanDelta, final int colSpanDelta) {
    if (rowSpanDelta != 0 || colSpanDelta != 0) {
      return false;
    }
    String side = (String) c.getCustomLayoutConstraints();
    String adjSide = getAdjacentSide(side, rowDelta, colDelta);
    return adjSide != null && c.getParent().findComponentWithConstraints(adjSide) == null;
  }

  @Override
  public void moveComponent(final RadComponent c, final int rowDelta, final int colDelta, final int rowSpanDelta, final int colSpanDelta) {
    String side = (String) c.getCustomLayoutConstraints();
    String adjSide = getAdjacentSide(side, rowDelta, colDelta);
    if (adjSide != null) {
      c.changeCustomLayoutConstraints(adjSide);
    }
  }

  private static @Nullable String getAdjacentSide(final String side, final int rowDelta, final int colDelta) {
    if (rowDelta == -1 && colDelta == 0) {
      return getAdjacentSide(side, BorderLayout.NORTH, BorderLayout.SOUTH);
    }
    if (rowDelta == 1 && colDelta == 0) {
      return getAdjacentSide(side, BorderLayout.SOUTH, BorderLayout.NORTH);
    }
    if (rowDelta == 0 && colDelta == -1) {
      return getAdjacentSide(side, BorderLayout.WEST, BorderLayout.EAST);
    }
    if (rowDelta == 0 && colDelta == 1) {
      return getAdjacentSide(side, BorderLayout.EAST, BorderLayout.WEST);
    }
    return null;
  }

  private static @Nullable String getAdjacentSide(final String side, final String toEdge, final String fromEdge) {
    if (side.equals(toEdge)) {
      return null;
    }
    if (side.equals(fromEdge)) {
      return BorderLayout.CENTER;
    }
    return toEdge;
  }

  private static class MyDropLocation implements ComponentDropLocation {
    private final RadContainer myContainer;
    private final String myQuadrant;

    MyDropLocation(final RadContainer container, final String quadrant) {
      myQuadrant = quadrant;
      myContainer = container;
    }

    @Override
    public RadContainer getContainer() {
      return myContainer;
    }

    @Override
    public boolean canDrop(ComponentDragObject dragObject) {
      return dragObject.getComponentCount() == 1 &&
             ((BorderLayout) myContainer.getLayout()).getLayoutComponent(myQuadrant) == null;
    }

    @Override
    public void placeFeedback(FeedbackLayer feedbackLayer, ComponentDragObject dragObject) {
      Dimension initialSize = dragObject.getInitialSize(myContainer);
      feedbackLayer.putFeedback(myContainer.getDelegee(), getFeedbackRect(myQuadrant, initialSize),
                                myContainer.getDisplayName() + " (" + StringUtil.toLowerCase(myQuadrant) + ")");
    }

    private Rectangle getFeedbackRect(final String quadrant, final Dimension initialSize) {
      Dimension size = myContainer.getDelegee().getSize();
      int initialWidth = (initialSize.width > 0 && initialSize.width < size.width) ? initialSize.width : size.width/3;
      int initialHeight = (initialSize.height > 0 && initialSize.height < size.height) ? initialSize.height: size.height/3;
      if (quadrant.equals(BorderLayout.WEST)) {
        int deltaN = getHeightAtConstraint(BorderLayout.NORTH);
        int deltaS = getHeightAtConstraint(BorderLayout.SOUTH);
        return new Rectangle(0, deltaN, initialWidth, size.height - deltaN - deltaS);
      }
      if (quadrant.equals(BorderLayout.NORTH)) {
        return new Rectangle(0, 0, size.width, initialHeight);
      }
      if (quadrant.equals(BorderLayout.EAST)) {
        int deltaN = getHeightAtConstraint(BorderLayout.NORTH);
        int deltaS = getHeightAtConstraint(BorderLayout.SOUTH);
        return new Rectangle(size.width - initialWidth, deltaN, initialWidth, size.height - deltaN - deltaS);
      }
      if (quadrant.equals(BorderLayout.SOUTH)) {
        return new Rectangle(0, size.height - initialHeight, size.width, initialHeight);
      }
      return new Rectangle(size.width/3, size.height/3, size.width/3, size.height/3);
    }

    private int getHeightAtConstraint(final String constraint) {
      BorderLayout layout = (BorderLayout) myContainer.getLayout();
      Component c = layout.getLayoutComponent(myContainer.getDelegee(), constraint);
      if (c == null) {
        return 0;
      }
      return c.getBounds().height;
    }

    @Override
    public void processDrop(GuiEditor editor,
                            RadComponent[] components,
                            GridConstraints[] constraintsToAdjust,
                            ComponentDragObject dragObject) {
      components [0].setCustomLayoutConstraints(myQuadrant);
      myContainer.addComponent(components [0]);
    }

    @Override
    public @Nullable ComponentDropLocation getAdjacentLocation(Direction direction) {
      String side = switch (direction) {
        case LEFT -> getAdjacentSide(myQuadrant, 0, -1);
        case UP -> getAdjacentSide(myQuadrant, -1, 0);
        case RIGHT -> getAdjacentSide(myQuadrant, 0, 1);
        case DOWN -> getAdjacentSide(myQuadrant, 1, 0);
      };
      if (side != null) {
        return new MyDropLocation(myContainer, side);
      }
      return null;
    }
  }

  private static class BorderSideProperty extends Property<RadComponent, String> {
    private LabelPropertyRenderer<String> myRenderer = null;
    private BorderSideEditor myEditor = null;

    public static final BorderSideProperty INSTANCE = new BorderSideProperty();

    BorderSideProperty() {
      super(null, "Border Side");
    }

    @Override
    public String getValue(RadComponent component) {
      return (String) component.getCustomLayoutConstraints();
    }

    @Override
    protected void setValueImpl(RadComponent component, String value) throws Exception {
      if (!value.equals(component.getCustomLayoutConstraints())) {
        if (component.getParent().findComponentWithConstraints(value) != null) {
          throw new Exception("There is already another component at location " + value);
        }
        component.changeCustomLayoutConstraints(value);
      }
    }

    @Override
    public @NotNull PropertyRenderer<String> getRenderer() {
      if (myRenderer == null) {
        myRenderer = new LabelPropertyRenderer<>();
      }
      return myRenderer;
    }

    @Override
    public PropertyEditor<String> getEditor() {
      if (myEditor == null) {
        myEditor = new BorderSideEditor();
      }
      return myEditor;
    }
  }

  private static class BorderSideEditor extends ComboBoxPropertyEditor<String> {
    BorderSideEditor() {
      @NlsSafe String[] sides = new String[] {
        BorderLayout.CENTER, BorderLayout.NORTH, BorderLayout.SOUTH, BorderLayout.WEST, BorderLayout.EAST,
        BorderLayout.PAGE_START, BorderLayout.PAGE_END, BorderLayout.LINE_START, BorderLayout.LINE_END
      };
      myCbx.setModel(new DefaultComboBoxModel(sides));
    }

    @Override
    public JComponent getComponent(RadComponent component, @NlsSafe String value, InplaceContext inplaceContext) {
      myCbx.setSelectedItem(value);
      return myCbx;
    }
  }
}

