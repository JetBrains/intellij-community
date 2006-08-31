/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.radComponents;

import com.intellij.openapi.project.Project;
import com.intellij.uiDesigner.UIFormXmlConstants;
import com.intellij.uiDesigner.XmlWriter;
import com.intellij.uiDesigner.compiler.GridBagConverter;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.Util;
import com.intellij.uiDesigner.designSurface.DropLocation;
import com.intellij.uiDesigner.designSurface.FirstComponentInsertLocation;
import com.intellij.uiDesigner.propertyInspector.Property;
import com.intellij.uiDesigner.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.propertyInspector.PropertyRenderer;
import com.intellij.uiDesigner.propertyInspector.editors.DoubleEditor;
import com.intellij.uiDesigner.propertyInspector.properties.AbstractInsetsProperty;
import com.intellij.uiDesigner.propertyInspector.properties.AbstractIntProperty;
import com.intellij.uiDesigner.propertyInspector.properties.HorzAlignProperty;
import com.intellij.uiDesigner.propertyInspector.properties.VertAlignProperty;
import com.intellij.uiDesigner.propertyInspector.renderers.LabelPropertyRenderer;
import com.intellij.uiDesigner.snapShooter.SnapshotContext;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.HashMap;
import java.util.Map;

/**
 * @author yole
 */
public class RadGridBagLayoutManager extends RadAbstractGridLayoutManager {
  private int myLastSnapshotRow = -1;
  private int myLastSnapshotCol = -1;
  private int[] mySnapshotXMax = new int[512];
  private int[] mySnapshotYMax = new int[512];
  private Map<RadComponent, MyPropertyChangeListener> myListenerMap = new HashMap<RadComponent, MyPropertyChangeListener>();

  @Override public String getName() {
    return UIFormXmlConstants.LAYOUT_GRIDBAG;
  }

  @Override
  public LayoutManager createLayout() {
    return new GridBagLayout();
  }

  @Override
  public void changeContainerLayout(RadContainer container) throws IncorrectOperationException {
    if (container.getLayoutManager().isGrid()) {
      // preprocess: store weights in GridBagConstraints
      RadAbstractGridLayoutManager grid = container.getGridLayoutManager();
      for(RadComponent c: container.getComponents()) {
        GridBagConstraints gbc = GridBagConverter.getGridBagConstraints(c);
        if (grid.canCellGrow(container, false, c.getConstraints().getColumn())) {
          gbc.weightx = 1.0;
        }
        if (grid.canCellGrow(container, true, c.getConstraints().getRow())) {
          gbc.weighty = 1.0;
        }
        c.setCustomLayoutConstraints(gbc);
      }
    }
    super.changeContainerLayout(container);
  }

  public void writeChildConstraints(final XmlWriter writer, final RadComponent child) {
    writeGridConstraints(writer, child);
    if (child.getCustomLayoutConstraints() instanceof GridBagConstraints) {
      GridBagConstraints gbc = (GridBagConstraints) child.getCustomLayoutConstraints();
      writer.startElement(UIFormXmlConstants.ELEMENT_GRIDBAG);
      try {
        if (!gbc.insets.equals(new Insets(0, 0, 0, 0))) {
          writer.addAttribute(UIFormXmlConstants.ATTRIBUTE_TOP, gbc.insets.top);
          writer.addAttribute(UIFormXmlConstants.ATTRIBUTE_LEFT, gbc.insets.left);
          writer.addAttribute(UIFormXmlConstants.ATTRIBUTE_BOTTOM, gbc.insets.bottom);
          writer.addAttribute(UIFormXmlConstants.ATTRIBUTE_RIGHT, gbc.insets.right);
        }
        writer.addAttribute(UIFormXmlConstants.ATTRIBUTE_WEIGHTX, gbc.weightx);
        writer.addAttribute(UIFormXmlConstants.ATTRIBUTE_WEIGHTY, gbc.weighty);
        if (gbc.ipadx != 0) {
          writer.addAttribute(UIFormXmlConstants.ATTRIBUTE_IPADX, gbc.ipadx);
        }
        if (gbc.ipady != 0) {
          writer.addAttribute(UIFormXmlConstants.ATTRIBUTE_IPADY, gbc.ipady);
        }
      }
      finally {
        writer.endElement();
      }
    }
  }

  @Override
  public void addComponentToContainer(final RadContainer container, final RadComponent component, final int index) {
    MyPropertyChangeListener listener = new MyPropertyChangeListener(component);
    myListenerMap.put(component, listener);
    component.addPropertyChangeListener(listener);
    GridBagConstraints gbc = GridBagConverter.getGridBagConstraints(component);
    component.setCustomLayoutConstraints(gbc);
    container.getDelegee().add(component.getDelegee(), gbc, index);
  }

