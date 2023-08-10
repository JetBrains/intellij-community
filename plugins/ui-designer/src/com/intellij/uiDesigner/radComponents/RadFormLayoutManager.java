// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.uiDesigner.radComponents;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.uiDesigner.GridChangeUtil;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.UIFormXmlConstants;
import com.intellij.uiDesigner.XmlWriter;
import com.intellij.uiDesigner.actions.*;
import com.intellij.uiDesigner.compiler.FormLayoutUtils;
import com.intellij.uiDesigner.compiler.Utils;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.designSurface.*;
import com.intellij.uiDesigner.lw.FormLayoutSerializer;
import com.intellij.uiDesigner.propertyInspector.Property;
import com.intellij.uiDesigner.propertyInspector.properties.AbstractInsetsProperty;
import com.intellij.uiDesigner.propertyInspector.properties.AlignPropertyProvider;
import com.intellij.uiDesigner.propertyInspector.properties.HorzAlignProperty;
import com.intellij.uiDesigner.propertyInspector.properties.VertAlignProperty;
import com.intellij.util.ui.PlatformColors;
import com.jgoodies.forms.factories.FormFactory;
import com.jgoodies.forms.layout.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class RadFormLayoutManager extends RadAbstractGridLayoutManager implements AlignPropertyProvider {
  private FormLayoutColumnProperties myPropertiesPanel;

  @NonNls private static final String ENCODED_FORMSPEC_GROW = "d:grow";
  private static final Size DEFAULT_NOGROW_SIZE = new BoundedSize(Sizes.DEFAULT, new ConstantSize(4, ConstantSize.PIXEL), null);

  @Override
  @Nullable public String getName() {
    return UIFormXmlConstants.LAYOUT_FORM;
  }

  @Override @Nullable
  public LayoutManager createLayout() {
    return new FormLayout(ENCODED_FORMSPEC_GROW, ENCODED_FORMSPEC_GROW);
  }

  @Override
  protected void changeLayoutFromGrid(final RadContainer container, final List<RadComponent> contents, final List<Boolean> canRowsGrow,
                                      final List<Boolean> canColumnsGrow) {
    int rowCount = canRowsGrow.size();
    int columnCount = canColumnsGrow.size();
    int rowCountWithGaps = (rowCount == 0) ? 0 : rowCount * 2 - 1;
    int columnCountWithGaps = (columnCount == 0) ? 0 : columnCount * 2 - 1;
    RowSpec[] rowSpecs = new RowSpec [rowCountWithGaps];
    ColumnSpec[] colSpecs = new ColumnSpec [columnCountWithGaps];

    for(int i=0; i<rowCount; i++) {
      rowSpecs [i*2] = canRowsGrow.get(i).booleanValue() ? new RowSpec(ENCODED_FORMSPEC_GROW) : new RowSpec(DEFAULT_NOGROW_SIZE);
      if (i*2+1 < rowSpecs.length) {
        rowSpecs [i*2+1] = FormFactory.RELATED_GAP_ROWSPEC;
      }
    }
    for(int i=0; i<columnCount; i++) {
      colSpecs [i*2] = canColumnsGrow.get(i).booleanValue() ? new ColumnSpec(ENCODED_FORMSPEC_GROW) : new ColumnSpec(DEFAULT_NOGROW_SIZE);
      if (i*2+1 < colSpecs.length) {
        colSpecs [i*2+1] = FormFactory.RELATED_GAP_COLSPEC;
      }
    }

    container.setLayoutManager(this, new FormLayout(colSpecs, rowSpecs));
  }

  @Override
  protected void changeLayoutFromIndexed(final RadContainer container, final List<RadComponent> components) {
    int maxSizePolicy = 0;
    for(RadComponent c: components) {
      maxSizePolicy = Math.max(maxSizePolicy, c.getConstraints().getHSizePolicy());
    }
    ColumnSpec[] colSpecs = new ColumnSpec [components.size() * 2 - 1];
    for(int i=0; i<components.size(); i++) {
      colSpecs [i*2] = components.get(i).getConstraints().getHSizePolicy() == maxSizePolicy
                       ? new ColumnSpec(ENCODED_FORMSPEC_GROW)
                       : FormFactory.DEFAULT_COLSPEC;
      if (i*2+1 < colSpecs.length) {
        colSpecs [i*2+1] = FormFactory.RELATED_GAP_COLSPEC;
      }
    }
    container.setLayoutManager(this, new FormLayout(colSpecs, new RowSpec[] { FormFactory.DEFAULT_ROWSPEC } ));
  }

  @Override
  public void writeLayout(final XmlWriter writer, final RadContainer radContainer) {
    FormLayout layout = (FormLayout) radContainer.getLayout();
    for(int i=1; i<=layout.getRowCount(); i++) {
      RowSpec rowSpec = layout.getRowSpec(i);
      writer.startElement(UIFormXmlConstants.ELEMENT_ROWSPEC);
      try {
        writer.addAttribute(UIFormXmlConstants.ATTRIBUTE_VALUE, FormLayoutUtils.getEncodedSpec(rowSpec));
      }
      finally {
        writer.endElement();
      }
    }
    for(int i=1; i<=layout.getColumnCount(); i++) {
      ColumnSpec columnSpec = layout.getColumnSpec(i);
      writer.startElement(UIFormXmlConstants.ELEMENT_COLSPEC);
      try {
        writer.addAttribute(UIFormXmlConstants.ATTRIBUTE_VALUE, FormLayoutUtils.getEncodedSpec(columnSpec));
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
    super.addComponentToContainer(container, component, index);
    final CellConstraints cc = gridToCellConstraints(component);
    if (component.getCustomLayoutConstraints() instanceof CellConstraints customCellConstraints) {
      cc.insets = customCellConstraints.insets;
    }
    component.setCustomLayoutConstraints(cc);
    container.getDelegee().add(component.getDelegee(), cc, index);
  }

  private static CellConstraints gridToCellConstraints(final RadComponent component) {
    GridConstraints gc = component.getConstraints();
    CellConstraints.Alignment hAlign = ((gc.getHSizePolicy() & GridConstraints.SIZEPOLICY_WANT_GROW) != 0)
                                       ? CellConstraints.FILL
                                       : CellConstraints.DEFAULT;
    CellConstraints.Alignment vAlign = ((gc.getVSizePolicy() & GridConstraints.SIZEPOLICY_WANT_GROW) != 0)
                                       ? CellConstraints.FILL
                                       : CellConstraints.DEFAULT;
    if (component.getCustomLayoutConstraints() instanceof CellConstraints cc) {
      hAlign = cc.hAlign;
      vAlign = cc.vAlign;
    }
    return new CellConstraints(gc.getColumn()+1, gc.getRow()+1, gc.getColSpan(), gc.getRowSpan(), hAlign, vAlign);
  }

  @Override
  public void writeChildConstraints(final XmlWriter writer, final RadComponent child) {
    writeGridConstraints(writer, child);
    if (child.getCustomLayoutConstraints() instanceof CellConstraints cc) {
      writer.startElement(UIFormXmlConstants.ELEMENT_FORMS);
      try {
        if (!cc.insets.equals(new Insets(0, 0, 0, 0))) {
          writer.addAttribute(UIFormXmlConstants.ATTRIBUTE_TOP, cc.insets.top);
          writer.addAttribute(UIFormXmlConstants.ATTRIBUTE_LEFT, cc.insets.left);
          writer.addAttribute(UIFormXmlConstants.ATTRIBUTE_BOTTOM, cc.insets.bottom);
          writer.addAttribute(UIFormXmlConstants.ATTRIBUTE_RIGHT, cc.insets.right);
        }
        if (cc.hAlign != CellConstraints.DEFAULT) {
          writer.addAttribute(UIFormXmlConstants.ATTRIBUTE_DEFAULTALIGN_HORZ, false);
        }
        if (cc.vAlign != CellConstraints.DEFAULT) {
          writer.addAttribute(UIFormXmlConstants.ATTRIBUTE_DEFAULTALIGN_VERT, false);
        }
      }
      finally {
        writer.endElement();
      }
    }
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
    int[] result = Arrays.copyOf(origins, origins.length-1);
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

  @NotNull @Override
  public ComponentDropLocation getDropLocation(@NotNull RadContainer container, @Nullable final Point location) {
    FormLayout formLayout = getFormLayout(container);
    if (formLayout.getRowCount() == 0 || formLayout.getColumnCount() == 0) {
      if (location != null) {
        Rectangle rc = new Rectangle(new Point(), container.getDelegee().getSize());
        return new FormFirstComponentInsertLocation(container, location, rc);
      }
    }
    final FormLayout.LayoutInfo layoutInfo = formLayout.getLayoutInfo(container.getDelegee());
    if (location != null && location.x > layoutInfo.getWidth()) {
      int row = findCell(layoutInfo.rowOrigins, location.y);
      if (row == -1) {
        return NoDropLocation.INSTANCE;
      }
        return new GridInsertLocation(container, row, getGridColumnCount(container)-1, GridInsertMode.ColumnAfter);
    }
    if (location != null && location.y > layoutInfo.getHeight()) {
      int column = findCell(layoutInfo.columnOrigins, location.x);
      if (column == -1) {
        return NoDropLocation.INSTANCE;
      }
      return new GridInsertLocation(container, getGridRowCount(container)-1, column, GridInsertMode.RowAfter);
    }

    if (container.getGridRowCount() == 1 && container.getGridColumnCount() == 1 &&
        getComponentAtGrid(container, 0, 0) == null) {
      final Rectangle rc = getGridCellRangeRect(container, 0, 0, 0, 0);
      if (location == null) {
        return new FormFirstComponentInsertLocation(container, rc, 0, 0);
      }
      return new FormFirstComponentInsertLocation(container, location, rc);
    }

    return super.getDropLocation(container, location);
  }

  @Override
  public CustomPropertiesPanel getRowColumnPropertiesPanel(RadContainer container, boolean isRow, int[] selectedIndices) {
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
    group.add(new SplitAction());
    group.add(new DeleteAction());
    group.add(new GroupRowsColumnsAction());
    group.add(new UngroupRowsColumnsAction());
    return group;
  }

  @Override
  public boolean canCellGrow(RadContainer container, boolean isRow, int index) {
    FormLayout layout = (FormLayout) container.getLayout();
    FormSpec spec = isRow ? layout.getRowSpec(index+1) : layout.getColumnSpec(index+1);
    return spec.getResizeWeight() > 0.01d;
  }

  @Override
  public void setChildDragging(RadComponent child, boolean dragging) {
    // do nothing here - otherwise the layout will jump around
  }

  @Override
  public void paintCaptionDecoration(final RadContainer container, final boolean isRow, final int index, final Graphics2D g2d,
                                     final Rectangle rc) {
    // don't paint gap rows/columns with red background
    if (isGapCell(container, isRow, index)) {
      g2d.setColor(Color.LIGHT_GRAY);
      g2d.fillRect(rc.x, rc.y, rc.width, rc.height);
    }

    if (canCellGrow(container, isRow, index)) {
      drawGrowMarker(isRow, g2d, rc);
    }

    FormLayout layout = (FormLayout) container.getLayout();
    int[][] groups = isRow ? layout.getRowGroups() : layout.getColumnGroups();
    //noinspection MultipleVariablesInDeclaration
    boolean haveTopLeft = false, haveTopRight = false, haveTopLine = false;
    //noinspection MultipleVariablesInDeclaration
    boolean haveBottomLeft = false, haveBottomRight = false, haveBottomLine = false;
    boolean inGroup = false;
    for(int i=0; i<groups.length; i++) {
      int minMember = Integer.MAX_VALUE;
      int maxMember = -1;
      for(int member: groups [i]) {
        minMember = Math.min(member-1, minMember);
        maxMember = Math.max(member-1, maxMember);
        inGroup = inGroup || (member-1 == index);
      }
      if (minMember <= index && index <= maxMember) {
        if (i % 2 == 0) {
          haveTopLeft = haveTopLeft || index > minMember;
          haveTopRight = haveTopRight || index < maxMember;
          haveTopLine = haveTopLine || inGroup;
        }
        else {
          haveBottomLeft = haveBottomLeft || index > minMember;
          haveBottomRight = haveBottomRight || index < maxMember;
          haveBottomLine = haveBottomLine || inGroup;
        }
      }
    }

    g2d.setColor(PlatformColors.BLUE);
    drawGroupLine(rc, isRow, g2d, true, haveTopLeft, haveTopRight, haveTopLine);
    drawGroupLine(rc, isRow, g2d, false, haveBottomLeft, haveBottomRight, haveBottomLine);
  }

  private static void drawGroupLine(final Rectangle rc, final boolean isRow, final Graphics2D g2d, boolean isTop,
                                    final boolean haveLeft, final boolean haveRight, final boolean haveLine) {

    int maxX = (int) rc.getMaxX();
    int maxY = (int) rc.getMaxY();
    Point linePos = isTop ? new Point(rc.x+1, rc.y+1) : new Point(rc.x+3, rc.y+3);
    Point markerPos = new Point(rc.x+6, rc.y+6);

    int midX = (int) rc.getCenterX();
    int midY = (int) rc.getCenterY();
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
  public Property[] getComponentProperties(final Project project, final RadComponent component) {
    return new Property[] {
      HorzAlignProperty.getInstance(project),
      VertAlignProperty.getInstance(project),
      new ComponentInsetsProperty()
    };
  }

  @Override
  public int insertGridCells(final RadContainer grid, final int cellIndex, final boolean isRow, final boolean isBefore, final boolean grow) {
    FormSpec formSpec;
    if (isRow) {
      formSpec = grow ? new RowSpec(ENCODED_FORMSPEC_GROW) : new RowSpec(DEFAULT_NOGROW_SIZE);
    }
    else {
      formSpec = grow ? new ColumnSpec(ENCODED_FORMSPEC_GROW) : new ColumnSpec(DEFAULT_NOGROW_SIZE);
    }
    insertGridCells(grid, cellIndex, isRow, isBefore, formSpec);
    return getGridCellCount(grid, isRow) == 1 ? 1 : 2;
  }

  @Override
  public void copyGridCells(RadContainer source, final RadContainer destination, final boolean isRow, int cellIndex, int cellCount, int targetIndex) {
    FormLayout sourceLayout = getFormLayout(source);
    FormLayout destinationLayout = getFormLayout(destination);
    if (isRow) {
      insertOrAppendRow(destinationLayout, targetIndex+1, FormFactory.RELATED_GAP_ROWSPEC);
    }
    else {
      insertOrAppendColumn(destinationLayout, targetIndex+1, FormFactory.RELATED_GAP_COLSPEC);
    }
    targetIndex++;
    if (targetIndex < cellIndex) cellIndex++;
    copyFormSpecs(sourceLayout, destinationLayout, isRow, cellIndex, cellCount, targetIndex);
    updateGridConstraintsFromCellConstraints(destination);
  }

  private static void copyFormSpecs(final FormLayout sourceLayout,
                                    final FormLayout destinationLayout,
                                    final boolean isRow,
                                    int cellIndex,
                                    int cellCount,
                                    int targetIndex) {
    for(int i=0; i < cellCount; i++) {
      if (isRow) {
        RowSpec rowSpec = sourceLayout.getRowSpec(cellIndex + 1);
        insertOrAppendRow(destinationLayout, targetIndex+1, rowSpec);
      }
      else {
        ColumnSpec colSpec = sourceLayout.getColumnSpec(cellIndex + 1);
        insertOrAppendColumn(destinationLayout, targetIndex+1, colSpec);
      }
      cellIndex += (targetIndex < cellIndex && sourceLayout == destinationLayout) ? 2 : 1;
      targetIndex++;
    }
  }

  @Override
  public void copyGridSection(final RadContainer source, final RadContainer destination, final Rectangle rc) {
    final FormLayout destinationLayout = new FormLayout();
    destination.setLayout(destinationLayout);
    copyFormSpecs(getFormLayout(source), destinationLayout, true, rc.y, rc.height, 0);
    copyFormSpecs(getFormLayout(source), destinationLayout, false, rc.x, rc.width, 0);
  }

  @Override
  public int getGapCellCount() {
    return 1;
  }

  @Override
  public int getGapCellSize(final RadContainer container, boolean isRow) {
    Size size = isRow ? FormFactory.RELATED_GAP_ROWSPEC.getSize() : FormFactory.RELATED_GAP_COLSPEC.getSize();
    if (size instanceof ConstantSize) {
      return ((ConstantSize) size).getPixelSize(container.getDelegee());
    }
    return 0;
  }

  @Override
  public boolean isGapCell(RadContainer grid, boolean isRow, int cellIndex) {
    if (cellIndex < 0 || cellIndex >= getGridCellCount(grid, isRow)) {
      return false;
    }
    if (cellIndex % 2 == 1) {
      final GridChangeUtil.CellStatus status = GridChangeUtil.canDeleteCell(grid, cellIndex, isRow);
      if (status == GridChangeUtil.CellStatus.Empty || status == GridChangeUtil.CellStatus.Redundant) {
        return true;
      }
    }
    return false;
  }

  @Override
  public int getCellIndexBase() {
    return 1;
  }

  /**
   * @return index where new column or row was actually inserted (0-based)
   */
  private int insertGridCells(RadContainer grid, int cellIndex, boolean isRow, boolean isBefore, FormSpec formSpec) {
    FormLayout formLayout = (FormLayout) grid.getLayout();
    int index = isBefore ? cellIndex+1 : cellIndex+2;
    if (isRow) {
      if (getGridCellCount(grid, true) > 0) {
        insertOrAppendRow(formLayout, index, FormFactory.RELATED_GAP_ROWSPEC);
        if (!isBefore) index++;
      }
      insertOrAppendRow(formLayout, index, (RowSpec) formSpec);
    }
    else {
      if (getGridCellCount(grid, false) > 0) {
        insertOrAppendColumn(formLayout, index, FormFactory.RELATED_GAP_COLSPEC);
        if (!isBefore) index++;
      }
      insertOrAppendColumn(formLayout, index, (ColumnSpec)formSpec);
    }
    updateGridConstraintsFromCellConstraints(grid);
    return index-1;
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
  public int deleteGridCells(final RadContainer grid, final int cellIndex, final boolean isRow) {
    int result = 1;
    FormLayout formLayout = (FormLayout) grid.getLayout();
    adjustDeletedCellOrigins(grid, cellIndex, isRow);
    if (isRow) {
      int[][] groupIndices = formLayout.getRowGroups();
      groupIndices = removeDeletedCell(groupIndices, cellIndex+1);
      formLayout.setRowGroups(groupIndices);
      formLayout.removeRow(cellIndex+1);
      updateGridConstraintsFromCellConstraints(grid);
      if (formLayout.getRowCount() > 0 && formLayout.getRowCount() % 2 == 0) {
        int gapRowIndex = (cellIndex >= grid.getGridRowCount()) ? cellIndex-1 : cellIndex;
        if (GridChangeUtil.isRowEmpty(grid, gapRowIndex)) {
          formLayout.removeRow(gapRowIndex+1);
          updateGridConstraintsFromCellConstraints(grid);
          result++;
        }
      }
    }
    else {
      int[][] groupIndices = formLayout.getColumnGroups();
      groupIndices = removeDeletedCell(groupIndices, cellIndex+1);
      formLayout.setColumnGroups(groupIndices);
      formLayout.removeColumn(cellIndex+1);
      updateGridConstraintsFromCellConstraints(grid);
      if (formLayout.getColumnCount() > 0 && formLayout.getColumnCount() % 2 == 0) {
        int gapColumnIndex = (cellIndex >= grid.getGridColumnCount()) ? cellIndex-1 : cellIndex;
        if (GridChangeUtil.isColumnEmpty(grid, gapColumnIndex)) {
          formLayout.removeColumn(gapColumnIndex+1);
          updateGridConstraintsFromCellConstraints(grid);
          result++;
        }
      }
    }
    return result;
  }

  private void adjustDeletedCellOrigins(final RadContainer grid, final int cellIndex, final boolean isRow) {
    int gapCellDelta = isGapCell(grid, isRow, cellIndex+1) ? 2 : 1;
    for(RadComponent component: grid.getComponents()) {
      // ensure that we don't have component origins in the deleted cells
      final GridConstraints gc = component.getConstraints();
      if (gc.getCell(isRow) == cellIndex) {
        final int span = gc.getSpan(isRow);
        if (span > gapCellDelta) {
          gc.setCell(isRow, cellIndex+gapCellDelta);
          gc.setSpan(isRow, span -gapCellDelta);
          updateConstraints(component);
        }
        else {
          throw new IllegalArgumentException("Attempt to delete grid row/column which contains origins of 1-span components");
        }
      }
    }
  }

  private static int[][] removeDeletedCell(final int[][] groupIndices, final int deletedIndex) {
    for(int i=0; i<groupIndices.length; i++) {
      for(int j=0; j<groupIndices [i].length; j++) {
        if (groupIndices [i][j] == deletedIndex) {
          int[][] newIndices;
          if (groupIndices [i].length <= 2) {
            // deleted cell is contained in a group with 1 or 2 cells => delete entire group
            newIndices = new int[groupIndices.length-1][];
            for (int newI = 0; newI < i; newI++) {
              newIndices [newI] = groupIndices [newI].clone();
            }
            for(int newI=i+1; newI<groupIndices.length; newI++) {
              newIndices [newI-1] = groupIndices [newI].clone();
            }
          }
          else {
            // deleted cell is contained in a group with more than 2 cells => keep the group and delete only the item
            newIndices = new int[groupIndices.length][];
            for(int newI=0; newI<groupIndices.length; newI++) {
              if (newI == i) {
                newIndices [newI] = new int[groupIndices [newI].length-1];
                System.arraycopy(groupIndices [newI], 0, newIndices [newI], 0, j);
                System.arraycopy(groupIndices [newI], j+1, newIndices [newI], j, groupIndices [i].length-j-1);
              }
              else {
                newIndices [newI] = new int[groupIndices [newI].length];
                System.arraycopy(groupIndices [newI], 0, newIndices [newI], 0, groupIndices [i].length);
              }
            }
          }
          return newIndices;
        }
      }
    }
    return groupIndices;
  }

  @Override @Nullable
  public String getCellResizeTooltip(RadContainer container, boolean isRow, int cell, int newSize) {
    final String size = getUpdatedSize(container, isRow, cell, newSize).toString();
    return isRow
           ? UIDesignerBundle.message("tooltip.resize.row", cell+getCellIndexBase(), size)
           : UIDesignerBundle.message("tooltip.resize.column", cell+getCellIndexBase(), size);
  }

  @Override
  public void processCellResized(RadContainer container, final boolean isRow, final int cell, final int newSize) {
    FormLayout formLayout = (FormLayout) container.getLayout();
    final ConstantSize updatedSize = getUpdatedSize(container, isRow, cell, newSize);
    FormSpec newSpec;
    if (isRow) {
      RowSpec rowSpec = formLayout.getRowSpec(cell+1);
      newSpec = new RowSpec(rowSpec.getDefaultAlignment(), updatedSize, rowSpec.getResizeWeight());
    }
    else {
      ColumnSpec colSpec = formLayout.getColumnSpec(cell+1);
      newSpec = new ColumnSpec(colSpec.getDefaultAlignment(), updatedSize, colSpec.getResizeWeight());
    }
    setSpec(formLayout, newSpec, cell+1, isRow);
    resizeSameGroupCells(cell, formLayout, newSpec, isRow);
  }

  // Explicitly resize all cells in the group to desired size to make sure that the resize operation is effective (IDEADEV-10202)
  private static void resizeSameGroupCells(final int cell, final FormLayout formLayout, final FormSpec newSpec, final boolean isRow) {
    int[][] groups = isRow ? formLayout.getRowGroups() : formLayout.getColumnGroups();
    for(int[] group: groups) {
      boolean foundGroup = false;
      for(int groupCell: group) {
        if (groupCell == cell+1) {
          foundGroup = true;
          break;
        }
      }
      if (foundGroup) {
        for(int groupCell: group) {
          setSpec(formLayout, newSpec, groupCell, isRow);
        }
        break;
      }
    }
  }

  private static void setSpec(final FormLayout formLayout, final FormSpec newSpec, final int cell, boolean isRow) {
    if (isRow) {
      formLayout.setRowSpec(cell, (RowSpec) newSpec);
    }
    else {
      formLayout.setColumnSpec(cell, (ColumnSpec) newSpec);
    }
  }

  private static ConstantSize getUpdatedSize(RadContainer container, boolean isRow, int cell, int newPx) {
    FormLayout formLayout = (FormLayout) container.getLayout();
    if (isRow) {
      return scaleSize(formLayout.getRowSpec(cell+1), container, newPx);
    }
    else {
      return scaleSize(formLayout.getColumnSpec(cell+1), container, newPx);
    }
  }

  private static ConstantSize scaleSize(final FormSpec rowSpec, final RadContainer container, final int newPx) {
    if (rowSpec.getSize() instanceof ConstantSize oldSize) {
      int oldPx = oldSize.getPixelSize(container.getDelegee());
      double newValue = Math.round(oldSize.getValue() * newPx / oldPx * 10) / 10;
      return new ConstantSize(newValue, oldSize.getUnit());
    }
    return new ConstantSize(newPx, ConstantSize.PIXEL);
  }

  @Override
  public void processCellsMoved(final RadContainer container, final boolean isRow, final int[] cellsToMove, int targetCell) {
    for(int i=0; i<cellsToMove.length; i++) {
      final int sourceCell = cellsToMove[i];
      moveCells(container, isRow, sourceCell, targetCell);
      if (sourceCell < targetCell) {
        for(int j=i+1; j<cellsToMove.length; j++) {
          cellsToMove [j] -= 2;
        }
      }
      else {
        targetCell += 2;
      }
    }
  }

  private void moveCells(final RadContainer container, final boolean isRow, final int cell, int targetCell) {
    if (targetCell >= cell && targetCell <= cell+2) {
      return;
    }
    FormLayout layout = (FormLayout) container.getLayout();
    List<RadComponent> componentsToMove = new ArrayList<>();
    FormSpec oldSpec = isRow ? layout.getRowSpec(cell+1) : layout.getColumnSpec(cell+1);
    for(RadComponent c: container.getComponents()) {
      if (c.getConstraints().getCell(isRow) == cell) {
        componentsToMove.add(c);
        container.removeComponent(c);
      }
    }
    int count = deleteGridCells(container, cell, isRow);
    int insertCell = (targetCell > cell) ? targetCell - count - 1 : targetCell;
    final boolean isBefore = targetCell < cell;
    int newIndex = insertGridCells(container, insertCell, isRow, isBefore, oldSpec);
    for(RadComponent c: componentsToMove) {
      c.getConstraints().setCell(isRow, newIndex);
      container.addComponent(c);
    }
  }

  private static void updateGridConstraintsFromCellConstraints(RadContainer grid) {
    FormLayout layout = (FormLayout) grid.getLayout();
    for(RadComponent c: grid.getComponents()) {
      CellConstraints cc = layout.getConstraints(c.getDelegee());
      GridConstraints gc = c.getConstraints();
      copyCellToGridConstraints(gc, cc);
    }
  }

  private static void copyCellToGridConstraints(final GridConstraints gc, final CellConstraints cc) {
    gc.setColumn(cc.gridX-1);
    gc.setRow(cc.gridY-1);
    gc.setColSpan(cc.gridWidth);
    gc.setRowSpan(cc.gridHeight);
  }

  @Override
  public int getAlignment(RadComponent component, boolean horizontal) {
    CellConstraints cc = (CellConstraints) component.getCustomLayoutConstraints();
    CellConstraints.Alignment al = horizontal ? cc.hAlign : cc.vAlign;
    if (al == CellConstraints.DEFAULT) {
      FormLayout formLayout = (FormLayout) component.getParent().getLayout();
      FormSpec formSpec = horizontal
                          ? formLayout.getColumnSpec(component.getConstraints().getColumn()+1)
                          : formLayout.getRowSpec(component.getConstraints().getRow()+1);
      final FormSpec.DefaultAlignment defaultAlignment = formSpec.getDefaultAlignment();
      if (defaultAlignment.equals(RowSpec.FILL)) {
        return GridConstraints.ALIGN_FILL;
      }
      if (defaultAlignment.equals(RowSpec.TOP) || defaultAlignment.equals(ColumnSpec.LEFT)) {
        return GridConstraints.ALIGN_LEFT;
      }
      if (defaultAlignment.equals(RowSpec.CENTER)) {
        return GridConstraints.ALIGN_CENTER;
      }
      return GridConstraints.ALIGN_RIGHT;
    }
    return Utils.alignFromConstraints(component.getConstraints(), horizontal);
  }

  @Override
  public void setAlignment(RadComponent component, boolean horizontal, int alignment) {
    CellConstraints cc = (CellConstraints) component.getCustomLayoutConstraints();
    if (horizontal) {
      cc.hAlign = FormLayoutSerializer.ourHorizontalAlignments [alignment];
    }
    else {
      cc.vAlign = FormLayoutSerializer.ourVerticalAlignments [alignment];
    }
    updateConstraints(component);
  }

  @Override
  public void resetAlignment(RadComponent component, boolean horizontal) {
    CellConstraints cc = (CellConstraints) component.getCustomLayoutConstraints();
    if (horizontal) {
      cc.hAlign = CellConstraints.DEFAULT;
    }
    else {
      cc.vAlign = CellConstraints.DEFAULT;
    }
    updateConstraints(component);
  }

  @Override
  public boolean isAlignmentModified(RadComponent component, boolean horizontal) {
    CellConstraints cc = (CellConstraints) component.getCustomLayoutConstraints();
    CellConstraints.Alignment al = horizontal ? cc.hAlign : cc.vAlign;
    return al != CellConstraints.DEFAULT;
  }

  @Override
  protected void updateConstraints(final RadComponent component) {
    FormLayout layout = (FormLayout) component.getParent().getLayout();
    layout.setConstraints(component.getDelegee(), gridToCellConstraints(component));
    super.updateConstraints(component);
  }

  @Override
  public int getMinCellCount() {
    return 0;
  }

  private static Object createSerializedCopy(final Object original) {
    // FormLayout may have been loaded with a different classloader, so we need to create a copy through serialization
    Object copy;
    try {
      BufferExposingByteArrayOutputStream baos = new BufferExposingByteArrayOutputStream();
      try (ObjectOutputStream os = new ObjectOutputStream(baos)) {
        os.writeObject(original);
      }

      try (ObjectInputStream is = new ObjectInputStream(baos.toInputStream())) {
        copy = is.readObject();
      }
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
    return copy;
  }

  private static class ComponentInsetsProperty extends AbstractInsetsProperty<RadComponent> {
    ComponentInsetsProperty() {
      super(null, "Insets");
    }

    @Override
    public Insets getValue(final RadComponent component) {
      if (component.getCustomLayoutConstraints() instanceof CellConstraints cellConstraints) {
        return cellConstraints.insets;
      }
      return new Insets(0, 0, 0, 0);
    }

    @Override
    protected void setValueImpl(final RadComponent component, final Insets value) throws Exception {
      if (component.getCustomLayoutConstraints() instanceof CellConstraints cellConstraints) {
        cellConstraints.insets = value;

        FormLayout layout = (FormLayout) component.getParent().getLayout();
        CellConstraints cc = (CellConstraints)layout.getConstraints(component.getDelegee()).clone();
        cc.insets = value;
        layout.setConstraints(component.getDelegee(), cc);
      }
    }
  }
}
