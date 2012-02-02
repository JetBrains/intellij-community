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

  @Override
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
    DraggedComponentList dcl = DraggedComponentList.withComponents(myDropComponent);
    assertTrue(location.canDrop(dcl));
    doDrop(location);
    assertEquals(2, myManager.getGridColumnCount(myContainer));
  }

  public void testInsertRowBefore() {
    setGridSize(2, 1);
    insertComponent(0, 0, 1, 1);
    final RadComponent c = insertComponent(1, 0, 1, 1);

    GridInsertLocation location = new GridInsertLocation(myContainer, 1, 0, GridInsertMode.RowBefore);
    DraggedComponentList dcl = DraggedComponentList.withComponents(myDropComponent);
    assertTrue(location.canDrop(dcl));
    doDrop(location);
    assertEquals(2, c.getConstraints().getRow());
  }

  public void testInsertInMiddleOfComponentColumn() {
    myContainer.setLayout(new GridLayoutManager(1, 2));
    insertComponent(0, 0, 1, 2);

    GridInsertLocation location = new GridInsertLocation(myContainer, 0, 0, GridInsertMode.ColumnAfter);
    DraggedComponentList dcl = DraggedComponentList.withComponents(myDropComponent);
    assertFalse(location.canDrop(dcl));
  }

  public void testInsertInMiddleOfComponentRow() {
    setGridSize(2, 1);
    insertComponent(0, 0, 2, 1);

    GridInsertLocation location = new GridInsertLocation(myContainer, 0, 0, GridInsertMode.RowAfter);
    DraggedComponentList dcl = DraggedComponentList.withComponents(myDropComponent);
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
    DraggedComponentList dcl = DraggedComponentList.withComponents(myDropComponent);
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
    assertFalse(location.canDrop(DraggedComponentList.withComponents(myDropComponent)));
  }

  public void testInsertGrowMultiple() {
    setGridSize(4, 4);

    // * . . .
    // . . . .
    // . . . .
    // . . . *

    insertComponent(0, 0, 1, 1);
    insertComponent(3, 3, 1, 1);

    // * . . .
    // . . . .
    // * . . .
    // * . . *
    RadComponent c1 = createComponent(0, 0, 2, 1);
    RadComponent c2 = createComponent(0, 1, 1, 1);

    GridInsertLocation location = new GridInsertLocation(myContainer, 1, 0, GridInsertMode.RowAfter);
    DraggedComponentList dcl = DraggedComponentList.withComponents(c1, c2);
    assertTrue(location.canDrop(dcl));
    location.processDrop(null, new RadComponent[] {c1, c2}, null, dcl);
    assertEquals(6, myManager.getGridRowCount(myContainer));
  }

  public void testInsertGrowMultiple1x1() {
    setGridSize(2, 2);

    // * .
    //
    // . *

    insertComponent(0, 0, 1, 1);
    insertComponent(1, 1, 1, 1);

    // * *
    // *
    // * .
    RadComponent c1 = createComponent(0, 0, 2, 1);
    RadComponent c2 = createComponent(0, 1, 1, 1);

    GridInsertLocation location = new GridInsertLocation(myContainer, 1, 0, GridInsertMode.RowAfter);
    DraggedComponentList dcl = DraggedComponentList.withComponents(c1, c2);
    assertTrue(location.canDrop(dcl));
    location.processDrop(null, new RadComponent[] {c1, c2}, null, dcl);
    assertEquals(3, myManager.getGridRowCount(myContainer));
  }

  public void testInsertGrowSingle1x1() {
    setGridSize(2, 2);

    // * .
    //
    // . *

    insertComponent(0, 0, 1, 1);
    insertComponent(1, 1, 1, 1);

    setComponentDimensions(myDropComponent, 0, 0, 2, 2);
    GridInsertLocation location = new GridInsertLocation(myContainer, 1, 0, GridInsertMode.RowAfter);
    DraggedComponentList dcl = DraggedComponentList.withComponents(myDropComponent);
    assertTrue(location.canDrop(dcl));
    doDrop(location);
    assertEquals(3, myManager.getGridRowCount(myContainer));
    final RadComponent addedComponent = myContainer.getComponents()[2];
    assertEquals(1, addedComponent.getConstraints().getRowSpan());
    assertEquals(1, addedComponent.getConstraints().getColSpan());

  }

  public void testInsertGrowSingle() {
    setGridSize(4, 4);

    // * . . .
    // . . . .
    // . . . .
    // . . . *

    insertComponent(0, 0, 1, 1);
    insertComponent(3, 3, 1, 1);

    setComponentDimensions(myDropComponent, 0, 0, 2, 2);
    GridInsertLocation location = new GridInsertLocation(myContainer, 1, 0, GridInsertMode.RowAfter);
    DraggedComponentList dcl = DraggedComponentList.withComponents(myDropComponent);
    assertTrue(location.canDrop(dcl));
    doDrop(location);
    assertEquals(6, myManager.getGridRowCount(myContainer));
    final RadComponent addedComponent = myContainer.getComponents()[2];
    assertEquals(2, addedComponent.getConstraints().getRowSpan());
    assertEquals(2, addedComponent.getConstraints().getColSpan());

  }

  public void testInsertDifferentRows() {
    setGridSize(2, 1);
    insertComponent(0, 0, 1, 1);
    insertComponent(1, 0, 1, 1);

    RadComponent c1 = createComponent(0, 0, 1, 1);
    RadComponent c2 = createComponent(1, 0, 1, 1);

    GridInsertLocation location = new GridInsertLocation(myContainer, 1, 0, GridInsertMode.RowAfter);
    DraggedComponentList dcl = DraggedComponentList.withComponents(c1, c2);
    assertTrue(location.canDrop(dcl));
    location.processDrop(null, new RadComponent[] {c1, c2}, null, dcl);
    assertEquals(4, myManager.getGridRowCount(myContainer));
  }

  private RadComponent insertComponent(int row, int column, int rowSpan, int colSpan) {
    RadComponent c = createComponent(row, column, rowSpan, colSpan);
    myContainer.addComponent(c);
    return c;
  }

  private RadComponent createComponent(final int row, final int column, final int rowSpan, final int colSpan) {
    final RadAtomicComponent c = new RadAtomicComponent(null, JLabel.class, "1");
    setComponentDimensions(c, row, column, rowSpan, colSpan);
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
    location.processDrop(null, new RadComponent[] {myDropComponent}, null, DraggedComponentList.withComponents(myDropComponent));
  }
}