  @Override
  public void removeComponentFromContainer(final RadContainer container, final RadComponent component) {
    final MyPropertyChangeListener listener = myListenerMap.get(component);
    if (listener != null) {
      component.removePropertyChangeListener(listener);
      myListenerMap.remove(component);
    }
    super.removeComponentFromContainer(container, component);
  }

  @Override
  public Property[] getContainerProperties(final Project project) {
    return Property.EMPTY_ARRAY;
  }

  @Override
  public Property[] getComponentProperties(final Project project, final RadComponent component) {
    return new Property[] {
      new HorzAlignProperty(),
      new VertAlignProperty(),
      new ComponentInsetsProperty(),
      new WeightProperty(true),
      new WeightProperty(false),
      new IPadProperty(true),
      new IPadProperty(false)
    };
  }
  
  private static GridBagLayout getGridBag(RadContainer container) {
    return (GridBagLayout) container.getLayout();
  }

  @Override
  public int getGridRowCount(RadContainer container) {
    int[][] layoutDimensions = getGridBag(container).getLayoutDimensions();
    return layoutDimensions [1].length;
  }

  @Override
  public int getGridColumnCount(RadContainer container) {
    int[][] layoutDimensions = getGridBag(container).getLayoutDimensions();
    return layoutDimensions [0].length;
  }

  @Override
  public int[] getHorizontalGridLines(RadContainer container) {
    return getGridLines(container, 1, 1);
  }

  @Override
  public int[] getVerticalGridLines(RadContainer container) {
    return getGridLines(container, 0, 1);
  }

  @Override
  public int[] getGridCellCoords(RadContainer container, boolean isRow) {
    return getGridLines(container, isRow ? 1 : 0, 0);
  }

  @Override
  public int[] getGridCellSizes(RadContainer container, boolean isRow) {
    int[][] layoutDimensions = getGridBag(container).getLayoutDimensions();
    return layoutDimensions [isRow ? 1 : 0];
  }

  private static int[] getGridLines(final RadContainer container, final int rowColIndex, final int delta) {
    final GridBagLayout gridBag = getGridBag(container);
    Point layoutOrigin = gridBag.getLayoutOrigin();
    int[][] layoutDimensions = gridBag.getLayoutDimensions();
    int[] result = new int [layoutDimensions [rowColIndex].length+delta];
    if (result.length > 0) {
      result [0] = (rowColIndex == 0) ? layoutOrigin.x : layoutOrigin.y;
      for(int i=1; i<result.length; i++) {
        result [i] = result [i-1]+layoutDimensions [rowColIndex] [i-1];
      }
    }
    return result;
  }

  @NotNull @Override
  public DropLocation getDropLocation(@NotNull RadContainer container, @Nullable final Point location) {
    if (getGridRowCount(container) == 0 && getGridColumnCount(container) == 0) {
      return new FirstComponentInsertLocation(container, container.getBounds(), 0, 0);
    }
    return super.getDropLocation(container, location);
  }

  public void copyGridSection(final RadContainer source, final RadContainer destination, final Rectangle rc) {
    destination.setLayout(new GridBagLayout());
  }

  private static void updateConstraints(final RadComponent component) {
    GridBagLayout layout = (GridBagLayout) component.getParent().getLayout();
    GridBagConstraints gbc = GridBagConverter.getGridBagConstraints(component);
    layout.setConstraints(component.getDelegee(), gbc);
    component.getParent().revalidate();
  }

  @Override
  public boolean isGridDefinedByComponents() {
    return true;
  }

  @Override
  public boolean canResizeCells() {
    return false;
  }

  @Override
  public boolean canCellGrow(RadContainer container, boolean isRow, int i) {
    GridBagLayout gridBag = getGridBag(container);
    double[][] weights = gridBag.getLayoutWeights();
    if (weights != null) {
      double[] cellWeights = weights [isRow ? 1 : 0];
      return i >= 0 && i < cellWeights.length && cellWeights [i] >= 0.1;
    }
    return false;
  }

  @Override
  public void createSnapshotLayout(final SnapshotContext context,
                                   final JComponent parent,
                                   final RadContainer container,
                                   final LayoutManager layout) {
    container.setLayout(new GridBagLayout());
  }

  public static Dimension getGridBagSize(final JComponent parent, final LayoutManager layout) {
    GridBagLayout gridBag = (GridBagLayout) layout;
    int[][] layoutDimensions = gridBag.getLayoutDimensions();

    int rowCount = layoutDimensions[1].length;
    int colCount = layoutDimensions[0].length;

    // account for invisible components
    for(Component component: parent.getComponents()) {
      final GridBagConstraints constraints = gridBag.getConstraints(component);
      colCount = Math.max(colCount, constraints.gridx + constraints.gridwidth);
      rowCount = Math.max(rowCount, constraints.gridy + constraints.gridheight);
    }

    return new Dimension(colCount, rowCount);
  }

