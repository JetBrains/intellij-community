/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.uiDesigner;

import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.radComponents.RadContainer;
import com.intellij.uiDesigner.radComponents.RadHSpacer;
import junit.framework.TestCase;

import java.awt.*;

public final class GridChangeUtilTest extends TestCase {

  public void test_margins_and_gaps() {
    final Insets margin = new Insets(11,12,13,14);

    final int hGap = 15;
    final int vGap = 16;
    final RadContainer grid = createGrid(2, 3, margin, hGap, vGap);

    {
      final LayoutManager oldLayout = grid.getLayout();
      GridChangeUtil.insertRowOrColumn(grid, 1, false, false);
      final GridLayoutManager newLayout = (GridLayoutManager)grid.getLayout();

      assertGridDimensions(grid, 2, 4);
      assertTrue(oldLayout != newLayout);
      assertTrue(margin != newLayout.getMargin());
      assertEquals(margin, newLayout.getMargin());
      assertEquals(hGap, newLayout.getHGap());
      assertEquals(vGap, newLayout.getVGap());
    }

    {
      final LayoutManager oldLayout = grid.getLayout();
      GridChangeUtil.splitRow(grid, 1);
      final GridLayoutManager newLayout = (GridLayoutManager)grid.getLayout();

      assertGridDimensions(grid, 3, 4);
      assertTrue(oldLayout != newLayout);
      assertTrue(margin != newLayout.getMargin());
      assertEquals(margin, newLayout.getMargin());
      assertEquals(hGap, newLayout.getHGap());
      assertEquals(vGap, newLayout.getVGap());
    }
  }

  public void test_invalid_parameters() {
    final RadContainer grid = SampleGrid.create();

    try {
      // should cause exception
      GridChangeUtil.splitRow(grid, -1);
      assertTrue(false);
    }
    catch (IllegalArgumentException ok) {
    }

    try {
      // should cause exception
      GridChangeUtil.splitRow(grid, SampleGrid.ORIGINAL_ROWS);
      assertTrue(false);
    }
    catch (IllegalArgumentException ok) {
    }

    // should be ok
    GridChangeUtil.splitRow(grid, SampleGrid.ORIGINAL_ROWS - 1);
    assertGridDimensions(grid, SampleGrid.ORIGINAL_ROWS + 1, SampleGrid.ORIGINAL_COLUMNS);

    try {
      // should cause exception
      GridChangeUtil.splitColumn(grid, -1);
      assertTrue(false);
    }
    catch (IllegalArgumentException ok) {
    }

    try {
      // should cause exception
      GridChangeUtil.splitColumn(grid, SampleGrid.ORIGINAL_COLUMNS);
      assertTrue(false);
    }
    catch (IllegalArgumentException ok) {
    }

    // should be ok
    GridChangeUtil.splitColumn(grid, SampleGrid.ORIGINAL_COLUMNS - 1);
    assertGridDimensions(grid, SampleGrid.ORIGINAL_ROWS + 1, SampleGrid.ORIGINAL_COLUMNS + 1);

    try {
      // should cause exception
      GridChangeUtil.canDeleteCell(grid, -1, false);
      assertTrue(false);
    }
    catch (IllegalArgumentException ok) {
    }

    try {
      // should cause exception
      GridChangeUtil.canDeleteCell(grid, ((GridLayoutManager)grid.getLayout()).getColumnCount(), false);
      assertTrue(false);
    }
    catch (IllegalArgumentException ok) {
    }
  }

  /**
   * 0 0 0 - -
   * - 1 1 1 -
   * 2 2 2 2 -
   * - 3 - - -
   * - - 4 4 4
   * 5 - - - -
   */
  private static final class SampleGrid {
    public static final int ORIGINAL_ROWS = 6;
    public static final int ORIGINAL_COLUMNS = 5;

    public static final int C0 = 0;
    public static final int C1 = 1;
    public static final int C2 = 0;
    public static final int C3 = 1;
    public static final int C4 = 2;
    public static final int C5 = 0;

    public static final int S0 = 3;
    public static final int S1 = 3;
    public static final int S2 = 4;
    public static final int S3 = 1;
    public static final int S4 = 3;
    public static final int S5 = 1;

    public static RadContainer create() {

      final RadContainer grid = createGrid(ORIGINAL_ROWS, ORIGINAL_COLUMNS);

      addComponent(0, C0, S0, grid);
      addComponent(1, C1, S1, grid);
      addComponent(2, C2, S2, grid);
      addComponent(3, C3, S3, grid);
      addComponent(4, C4, S4, grid);
      addComponent(5, C5, S5, grid);

      return grid;
    }

    private static void addComponent(final int idx, final int cell, final int span, final RadContainer grid) {
      final RadHSpacer component = new RadHSpacer(null, Integer.toString(idx));
      component.getConstraints().restore(new GridConstraints(idx, cell, 1, span, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, 0,0,null,null,null,
                                                             0));
      grid.addComponent(component);
    }
  }

