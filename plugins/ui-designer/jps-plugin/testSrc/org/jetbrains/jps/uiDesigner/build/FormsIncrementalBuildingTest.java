package org.jetbrains.jps.uiDesigner.build;

import org.jetbrains.ether.IncrementalTestCase;

/**
 * @author nik
 */
public class FormsIncrementalBuildingTest extends IncrementalTestCase {
  public FormsIncrementalBuildingTest() throws Exception {
    super("uiDesigner");
  }

  public void testSimple() throws Exception {
    doTest().assertSuccessful();
  }
}
