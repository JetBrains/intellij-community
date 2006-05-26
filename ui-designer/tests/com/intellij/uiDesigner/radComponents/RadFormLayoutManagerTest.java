/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.radComponents;

import junit.framework.TestCase;

/**
 * @author yole
 */
public class RadFormLayoutManagerTest extends TestCase {
  private RadFormLayoutManager myManager;
  private RadContainer myContainer;

  public void setUp() throws Exception {
    super.setUp();
    myManager = new RadFormLayoutManager();
    myContainer = new RadContainer(null, "grid");
    myContainer.setLayoutManager(myManager);
  }

  public void testInsertColumn() {
    myManager.insertGridCells(myContainer, 0, false, false, true);
    assertEquals(3, myManager.getGridColumnCount(myContainer));
  }
}