  public void test_split() {
    final RadContainer grid = SampleGrid.create();
    GridChangeUtil.splitColumn(grid, 1);
    assertGridDimensions(grid, SampleGrid.ORIGINAL_ROWS, SampleGrid.ORIGINAL_COLUMNS + 1);

    assertComponentCellAndSpan(grid, 0, 0, 4);
    assertComponentCellAndSpan(grid, 1, 1, 4);
    assertComponentCellAndSpan(grid, 2, 0, 5);
    assertComponentCellAndSpan(grid, 3, 1, 2);
    assertComponentCellAndSpan(grid, 4, 3, 3);
    assertComponentCellAndSpan(grid, 5, 0, 1);
  }

  public void test_insert_1() {
    final RadContainer grid = SampleGrid.create();
    GridChangeUtil.insertRowOrColumn(grid, 1, false, false);
    assertGridDimensions(grid, SampleGrid.ORIGINAL_ROWS, SampleGrid.ORIGINAL_COLUMNS + 1);

    assertComponentCellAndSpan(grid, 0, 0, 4);
    assertComponentCellAndSpan(grid, 1, 1, 4);
    assertComponentCellAndSpan(grid, 2, 0, 5);
    assertComponentCellAndSpan(grid, 3, 1, 1);
    assertComponentCellAndSpan(grid, 4, 3, 3);
    assertComponentCellAndSpan(grid, 5, 0, 1);
  }

  @SuppressWarnings({"PointlessArithmeticExpression"})
  public void test_insert_first() {
    final RadContainer grid = SampleGrid.create();
    GridChangeUtil.insertRowOrColumn(grid, 0, false, true);
    assertGridDimensions(grid, SampleGrid.ORIGINAL_ROWS, SampleGrid.ORIGINAL_COLUMNS + 1);

    // all must be shifted one cell right
    assertComponentCellAndSpan(grid, 0, SampleGrid.C0 + 1, SampleGrid.S0);
    assertComponentCellAndSpan(grid, 1, SampleGrid.C1 + 1, SampleGrid.S1);
    assertComponentCellAndSpan(grid, 2, SampleGrid.C2 + 1, SampleGrid.S2);
    assertComponentCellAndSpan(grid, 3, SampleGrid.C3 + 1, SampleGrid.S3);
    assertComponentCellAndSpan(grid, 4, SampleGrid.C4 + 1, SampleGrid.S4);
    assertComponentCellAndSpan(grid, 5, SampleGrid.C5 + 1, SampleGrid.S5);
  }

  public void test_insert_last() {
    final RadContainer grid = SampleGrid.create();
    GridChangeUtil.insertRowOrColumn(grid, SampleGrid.ORIGINAL_COLUMNS-1, false, false);
    assertGridDimensions(grid, SampleGrid.ORIGINAL_ROWS, SampleGrid.ORIGINAL_COLUMNS + 1);

    // no component should change its cell or span
    assertComponentCellAndSpan(grid, 0, SampleGrid.C0, SampleGrid.S0);
    assertComponentCellAndSpan(grid, 1, SampleGrid.C1, SampleGrid.S1);
    assertComponentCellAndSpan(grid, 2, SampleGrid.C2, SampleGrid.S2);
    assertComponentCellAndSpan(grid, 3, SampleGrid.C3, SampleGrid.S3);
    assertComponentCellAndSpan(grid, 4, SampleGrid.C4, SampleGrid.S4);
    assertComponentCellAndSpan(grid, 5, SampleGrid.C5, SampleGrid.S5);
  }

  public void test_insert_after_and_before() {
    for (int i=0; i < SampleGrid.ORIGINAL_COLUMNS-1; i++){
      final RadContainer afterCurrent = SampleGrid.create();
      GridChangeUtil.insertRowOrColumn(afterCurrent, i, false, false);
      assertGridDimensions(afterCurrent, SampleGrid.ORIGINAL_ROWS, SampleGrid.ORIGINAL_COLUMNS + 1);

      final RadContainer beforeNext = SampleGrid.create();
      GridChangeUtil.insertRowOrColumn(beforeNext, i+1, false, true);
      assertGridDimensions(beforeNext, SampleGrid.ORIGINAL_ROWS, SampleGrid.ORIGINAL_COLUMNS + 1);

      // afterCurrent and beforeNext grids should be same 
      assertGridsEqual(afterCurrent, beforeNext);
    }
  }

