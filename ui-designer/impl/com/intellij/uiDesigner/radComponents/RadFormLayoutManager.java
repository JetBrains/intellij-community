/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.radComponents;

import com.intellij.openapi.project.Project;
import com.intellij.uiDesigner.UIFormXmlConstants;
import com.intellij.uiDesigner.XmlWriter;
import com.intellij.uiDesigner.GridChangeUtil;
import com.intellij.uiDesigner.compiler.Utils;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.propertyInspector.Property;
import com.intellij.uiDesigner.propertyInspector.properties.HorzAlignProperty;
import com.intellij.uiDesigner.propertyInspector.properties.VertAlignProperty;
import com.jgoodies.forms.factories.FormFactory;
import com.jgoodies.forms.layout.*;
import org.jetbrains.annotations.Nullable;

import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeEvent;
import java.util.Map;
import java.util.HashMap;

/**
 * @author yole
 */
public class RadFormLayoutManager extends RadGridLayoutManager {
  private FormLayoutColumnProperties myPropertiesPanel;
  private Map<RadComponent, MyPropertyChangeListener> myListenerMap = new HashMap<RadComponent, MyPropertyChangeListener>();

  private static CellConstraints.Alignment[] ourHorizontalAlignments = new CellConstraints.Alignment[] {
    CellConstraints.LEFT, CellConstraints.CENTER, CellConstraints.RIGHT, CellConstraints.FILL
  };
  private static CellConstraints.Alignment[] ourVerticalAlignments = new CellConstraints.Alignment[] {
    CellConstraints.TOP, CellConstraints.CENTER, CellConstraints.BOTTOM, CellConstraints.FILL
  };

  @Nullable public String getName() {
    return UIFormXmlConstants.LAYOUT_FORM;
  }

  @Override @Nullable
  public LayoutManager createLayout() {
    return new FormLayout("d:grow", "d:grow");
  }

