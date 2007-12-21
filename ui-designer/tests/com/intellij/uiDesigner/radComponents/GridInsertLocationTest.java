package com.intellij.uiDesigner.radComponents;

import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.designSurface.DraggedComponentList;
import com.intellij.uiDesigner.designSurface.GridInsertLocation;
import com.intellij.uiDesigner.designSurface.GridInsertMode;
import junit.framework.TestCase;

import javax.swing.*;

/**
 * @author yole
 */
public class GridInsertLocationTest extends TestCase {
  private RadGridLayoutManager myManager;
  private RadContainer myContainer;
  private RadComponent myDropComponent;

  protected void setUp() throws Exception {
    super.setUp();
    myManager = new RadGridLayoutManager();
    myContainer = new RadContainer(null, "grid");
    myContainer.setLayoutManager(myManager);
    myDropComponent = new RadAtomicComponent(null, JLabel.class, "2");
  }

  public void testInsertColumnAfter() {
    assertEquals(1, myManager.getGridColumnCount(myContainer));

    insertComponent(0, 0, 1, 1);

    GridInsertLocation location = new GridInsertLocation(myContainer, 0, 0, GridInsertMode.ColumnAfter);
    DraggedComponentList dcl = DraggedComponentList.withComponent(myDropComponent);
    assertTrue(location.canDrop(dcl));
    doDrop(location);
    assertEquals(2, myManager.getGridColumnCount(myContainer));
  }

  public void testInsertRowBefore() {
    setGridSize(2, 1);
    insertComponent(0, 0, 1, 1);
    final RadComponent c = insertComponent(1, 0, 1, 1);

    GridInsertLocation location = new GridInsertLocation(myContainer, 1, 0, GridInsertMode.RowBefore);
    DraggedComponentList dcl = DraggedComponentList.withComponent(myDropComponent);
    assertTrue(location.canDrop(dcl));
    doDrop(location);
    assertEquals(2, c.getConstraints().getRow());
  }

  public void testInsertInMiddleOfComponentColumn() {
    myContainer.setLayout(new GridLayoutManager(1, 2));
    insertComponent(0, 0, 1, 2);

    GridInsertLocation location = new GridInsertLocation(myContainer, 0, 0, GridInsertMode.ColumnAfter);
    DraggedComponentList dcl = DraggedComponentList.withComponent(myDropComponent);
    assertFalse(location.canDrop(dcl));
  }

  public void testInsertInMiddleOfComponentRow() {
    setGridSize(2, 1);
    insertComponent(0, 0, 2, 1);

    GridInsertLocation location = new GridInsertLocation(myContainer, 0, 0, GridInsertMode.RowAfter);
    DraggedComponentList dcl = DraggedComponentList.withComponent(myDropComponent);
    assertFalse(location.canDrop(dcl));
  }

  public void testGrowComponent() {
    myContainer.setLayout(new GridLayoutManager(2, 2));

    //  *|.       *** .
    //
    //  ***   ->  *****
    insertComponent(0, 0, 1, 1);
    RadComponent c = insertComponent(1, 0, 1, 2);

    GridInsertLocation location = new GridInsertLocation(myContainer, 0, 0, GridInsertMode.ColumnAfter);
    DraggedComponentList dcl = DraggedComponentList.withComponent(myDropComponent);
    assertTrue(location.canDrop(dcl));
    doDrop(location);
    assertEquals(3, myManager.getGridColumnCount(myContainer));
    assertEquals(3, c.getConstraints().getColSpan());
  }

  public void testInsertInsideBigComponent() {
    setGridSize(2, 2);
    insertComponent(0, 0, 1, 1);
    insertComponent(1, 0, 1, 2);

    setComponentDimensions(myDropComponent, 0, 0, 2, 1);
    GridInsertLocation location = new GridInsertLocation(myContainer, 0, 0, GridInsertMode.ColumnAfter);
    assertFalse(location.canDrop(DraggedComponentList.withComponent(myDropComponent)));
  }

  private RadComponent insertComponent(int row, int column, int rowSpan, int colSpan) {
    final RadAtomicComponent c = new RadAtomicComponent(null, JLabel.class, "1");
    setComponentDimensions(c, row, column, rowSpan, colSpan);
    myContainer.addComponent(c);
    return c;
  }

  private void setGridSize(final int rowCount, final int columnCount) {
    myContainer.setLayout(new GridLayoutManager(rowCount, columnCount));
  }

  private static void setComponentDimensions(final RadComponent c,
                                             final int row,
                                             final int column,
                                             final int rowSpan,
                                             final int colSpan) {
    c.getConstraints().restore(new GridConstraints(row, column, rowSpan, colSpan, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                   GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_GROW,
                                                   null, null, null));
  }

  private void doDrop(final GridInsertLocation location) {
    location.processDrop(null, new RadComponent[] {myDropComponent}, null, DraggedComponentList.withComponent(myDropComponent));
  }
}
