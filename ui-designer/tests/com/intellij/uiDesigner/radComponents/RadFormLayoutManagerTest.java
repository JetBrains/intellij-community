/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.radComponents;

import com.jgoodies.forms.layout.ColumnSpec;
import com.jgoodies.forms.layout.ConstantSize;
import com.jgoodies.forms.layout.FormLayout;
import com.jgoodies.forms.layout.CellConstraints;
import com.intellij.uiDesigner.core.GridConstraints;
import junit.framework.TestCase;

import javax.swing.*;

/**
 * @author yole
 */
public class RadFormLayoutManagerTest extends TestCase {
  private RadFormLayoutManager myManager;
  private RadContainer myContainer;
  private FormLayout myLayout;

  public void setUp() throws Exception {
    super.setUp();
    myManager = new RadFormLayoutManager();
    myContainer = new RadContainer(null, "grid");
    myContainer.setLayoutManager(myManager);
    myLayout = (FormLayout) myContainer.getLayout();
  }

  public void testAddComponent() {
    RadComponent c = newComponent(0, 0, 1, 1);
    myContainer.addComponent(c);
    CellConstraints cc = myLayout.getConstraints(c.getDelegee());
    assertEquals(1, cc.gridX);
    assertEquals(1, cc.gridY);
    assertEquals(1, cc.gridWidth);
    assertEquals(1, cc.gridHeight);
  }

  private RadComponent newComponent(final int row, final int column, final int rowSpan, final int colSpan) {
    RadComponent c = new RadAtomicComponent(null, JLabel.class, "1");
    c.setCustomLayoutConstraints(new CellConstraints(1, 1, CellConstraints.DEFAULT, CellConstraints.DEFAULT));
    c.getConstraints().restore(new GridConstraints(row, column, rowSpan, colSpan, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                   GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_GROW,
                                                   null, null, null));
    return c;
  }

  public void testInsertColumn() {
    myManager.insertGridCells(myContainer, 0, false, false, true);
    assertEquals(3, myManager.getGridColumnCount(myContainer));
  }

  public void testResizeColumn() {
    myManager.processCellResized(myContainer, false, 0, 210);
    final ColumnSpec spec = myLayout.getColumnSpec(1);
    assertTrue(spec.getSize() instanceof ConstantSize);
    ConstantSize cSize = (ConstantSize) spec.getSize();
    assertEquals(210, cSize.getPixelSize(myContainer.getDelegee()));
  }

  public void testMoveColumnRight() {
    myManager.insertGridCells(myContainer, 0, false, false, true);
    final ConstantSize colSize = new ConstantSize(17, ConstantSize.MM);
    myLayout.setColumnSpec(1, new ColumnSpec(colSize));
    RadComponent c = newComponent(0, 0, 1, 1);
    myContainer.addComponent(c);
    myManager.processCellsMoved(myContainer, false, new int[] { 0 }, 3);
    assertEquals(colSize, myLayout.getColumnSpec(3).getSize());
    assertEquals(3, myLayout.getConstraints(c.getDelegee()).gridX);
  }

  public void testMoveColumnLeft() {
    myManager.insertGridCells(myContainer, 0, false, false, true);
    final ConstantSize colSize = new ConstantSize(17, ConstantSize.MM);
    myLayout.setColumnSpec(3, new ColumnSpec(colSize));
    RadComponent c = newComponent(0, 2, 1, 1);
    myContainer.addComponent(c);
    myManager.processCellsMoved(myContainer, false, new int[] { 2 }, 0);
    assertEquals(colSize, myLayout.getColumnSpec(1).getSize());
    assertEquals(1, myLayout.getConstraints(c.getDelegee()).gridX);
  }

  public void testMoveMultipleColumnsRight() {
    myManager.insertGridCells(myContainer, 0, false, false, true);
    myManager.insertGridCells(myContainer, 0, false, false, true);
    final ConstantSize colSize1 = new ConstantSize(17, ConstantSize.MM);
    final ConstantSize colSize2 = new ConstantSize(19, ConstantSize.MM);
    myLayout.setColumnSpec(1, new ColumnSpec(colSize1));
    myLayout.setColumnSpec(3, new ColumnSpec(colSize2));
    RadComponent c1 = newComponent(0, 0, 1, 1);
    myContainer.addComponent(c1);
    RadComponent c2 = newComponent(0, 2, 1, 1);
    myContainer.addComponent(c2);
    myManager.processCellsMoved(myContainer, false, new int[] { 0, 2 }, 5);
    assertEquals(colSize1, myLayout.getColumnSpec(3).getSize());
    assertEquals(colSize2, myLayout.getColumnSpec(5).getSize());
    assertEquals(3, myLayout.getConstraints(c1.getDelegee()).gridX);
    assertEquals(5, myLayout.getConstraints(c2.getDelegee()).gridX);
  }
}
