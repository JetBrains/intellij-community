package com.intellij.uiDesigner.radComponents;

import com.intellij.uiDesigner.designSurface.DraggedComponentList;
import com.intellij.uiDesigner.designSurface.GridInsertLocation;
import com.intellij.uiDesigner.designSurface.GridInsertMode;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.GridConstraints;
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

  public void testSimple() {
    assertEquals(1, myManager.getGridColumnCount(myContainer));

    insertComponent(0, 0, 1, 1);

    GridInsertLocation location = new GridInsertLocation(myContainer, 0, 0, GridInsertMode.ColumnAfter);
    DraggedComponentList dcl = DraggedComponentList.withComponent(myDropComponent);
    assertTrue(location.canDrop(dcl));
    location.processDrop(null, new RadComponent[] {myDropComponent}, null, dcl);
    assertEquals(2, myManager.getGridColumnCount(myContainer));
  }

  public void testInsertInMiddleOfComponentColumn() {
    myContainer.setLayout(new GridLayoutManager(1, 2));
    insertComponent(0, 0, 1, 2);

    GridInsertLocation location = new GridInsertLocation(myContainer, 0, 0, GridInsertMode.ColumnAfter);
    DraggedComponentList dcl = DraggedComponentList.withComponent(myDropComponent);
    assertFalse(location.canDrop(dcl));
  }

  public void testInsertInMiddleOfComponentRow() {
    myContainer.setLayout(new GridLayoutManager(2, 1));
    insertComponent(0, 0, 2, 1);

    GridInsertLocation location = new GridInsertLocation(myContainer, 0, 0, GridInsertMode.RowAfter);
    DraggedComponentList dcl = DraggedComponentList.withComponent(myDropComponent);
    assertFalse(location.canDrop(dcl));
  }

  private void insertComponent(int row, int column, int rowSpan, int colSpan) {
    final RadAtomicComponent c = new RadAtomicComponent(null, JLabel.class, "1");
    c.getConstraints().restore(new GridConstraints(row, column, rowSpan, colSpan, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                   GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_GROW,
                                                   null, null, null));
    myContainer.addComponent(c);
  }
}