  @Override
  public void writeLayout(final XmlWriter writer, final RadContainer radContainer) {
    FormLayout layout = (FormLayout) radContainer.getLayout();
    for(int i=1; i<=layout.getRowCount(); i++) {
      RowSpec rowSpec = layout.getRowSpec(i);
      writer.startElement(UIFormXmlConstants.ELEMENT_ROWSPEC);
      try {
        writer.addAttribute(UIFormXmlConstants.ATTRIBUTE_VALUE, Utils.getEncodedSpec(rowSpec));
      }
      finally {
        writer.endElement();
      }
    }
    for(int i=1; i<=layout.getColumnCount(); i++) {
      ColumnSpec columnSpec = layout.getColumnSpec(i);
      writer.startElement(UIFormXmlConstants.ELEMENT_COLSPEC);
      try {
        writer.addAttribute(UIFormXmlConstants.ATTRIBUTE_VALUE, Utils.getEncodedSpec(columnSpec));
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
    container.getDelegee().add(component.getDelegee(), gridToCellConstraints(component), index);
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

  private static CellConstraints gridToCellConstraints(final RadComponent component) {
    GridConstraints gc = component.getConstraints();
    CellConstraints.Alignment hAlign = CellConstraints.DEFAULT;
    CellConstraints.Alignment vAlign = CellConstraints.DEFAULT;

    if (HorzAlignProperty.getInstance(component.getProject()).isModified(component)) {
      hAlign = ourHorizontalAlignments [Utils.alignFromConstraints(gc, true)];
    }
    if (VertAlignProperty.getInstance(component.getProject()).isModified(component)) {
      vAlign = ourVerticalAlignments [Utils.alignFromConstraints(gc, false)];
    }

    return new CellConstraints(gc.getColumn()+1, gc.getRow()+1, gc.getColSpan(), gc.getRowSpan(), hAlign, vAlign);
  }

  @Override public boolean isGrid() {
    return true;
  }

  private static FormLayout getFormLayout(final RadContainer container) {
    return (FormLayout) container.getLayout();
  }

  @Override public int getGridRowCount(RadContainer container) {
    return getFormLayout(container).getRowCount();
  }

  @Override public int getGridColumnCount(RadContainer container) {
    return getFormLayout(container).getColumnCount();
  }

  @Override public int[] getGridCellCoords(RadContainer container, boolean isRow) {
    final FormLayout.LayoutInfo layoutInfo = getFormLayout(container).getLayoutInfo(container.getDelegee());
    int[] origins = isRow ? layoutInfo.rowOrigins : layoutInfo.columnOrigins;
    int[] result = new int [origins.length-1];
    System.arraycopy(origins, 0, result, 0, result.length);
    return result;
  }

  @Override public int[] getGridCellSizes(RadContainer container, boolean isRow) {
    final FormLayout.LayoutInfo layoutInfo = getFormLayout(container).getLayoutInfo(container.getDelegee());
    int[] origins = isRow ? layoutInfo.rowOrigins : layoutInfo.columnOrigins;
    int[] result = new int [origins.length-1];
    for(int i=0; i<result.length; i++) {
      result [i] = origins [i+1] - origins [i];
    }
    return result;
  }

  @Override public int[] getHorizontalGridLines(RadContainer container) {
    final FormLayout.LayoutInfo layoutInfo = getFormLayout(container).getLayoutInfo(container.getDelegee());
    return layoutInfo.rowOrigins;
  }

  @Override public int[] getVerticalGridLines(RadContainer container) {
    final FormLayout.LayoutInfo layoutInfo = getFormLayout(container).getLayoutInfo(container.getDelegee());
    return layoutInfo.columnOrigins;
  }


  @Override public int getGridRowAt(RadContainer container, int y) {
    final FormLayout.LayoutInfo layoutInfo = getFormLayout(container).getLayoutInfo(container.getDelegee());
    return findCell(layoutInfo.rowOrigins, y);
  }

  @Override public int getGridColumnAt(RadContainer container, int x) {
    final FormLayout.LayoutInfo layoutInfo = getFormLayout(container).getLayoutInfo(container.getDelegee());
    return findCell(layoutInfo.columnOrigins, x);
  }

  private static int findCell(final int[] origins, final int coord) {
    for(int i=0; i<origins.length-1; i++) {
      if (coord >= origins [i] && coord < origins [i+1]) return i;
    }
    return -1;
  }

  @Override
  public RowColumnPropertiesPanel getRowColumnPropertiesPanel(RadContainer container, boolean isRow, int[] selectedIndices) {
    if (myPropertiesPanel == null) {
      myPropertiesPanel = new FormLayoutColumnProperties();
    }
    myPropertiesPanel.showProperties(container, isRow, selectedIndices);
    return myPropertiesPanel;
  }

  @Override
  public void paintCaptionDecoration(final RadContainer container, final boolean isRow, final int i, final Graphics2D g2d,
                                     final Rectangle rc) {
    // TODO
  }


  @Override
  public Property[] getContainerProperties(final Project project) {
    return Property.EMPTY_ARRAY; // TODO
  }

  @Override
  public Property[] getComponentProperties(final Project project, final RadComponent component) {
    return new Property[] {
      HorzAlignProperty.getInstance(project),
      VertAlignProperty.getInstance(project)
    };
  }

  @Override
  public int insertGridCells(final RadContainer grid, final int cellIndex, final boolean isRow, final boolean isBefore) {
    FormLayout formLayout = (FormLayout) grid.getLayout();
    int index = isBefore ? cellIndex+1 : cellIndex+2;
    if (isRow) {
      insertOrAppendRow(formLayout, index, FormFactory.RELATED_GAP_ROWSPEC);
      if (!isBefore) index++;
      insertOrAppendRow(formLayout, index, new RowSpec("d:grow"));
    }
    else {
      insertOrAppendColumn(formLayout, index, FormFactory.RELATED_GAP_COLSPEC);
      if (!isBefore) index++;
      insertOrAppendColumn(formLayout, index, new ColumnSpec("d:grow"));
    }
    updateGridConstraintsFromCellConstraints(grid);
    return 2;
  }

  private static void insertOrAppendRow(final FormLayout formLayout, final int index, final RowSpec rowSpec) {
    if (index == formLayout.getRowCount()+1) {
      formLayout.appendRow(rowSpec);
    }
    else {
      formLayout.insertRow(index, rowSpec);
    }
  }

  private static void insertOrAppendColumn(final FormLayout formLayout, final int index, final ColumnSpec columnSpec) {
    if (index == formLayout.getColumnCount()+1) {
      formLayout.appendColumn(columnSpec);
    }
    else {
      formLayout.insertColumn(index, columnSpec);
    }
  }

  @Override
  public void deleteGridCells(final RadContainer grid, final int cellIndex, final boolean isRow) {
    FormLayout formLayout = (FormLayout) grid.getLayout();
    if (isRow) {
      formLayout.removeRow(cellIndex+1);
      if (formLayout.getRowCount() % 2 == 0) {
        int gapRowIndex = (cellIndex >= grid.getGridRowCount()) ? cellIndex-1 : cellIndex;
        if (GridChangeUtil.isRowEmpty(grid, gapRowIndex)) {
          formLayout.removeRow(gapRowIndex+1);
        }
      }
    }
    else {
      formLayout.removeColumn(cellIndex+1);
      if (formLayout.getColumnCount() % 2 == 0) {
        int gapColumnIndex = (cellIndex >= grid.getGridColumnCount()) ? cellIndex-1 : cellIndex;
        if (GridChangeUtil.isColumnEmpty(grid, gapColumnIndex)) {
          formLayout.removeColumn(gapColumnIndex+1);
        }
      }
    }
    updateGridConstraintsFromCellConstraints(grid);
  }

  private static void updateGridConstraintsFromCellConstraints(RadContainer grid) {
    FormLayout layout = (FormLayout) grid.getLayout();
    for(RadComponent c: grid.getComponents()) {
      CellConstraints cc = layout.getConstraints(c.getDelegee());
      GridConstraints gc = c.getConstraints();
      gc.setColumn(cc.gridX-1);
      gc.setRow(cc.gridY-1);
      gc.setColSpan(cc.gridWidth);
      gc.setRowSpan(cc.gridHeight);
    }
  }

  private static class MyPropertyChangeListener implements PropertyChangeListener {
    private final RadComponent myComponent;

    public MyPropertyChangeListener(final RadComponent component) {
      myComponent = component;
    }

    public void propertyChange(PropertyChangeEvent evt) {
      if (evt.getPropertyName().equals(RadComponent.PROP_CONSTRAINTS)) {
        FormLayout layout = (FormLayout) myComponent.getParent().getLayout();
        layout.setConstraints(myComponent.getDelegee(), gridToCellConstraints(myComponent));
      }
    }
  }
}
