package org.jetbrains.jps.uiDesigner.build;

import org.jetbrains.ether.IncrementalTestCase;

public class FormsIncrementalBuildingTest extends IncrementalTestCase {
  public FormsIncrementalBuildingTest() {
    super("uiDesigner");
  }

  public void testSimple() {
    doTest().assertSuccessful();
  }
}