  public void test_delete() {
    {
      final RadContainer grid = SampleGrid.create();

      assertEquals(GridChangeUtil.CellStatus.Required, GridChangeUtil.canDeleteCell(grid, 0, false));
      assertEquals(GridChangeUtil.CellStatus.Required, GridChangeUtil.canDeleteCell(grid, 1, false));
      assertEquals(GridChangeUtil.CellStatus.CanShift, GridChangeUtil.canDeleteCell(grid, 2, false));
      assertEquals(GridChangeUtil.CellStatus.Redundant, GridChangeUtil.canDeleteCell(grid, 3, false));
      assertEquals(GridChangeUtil.CellStatus.Redundant, GridChangeUtil.canDeleteCell(grid, 4, false));
    }

    for (int i=0; i < SampleGrid.ORIGINAL_COLUMNS; i++){
      final RadContainer grid = SampleGrid.create();

      if (GridChangeUtil.canDeleteCell(grid, i, false) != GridChangeUtil.CellStatus.Required) {
        GridChangeUtil.deleteCell(grid, i, false);
        assertGridDimensions(grid, SampleGrid.ORIGINAL_ROWS, SampleGrid.ORIGINAL_COLUMNS - 1);
      }
      else {
        // exception should be thrown
        try {
          GridChangeUtil.deleteCell(grid, i, false);
          assertTrue(false);
        }
        catch (IllegalArgumentException ok) {
        }
      }
    }

    // no rows in SampleGrid should be deletable...
    {
      final RadContainer grid = SampleGrid.create();
      for (int i=0; i < SampleGrid.ORIGINAL_ROWS; i++){
        assertEquals(GridChangeUtil.CellStatus.Required, GridChangeUtil.canDeleteCell(grid, i, true));
      }
    }
  }

  public void testMoveRowDown() {
    final RadContainer grid = SampleGrid.create();
    GridChangeUtil.moveCells(grid, true, new int[] { 3 }, 6);
    assertEquals(5, grid.getComponent(3).getConstraints().getRow());
    assertEquals(3, grid.getComponent(4).getConstraints().getRow());
    assertEquals(4, grid.getComponent(5).getConstraints().getRow());
  }

  public void testMoveRowUp() {
    final RadContainer grid = SampleGrid.create();
    GridChangeUtil.moveCells(grid, true, new int[] { 5 }, 3);
    assertEquals(4, grid.getComponent(3).getConstraints().getRow());
    assertEquals(5, grid.getComponent(4).getConstraints().getRow());
    assertEquals(3, grid.getComponent(5).getConstraints().getRow());
  }

  public void testMoveAdjacentRowsDown() {
    final RadContainer grid = SampleGrid.create();
    GridChangeUtil.moveCells(grid, true, new int[] { 3, 4 }, 6);
    assertEquals(4, grid.getComponent(3).getConstraints().getRow());
    assertEquals(5, grid.getComponent(4).getConstraints().getRow());
    assertEquals(3, grid.getComponent(5).getConstraints().getRow());
  }

  public void testMoveAdjacentRowsUp() {
    final RadContainer grid = SampleGrid.create();
    GridChangeUtil.moveCells(grid, true, new int[] { 4, 5 }, 3);
    assertEquals(5, grid.getComponent(3).getConstraints().getRow());
    assertEquals(3, grid.getComponent(4).getConstraints().getRow());
    assertEquals(4, grid.getComponent(5).getConstraints().getRow());
  }

  public void testMoveDisjointRowsDown() {
    final RadContainer grid = SampleGrid.create();
    GridChangeUtil.moveCells(grid, true, new int[] { 0, 2 }, 5);
    assertEquals(3, grid.getComponent(0).getConstraints().getRow());
    assertEquals(4, grid.getComponent(2).getConstraints().getRow());
    assertEquals(1, grid.getComponent(3).getConstraints().getRow());
  }

  public void testMoveDisjointRowsUp() {
    final RadContainer grid = SampleGrid.create();
    GridChangeUtil.moveCells(grid, true, new int[] { 2, 4 }, 0);
    assertEquals(0, grid.getComponent(2).getConstraints().getRow());
    assertEquals(1, grid.getComponent(4).getConstraints().getRow());
    assertEquals(2, grid.getComponent(0).getConstraints().getRow());
    assertEquals(3, grid.getComponent(1).getConstraints().getRow());
  }

  private static void assertGridsEqual(final RadContainer gridA, final RadContainer gridB){
    final int count = gridA.getComponentCount();
    assertEquals(count, gridB.getComponentCount());

    for (int j = 0; j < count; j++) {
      final GridConstraints aConstraints = gridA.getComponent(j).getConstraints();
      final GridConstraints bConstraints = gridB.getComponent(j).getConstraints();

      assertEquals(aConstraints.getColumn(), bConstraints.getColumn());
      assertEquals(aConstraints.getColSpan(), bConstraints.getColSpan());
    }
  }

  private static RadContainer createGrid(final int rowCount, final int columnCount){
    return createGrid(rowCount, columnCount, new Insets(0,0,0,0), 0, 0);
  }

  private static RadContainer createGrid(final int rowCount, final int columnCount, final Insets margin, final int hGap, final int vGap){
    final RadContainer container = new RadContainer(null, "grid");
    container.setLayout(new GridLayoutManager(rowCount,columnCount,margin, hGap, vGap));
    return container;
  }

  private static void assertGridDimensions(final RadContainer grid, final int rowCount, final int columnCount) {
    final GridLayoutManager layout = (GridLayoutManager)grid.getLayout();
    assertEquals(columnCount, layout.getColumnCount());
    assertEquals(rowCount, layout.getRowCount());
  }

  private static void assertComponentCellAndSpan(final RadContainer grid, final int idx, final int cell, final int span){
    final GridConstraints constraints = grid.getComponent(idx).getConstraints();
    assertEquals(cell, constraints.getColumn());
    assertEquals(span, constraints.getColSpan());
  }
}
