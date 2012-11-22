package org.jetbrains.jps.uiDesigner.build;

import org.jetbrains.ether.IncrementalTestCase;

/**
 * @author nik
 */
public class UiDesignerIncrementalCompilationTest extends IncrementalTestCase {
  public UiDesignerIncrementalCompilationTest() throws Exception {
    super("uiDesigner");
  }

  public void testSimple() throws Exception {
    doTest().assertSuccessful();
  }
}
