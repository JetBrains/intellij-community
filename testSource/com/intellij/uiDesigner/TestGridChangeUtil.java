package com.intellij.uiDesigner;

import com.intellij.openapi.module.Module;
import com.intellij.testFramework.LightIdeaTestCase;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;

import java.awt.*;

public final class TestGridChangeUtil extends LightIdeaTestCase{
  
  public void test_margins_and_gaps() {
    final Insets margin = new Insets(11,12,13,14);
    
    final int hGap = 15;
    final int vGap = 16;
    final RadContainer grid = createGrid(getModule(), 2, 3, margin, hGap, vGap);
    
    {
      final LayoutManager oldLayout = grid.getLayout();
      GridChangeUtil.insertColumnAfter(grid, 1);
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
  
  public void test_invalid_parameters() throws Exception{
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
      GridChangeUtil.canDeleteColumn(grid, -1);
      assertTrue(false);
    }
    catch (IllegalArgumentException ok) {
    }
    
    try {
      // should cause exception
      GridChangeUtil.canDeleteColumn(grid, ((GridLayoutManager)grid.getLayout()).getColumnCount());
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
    
    public static RadContainer create() throws Exception{
      final Module module = getModule();

      final RadContainer grid = createGrid(module, ORIGINAL_ROWS, ORIGINAL_COLUMNS);
      
      addComponent(0, C0, S0, grid, module);
      addComponent(1, C1, S1, grid, module);
      addComponent(2, C2, S2, grid, module);
      addComponent(3, C3, S3, grid, module);
      addComponent(4, C4, S4, grid, module);
      addComponent(5, C5, S5, grid, module);
      
      return grid;
    }
    
    private static void addComponent(final int idx, final int cell, final int span, final RadContainer grid, final Module module) {
      final RadHSpacer component = new RadHSpacer(module, Integer.toString(idx));
      component.getConstraints().restore(new GridConstraints(idx, cell, 1, span, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, 0,0,null,null,null));
      grid.addComponent(component);
    }
  }
  
  public void test_split() throws Exception{
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
  
  public void test_insert_1() throws Exception{
    final RadContainer grid = SampleGrid.create();
    GridChangeUtil.insertColumnAfter(grid, 1);
    assertGridDimensions(grid, SampleGrid.ORIGINAL_ROWS, SampleGrid.ORIGINAL_COLUMNS + 1);
    
    assertComponentCellAndSpan(grid, 0, 0, 4);
    assertComponentCellAndSpan(grid, 1, 1, 4);
    assertComponentCellAndSpan(grid, 2, 0, 5);
    assertComponentCellAndSpan(grid, 3, 1, 1);
    assertComponentCellAndSpan(grid, 4, 3, 3);
    assertComponentCellAndSpan(grid, 5, 0, 1);
  }
  
  public void test_insert_first() throws Exception{
    final RadContainer grid = SampleGrid.create();
    GridChangeUtil.insertColumnBefore(grid, 0);
    assertGridDimensions(grid, SampleGrid.ORIGINAL_ROWS, SampleGrid.ORIGINAL_COLUMNS + 1);
    
    // all must be shifted one cell right
    assertComponentCellAndSpan(grid, 0, SampleGrid.C0 + 1, SampleGrid.S0);
    assertComponentCellAndSpan(grid, 1, SampleGrid.C1 + 1, SampleGrid.S1);
    assertComponentCellAndSpan(grid, 2, SampleGrid.C2 + 1, SampleGrid.S2);
    assertComponentCellAndSpan(grid, 3, SampleGrid.C3 + 1, SampleGrid.S3);
    assertComponentCellAndSpan(grid, 4, SampleGrid.C4 + 1, SampleGrid.S4);
    assertComponentCellAndSpan(grid, 5, SampleGrid.C5 + 1, SampleGrid.S5);
  }
  
  public void test_insert_last() throws Exception{
    final RadContainer grid = SampleGrid.create();
    GridChangeUtil.insertColumnAfter(grid, SampleGrid.ORIGINAL_COLUMNS-1);
    assertGridDimensions(grid, SampleGrid.ORIGINAL_ROWS, SampleGrid.ORIGINAL_COLUMNS + 1);
    
    // no component should change its cell or span
    assertComponentCellAndSpan(grid, 0, SampleGrid.C0, SampleGrid.S0);
    assertComponentCellAndSpan(grid, 1, SampleGrid.C1, SampleGrid.S1);
    assertComponentCellAndSpan(grid, 2, SampleGrid.C2, SampleGrid.S2);
    assertComponentCellAndSpan(grid, 3, SampleGrid.C3, SampleGrid.S3);
    assertComponentCellAndSpan(grid, 4, SampleGrid.C4, SampleGrid.S4);
    assertComponentCellAndSpan(grid, 5, SampleGrid.C5, SampleGrid.S5);
  }
  
  public void test_insert_after_and_before() throws Exception{
    for (int i=0; i < SampleGrid.ORIGINAL_COLUMNS-1; i++){
      final RadContainer afterCurrent = SampleGrid.create();
      GridChangeUtil.insertColumnAfter(afterCurrent, i);
      assertGridDimensions(afterCurrent, SampleGrid.ORIGINAL_ROWS, SampleGrid.ORIGINAL_COLUMNS + 1);
      
      final RadContainer beforeNext = SampleGrid.create();
      GridChangeUtil.insertColumnBefore(beforeNext, i+1);
      assertGridDimensions(beforeNext, SampleGrid.ORIGINAL_ROWS, SampleGrid.ORIGINAL_COLUMNS + 1);
      
      // afterCurrent and beforeNext grids should be same 
      assertGridsEqual(afterCurrent, beforeNext);
    }
  }
  
  public void test_delete() throws Exception{
    {
      final RadContainer grid = SampleGrid.create();
      
      assertFalse(GridChangeUtil.canDeleteColumn(grid, 0));
      assertFalse(GridChangeUtil.canDeleteColumn(grid, 1));
      assertTrue(GridChangeUtil.canDeleteColumn(grid, 2));
      assertTrue(GridChangeUtil.canDeleteColumn(grid, 3));
      assertTrue(GridChangeUtil.canDeleteColumn(grid, 4));
    }
    
    for (int i=0; i < SampleGrid.ORIGINAL_COLUMNS; i++){
      final RadContainer grid = SampleGrid.create();
      
      if (GridChangeUtil.canDeleteColumn(grid, i)) {
        GridChangeUtil.deleteColumn(grid, i);
        assertGridDimensions(grid, SampleGrid.ORIGINAL_ROWS, SampleGrid.ORIGINAL_COLUMNS - 1);
      }
      else {
        // exception should be thrown
        try {
          GridChangeUtil.deleteColumn(grid, i);
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
        assertFalse(GridChangeUtil.canDeleteRow(grid, i));
      }
    }
  }
  
  public void test_delete_2() throws Exception{
    final RadContainer grid = SampleGrid.create();
    GridChangeUtil.deleteColumn(grid, 2);
    assertGridDimensions(grid, SampleGrid.ORIGINAL_ROWS, SampleGrid.ORIGINAL_COLUMNS - 1);
    
    assertComponentCellAndSpan(grid, 0, 0, 2);
    assertComponentCellAndSpan(grid, 1, 1, 2);
    assertComponentCellAndSpan(grid, 2, 0, 3);
    assertComponentCellAndSpan(grid, 3, 1, 1);
    assertComponentCellAndSpan(grid, 4, 2, 2);
    assertComponentCellAndSpan(grid, 5, 0, 1);
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
  
  private static RadContainer createGrid(final Module module, final int rowCount, final int columnCount){
    return createGrid(module, rowCount, columnCount, new Insets(0,0,0,0), 0, 0);
  }
  
  private static RadContainer createGrid(final Module module, final int rowCount, final int columnCount, final Insets margin, final int hGap, final int vGap){
    final RadContainer container = new RadContainer(module, "grid");
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