  @Override
  public void addSnapshotComponent(final JComponent parent,
                                   final JComponent child,
                                   final RadContainer container,
                                   final RadComponent component) {
    GridBagLayout grid = (GridBagLayout) container.getLayout();
    Dimension gridBagSize = getGridBagSize(parent, grid);

    // logic copied from GridBagLayout.java

    GridBagLayout gridBag = (GridBagLayout) parent.getLayout();
    final GridBagConstraints constraints = gridBag.getConstraints(child);

    int curX = constraints.gridx;
    int curY = constraints.gridy;
    int curWidth = constraints.gridwidth;
    int curHeight = constraints.gridheight;
    int px;
    int py;

    /* If x or y is negative, then use relative positioning: */
    if (curX < 0 && curY < 0) {
      if(myLastSnapshotRow >= 0)
        curY = myLastSnapshotRow;
      else if(myLastSnapshotCol >= 0)
        curX = myLastSnapshotCol;
      else
        curY = 0;
    }

    if (curX < 0) {
      if (curHeight <= 0) {
        curHeight += gridBagSize.height - curY;
        if (curHeight < 1)
          curHeight = 1;
      }

      px = 0;
      for (int i = curY; i < (curY + curHeight); i++)
        px = Math.max(px, mySnapshotXMax[i]);

      curX = px - curX - 1;
      if(curX < 0)
        curX = 0;
    }
    else if (curY < 0) {
      if (curWidth <= 0) {
        curWidth += gridBagSize.width - curX;
        if (curWidth < 1)
          curWidth = 1;
      }

      py = 0;
      for (int i = curX; i < (curX + curWidth); i++)
        py = Math.max(py, mySnapshotYMax[i]);

      curY = py - curY - 1;
      if(curY < 0)
        curY = 0;
    }

    if (curWidth <= 0) {
      curWidth += gridBagSize.width - curX;
      if (curWidth < 1)
        curWidth = 1;
    }

    if (curHeight <= 0) {
      curHeight += gridBagSize.height - curY;
      if (curHeight < 1)
        curHeight = 1;
    }

    /* Make negative sizes start a new row/column */
    if (constraints.gridheight == 0 && constraints.gridwidth == 0)
      myLastSnapshotRow = myLastSnapshotCol = -1;
    if (constraints.gridheight == 0 && myLastSnapshotRow < 0)
      myLastSnapshotCol = curX + curWidth;
    else if (constraints.gridwidth == 0 && myLastSnapshotCol < 0)
      myLastSnapshotRow = curY + curHeight;

    component.getConstraints().setColumn(curX);
    component.getConstraints().setRow(curY);
    component.getConstraints().setColSpan(curWidth);
    component.getConstraints().setRowSpan(curHeight);

    if (constraints.weightx >= 1.0) {
      component.getConstraints().setHSizePolicy(GridConstraints.SIZEPOLICY_CAN_GROW | GridConstraints.SIZEPOLICY_WANT_GROW);
    }
    else {                                                                
      component.getConstraints().setHSizePolicy(0);
    }
    if (constraints.weighty >= 1.0) {
      component.getConstraints().setVSizePolicy(GridConstraints.SIZEPOLICY_CAN_GROW | GridConstraints.SIZEPOLICY_WANT_GROW);
    }
    else {
      component.getConstraints().setVSizePolicy(0);
    }
    if (constraints.insets.right == 0 && constraints.insets.top == 0 && constraints.insets.bottom == 0) {
      component.getConstraints().setIndent(constraints.insets.left / Util.DEFAULT_INDENT);
    }

    component.getConstraints().setAnchor(convertAnchor(constraints));
    component.getConstraints().setFill(convertFill(constraints));
    component.setCustomLayoutConstraints(constraints.clone());
    container.addComponent(component);
  }

  private static int convertAnchor(final GridBagConstraints gbc) {
    switch(gbc.anchor) {
      case GridBagConstraints.NORTHWEST: return GridConstraints.ANCHOR_NORTHWEST;
      case GridBagConstraints.NORTH:     return GridConstraints.ANCHOR_NORTH;
      case GridBagConstraints.NORTHEAST: return GridConstraints.ANCHOR_NORTHEAST;
      case GridBagConstraints.EAST:      return GridConstraints.ANCHOR_EAST;
      case GridBagConstraints.SOUTHEAST: return GridConstraints.ANCHOR_SOUTHEAST;
      case GridBagConstraints.SOUTH:     return GridConstraints.ANCHOR_SOUTH;
      case GridBagConstraints.SOUTHWEST: return GridConstraints.ANCHOR_SOUTHWEST;
      default:                           return GridConstraints.ANCHOR_WEST;
    }
  }

