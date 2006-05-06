/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.radComponents;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.uiDesigner.UIFormXmlConstants;
import com.intellij.uiDesigner.XmlWriter;
import com.intellij.uiDesigner.GridChangeUtil;
import com.intellij.uiDesigner.actions.*;
import com.intellij.uiDesigner.compiler.Utils;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.propertyInspector.Property;
import com.intellij.uiDesigner.propertyInspector.PropertyRenderer;
import com.intellij.uiDesigner.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.propertyInspector.editors.IntRegexEditor;
import com.intellij.uiDesigner.propertyInspector.renderers.InsetsPropertyRenderer;
import com.intellij.uiDesigner.propertyInspector.properties.HorzAlignProperty;
import com.intellij.uiDesigner.propertyInspector.properties.VertAlignProperty;
import com.intellij.uiDesigner.propertyInspector.properties.IntFieldProperty;
import com.intellij.util.IncorrectOperationException;
import com.jgoodies.forms.factories.FormFactory;
import com.jgoodies.forms.layout.*;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

import java.awt.*;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeEvent;
import java.util.*;
import java.util.List;

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
  @NonNls private static final String ENCODED_FORMSPEC_GROW = "d:grow";

  @Nullable public String getName() {
    return UIFormXmlConstants.LAYOUT_FORM;
  }

  @Override @Nullable
  public LayoutManager createLayout() {
    return new FormLayout(ENCODED_FORMSPEC_GROW, ENCODED_FORMSPEC_GROW);
  }

  @Override
  public void changeContainerLayout(RadContainer container) throws IncorrectOperationException {
    if (container.getLayout() instanceof GridLayoutManager) {
      GridLayoutManager grid = (GridLayoutManager) container.getLayout();

      RowSpec[] rowSpecs = new RowSpec [grid.getRowCount() * 2 - 1];
      ColumnSpec[] colSpecs = new ColumnSpec [grid.getColumnCount() * 2 - 1];

      int maxSizePolicy = 0;
      for(int i=0; i<grid.getRowCount(); i++) {
        maxSizePolicy = Math.max(maxSizePolicy, grid.getCellSizePolicy(true, i));
      }
      for(int i=0; i<grid.getRowCount(); i++) {
        int sizePolicy = grid.getCellSizePolicy(true, i);
        rowSpecs [i*2] = (sizePolicy < maxSizePolicy) ? FormFactory.DEFAULT_ROWSPEC : new RowSpec(ENCODED_FORMSPEC_GROW);
        if (i*2+1 < rowSpecs.length) {
          rowSpecs [i*2+1] = FormFactory.RELATED_GAP_ROWSPEC;
        }
      }
      maxSizePolicy = 0;
      for(int i=0; i<grid.getColumnCount(); i++) {
        maxSizePolicy = Math.max(maxSizePolicy, grid.getCellSizePolicy(false, i));
      }
      for(int i=0; i<grid.getColumnCount(); i++) {
        int sizePolicy = grid.getCellSizePolicy(true, i);
        colSpecs [i*2] = (sizePolicy < maxSizePolicy) ? FormFactory.DEFAULT_COLSPEC : new ColumnSpec(ENCODED_FORMSPEC_GROW);
        if (i*2+1 < colSpecs.length) {
          colSpecs [i*2+1] = FormFactory.RELATED_GAP_COLSPEC;
        }
      }

      List<RadComponent> contents = new ArrayList<RadComponent>();
      for(int i=container.getComponentCount()-1; i >= 0; i--) {
        final RadComponent component = container.getComponent(i);
        if (!(component instanceof RadHSpacer) && !(component instanceof RadVSpacer)) {
          contents.add(0, component);
        }
        container.removeComponent(component);
      }

      container.setLayoutManager(this, new FormLayout(colSpecs, rowSpecs));
      for(RadComponent c: contents) {
        GridConstraints gc = c.getConstraints();
        gc.setRow(gc.getRow() * 2);
        gc.setColumn(gc.getColumn() * 2);
        container.addComponent(c);
      }
    }
    else if (container.getComponentCount() == 0) {
      container.setLayoutManager(this, new FormLayout(ENCODED_FORMSPEC_GROW, ENCODED_FORMSPEC_GROW));
    }
    else {
      throw new IncorrectOperationException("Cannot change from " + container.getLayout() + " to grid layout");
    }
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
    writeGroups(writer, UIFormXmlConstants.ELEMENT_ROWGROUP, layout.getRowGroups());
    writeGroups(writer, UIFormXmlConstants.ELEMENT_COLGROUP, layout.getColumnGroups());
  }

  private static void writeGroups(final XmlWriter writer, final String elementName, final int[][] groups) {
    for(int[] group: groups) {
      writer.startElement(elementName);
      try {
        for(int member: group) {
          writer.startElement(UIFormXmlConstants.ELEMENT_MEMBER);
          writer.addAttribute(UIFormXmlConstants.ATTRIBUTE_INDEX, member);
          writer.endElement();
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
    final CellConstraints cc = gridToCellConstraints(component);
    if (component.getCustomLayoutConstraints() instanceof CellConstraints) {
      CellConstraints customCellConstraints = (CellConstraints) component.getCustomLayoutConstraints();
      cc.insets = customCellConstraints.insets;
    }
    component.setCustomLayoutConstraints(cc);
    container.getDelegee().add(component.getDelegee(), cc, index);
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

  @Override
  public void writeChildConstraints(final XmlWriter writer, final RadComponent child) {
    super.writeChildConstraints(writer, child);
    if (child.getCustomLayoutConstraints() instanceof CellConstraints) {
      CellConstraints cc = (CellConstraints) child.getCustomLayoutConstraints();
      writer.startElement(UIFormXmlConstants.ELEMENT_FORMS);
      try {
        if (!cc.insets.equals(new Insets(0, 0, 0, 0))) {
          writer.addAttribute(UIFormXmlConstants.ATTRIBUTE_TOP, cc.insets.top);
          writer.addAttribute(UIFormXmlConstants.ATTRIBUTE_LEFT, cc.insets.left);
          writer.addAttribute(UIFormXmlConstants.ATTRIBUTE_BOTTOM, cc.insets.bottom);
          writer.addAttribute(UIFormXmlConstants.ATTRIBUTE_RIGHT, cc.insets.right);
        }
      }
      finally {
        writer.endElement();
      }
    }
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
  public ActionGroup getCaptionActions() {
    DefaultActionGroup group = new DefaultActionGroup();
    group.add(new InsertBeforeAction());
    group.add(new InsertAfterAction());
    group.add(new DeleteAction());
    group.add(new GroupRowsColumnsAction());
    group.add(new UngroupRowsColumnsAction());
    return group;
  }

  @Override
  public void paintCaptionDecoration(final RadContainer container, final boolean isRow, final int index, final Graphics2D g2d,
                                     final Rectangle rc) {
    // don't paint gap rows/columns with red background
    if (index % 2 == 1) {
      g2d.setColor(Color.LIGHT_GRAY);
      g2d.fillRect(rc.x, rc.y, rc.width, rc.height);
    }

    FormLayout layout = (FormLayout) container.getLayout();
    int[][] groups = isRow ? layout.getRowGroups() : layout.getColumnGroups();
    //noinspection MultipleVariablesInDeclaration
    boolean haveTopLeft = false, haveTopRight = false, haveTopLine = false;
    //noinspection MultipleVariablesInDeclaration
    boolean haveBottomLeft = false, haveBottomRight = false, haveBottomLine = false;
    for(int i=0; i<groups.length; i++) {
      int minMember = Integer.MAX_VALUE;
      int maxMember = -1;
      for(int member: groups [i]) {
        minMember = Math.min(member-1, minMember);
        maxMember = Math.max(member-1, maxMember);
      }
      if (minMember <= index && index <= maxMember) {
        if (i % 2 == 0) {
          haveTopLeft = haveTopLeft || index > minMember;
          haveTopRight = haveTopRight || index < maxMember;
          haveTopLine = haveTopLine || index == minMember || index == maxMember;
        }
        else {
          haveBottomLeft = haveBottomLeft || index > minMember;
          haveBottomRight = haveBottomRight || index < maxMember;
          haveBottomLine = haveBottomLine || index == minMember || index == maxMember;
        }
      }
    }

    g2d.setColor(Color.BLACK);
    drawGroupLine(rc, isRow, g2d, true, haveTopLeft, haveTopRight, haveTopLine);
    drawGroupLine(rc, isRow, g2d, false, haveBottomLeft, haveBottomRight, haveBottomLine);
  }

  private static void drawGroupLine(final Rectangle rc, final boolean isRow, final Graphics2D g2d, boolean isTop,
                                    final boolean haveLeft, final boolean haveRight, final boolean haveLine) {

    Point linePos = isTop ? new Point(rc.x+3, rc.y+3) : new Point((int) rc.getMaxX()-2, (int) rc.getMaxY()-2);
    Point markerPos = isTop ?  new Point((int) rc.getMaxX()-4, (int) rc.getMaxY()-4) : new Point(rc.x+5, rc.y+5);

    int midX = (int) rc.getCenterX();
    int midY = (int) rc.getCenterY();
    int maxX = (int) rc.getMaxX();
    int maxY = (int) rc.getMaxY();
    if (haveLine) {
      if (isRow) {
        g2d.drawLine(linePos.x, midY, markerPos.x, midY);
      }
      else {
        g2d.drawLine(midX, linePos.y, midX, markerPos.y);
      }
    }
    if (haveLeft) {
      if (isRow) {
        g2d.drawLine(linePos.x, rc.y, linePos.x, midY);
      }
      else {
        g2d.drawLine(rc.x, linePos.y, midX, linePos.y);
      }
    }
    if (haveRight) {
      if (isRow) {
        g2d.drawLine(linePos.x, midY, linePos.x, maxY);
      }
      else {
        g2d.drawLine(midX, linePos.y, maxX, linePos.y);
      }
    }
  }

  @Override
  public Property[] getContainerProperties(final Project project) {
    return Property.EMPTY_ARRAY; // TODO
  }

  @Override
  public Property[] getComponentProperties(final Project project, final RadComponent component) {
    return new Property[] {
      HorzAlignProperty.getInstance(project),
      VertAlignProperty.getInstance(project),
      new ComponentInsetsProperty()
    };
  }

  @Override
  public int insertGridCells(final RadContainer grid, final int cellIndex, final boolean isRow, final boolean isBefore, final boolean grow) {
    FormLayout formLayout = (FormLayout) grid.getLayout();
    int index = isBefore ? cellIndex+1 : cellIndex+2;
    if (isRow) {
      insertOrAppendRow(formLayout, index, FormFactory.RELATED_GAP_ROWSPEC);
      if (!isBefore) index++;
      insertOrAppendRow(formLayout, index, grow ? new RowSpec(ENCODED_FORMSPEC_GROW) : FormFactory.DEFAULT_ROWSPEC);
    }
    else {
      insertOrAppendColumn(formLayout, index, FormFactory.RELATED_GAP_COLSPEC);
      if (!isBefore) index++;
      insertOrAppendColumn(formLayout, index, grow ? new ColumnSpec(ENCODED_FORMSPEC_GROW) : FormFactory.DEFAULT_COLSPEC);
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

  private static class ComponentInsetsProperty extends Property<RadComponent, Insets> {
    private InsetsPropertyRenderer myRenderer;
    private IntRegexEditor<Insets> myEditor;
    private Property[] myChildren;

    public ComponentInsetsProperty() {
      super(null, "Insets");
      myChildren=new Property[]{
        new IntFieldProperty(this, "top", 0),
        new IntFieldProperty(this, "left", 0),
        new IntFieldProperty(this, "bottom", 0),
        new IntFieldProperty(this, "right", 0),
      };
    }

    @NotNull @Override
    public Property[] getChildren(final RadComponent component) {
      return myChildren;
    }

    public Insets getValue(final RadComponent component) {
      if (component.getCustomLayoutConstraints() instanceof CellConstraints) {
        final CellConstraints cellConstraints = (CellConstraints)component.getCustomLayoutConstraints();
        return cellConstraints.insets;
      }
      return new Insets(0, 0, 0, 0);
    }

    protected void setValueImpl(final RadComponent component, final Insets value) throws Exception {
      if (component.getCustomLayoutConstraints() instanceof CellConstraints) {
        final CellConstraints cellConstraints = (CellConstraints)component.getCustomLayoutConstraints();
        cellConstraints.insets = value;

        FormLayout layout = (FormLayout) component.getParent().getLayout();
        CellConstraints cc = (CellConstraints)layout.getConstraints(component.getDelegee()).clone();
        cc.insets = value;
        layout.setConstraints(component.getDelegee(), cc);
      }
    }

    @NotNull
    public PropertyRenderer<Insets> getRenderer() {
      if (myRenderer == null) {
        myRenderer = new InsetsPropertyRenderer();
      }
      return myRenderer;
    }

    public PropertyEditor<Insets> getEditor() {
      if (myEditor == null) {
        myEditor = new IntRegexEditor<Insets>(Insets.class, myRenderer, new int[] { 0, 0, 0, 0 });
      }
      return myEditor;
    }
  }
}
