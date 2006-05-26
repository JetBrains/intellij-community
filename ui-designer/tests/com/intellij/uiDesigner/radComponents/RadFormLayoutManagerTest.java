/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.radComponents;

import junit.framework.TestCase;
import com.jgoodies.forms.layout.FormLayout;
import com.jgoodies.forms.layout.ColumnSpec;
import com.jgoodies.forms.layout.ConstantSize;

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
}