  private static int convertFill(final GridBagConstraints gbc) {
    switch(gbc.fill) {
      case GridBagConstraints.HORIZONTAL: return GridConstraints.FILL_HORIZONTAL;
      case GridBagConstraints.VERTICAL: return GridConstraints.FILL_VERTICAL;
      case GridBagConstraints.BOTH: return GridConstraints.FILL_BOTH;
      default: return GridConstraints.FILL_NONE;
    }
  }

  private static class MyPropertyChangeListener implements PropertyChangeListener {
    private final RadComponent myComponent;

    public MyPropertyChangeListener(final RadComponent component) {
      myComponent = component;
    }

    public void propertyChange(PropertyChangeEvent evt) {
      if (evt.getPropertyName().equals(RadComponent.PROP_CONSTRAINTS)) {
        updateConstraints(myComponent);
      }
    }
  }

  private static class ComponentInsetsProperty extends AbstractInsetsProperty<RadComponent> {
    public ComponentInsetsProperty() {
      super(null, "Insets");
    }

    public Insets getValue(final RadComponent component) {
      if (component.getCustomLayoutConstraints() instanceof GridBagConstraints) {
        final GridBagConstraints gbc = (GridBagConstraints)component.getCustomLayoutConstraints();
        return gbc.insets;
      }
      return new Insets(0, 0, 0, 0);
    }

    protected void setValueImpl(final RadComponent component, final Insets value) throws Exception {
      if (component.getCustomLayoutConstraints() instanceof GridBagConstraints) {
        final GridBagConstraints cellConstraints = (GridBagConstraints)component.getCustomLayoutConstraints();
        cellConstraints.insets = value;

        GridBagLayout layout = (GridBagLayout) component.getParent().getLayout();
        GridBagConstraints gbc = (GridBagConstraints)layout.getConstraints(component.getDelegee()).clone();
        gbc.insets = value;
        layout.setConstraints(component.getDelegee(), gbc);
      }
    }
  }

  private static class WeightProperty extends Property<RadComponent, Double> {
    private boolean myIsWeightX;
    private LabelPropertyRenderer<Double> myRenderer;
    private DoubleEditor myEditor;

    public WeightProperty(final boolean isWeightX) {
      super(null, isWeightX ? "Weight X" : "Weight Y");
      myIsWeightX = isWeightX;
    }

    public Double getValue(final RadComponent component) {
      if (component.getCustomLayoutConstraints() instanceof GridBagConstraints) {
        GridBagConstraints gbc = (GridBagConstraints) component.getCustomLayoutConstraints();
        return myIsWeightX ? gbc.weightx : gbc.weighty;
      }
      return 0.0;
    }

    protected void setValueImpl(final RadComponent component, final Double value) throws Exception {
      if (component.getCustomLayoutConstraints() instanceof GridBagConstraints) {
        GridBagConstraints gbc = (GridBagConstraints) component.getCustomLayoutConstraints();
        if (myIsWeightX) {
          gbc.weightx = value.doubleValue();
        }
        else {
          gbc.weighty = value.doubleValue();
        }
        ((GridBagLayout) component.getParent().getLayout()).setConstraints(component.getDelegee(), gbc);
      }
    }

    @NotNull
    public PropertyRenderer<Double> getRenderer() {
      if (myRenderer == null) {
        myRenderer = new LabelPropertyRenderer<Double>();
      }
      return myRenderer;
    }

    public PropertyEditor<Double> getEditor() {
      if (myEditor == null) {
        myEditor = new DoubleEditor();
      }
      return myEditor;
    }

    @Override
    public boolean isModified(final RadComponent component) {
      return !(new Double(0.0).equals(getValue(component)));
    }

    @Override
    public void resetValue(final RadComponent component) throws Exception {
      setValue(component, 0.0);
    }
  }

  private static class IPadProperty extends AbstractIntProperty<RadComponent> {
    private boolean myIsIpadX;

    public IPadProperty(final boolean isIpadX) {
      super(null, isIpadX ? "Ipad X" : "Ipad Y", 0);
      myIsIpadX = isIpadX;
    }

    public Integer getValue(final RadComponent component) {
      if (component.getCustomLayoutConstraints() instanceof GridBagConstraints) {
        GridBagConstraints gbc = (GridBagConstraints) component.getCustomLayoutConstraints();
        return myIsIpadX ? gbc.ipadx : gbc.ipady;
      }
      return 0;
    }

    protected void setValueImpl(final RadComponent component, final Integer value) throws Exception {
      if (component.getCustomLayoutConstraints() instanceof GridBagConstraints) {
        GridBagConstraints gbc = (GridBagConstraints) component.getCustomLayoutConstraints();
        if (myIsIpadX) {
          gbc.ipadx = value.intValue();
        }
        else {
          gbc.ipady = value.intValue();
        }
        ((GridBagLayout) component.getParent().getLayout()).setConstraints(component.getDelegee(), gbc);
      }
    }
  }
}
