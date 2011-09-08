package com.jetbrains.python;

import com.intellij.testFramework.ParsingTestCase;
import com.intellij.testFramework.TestDataPath;
import com.jetbrains.cython.parser.CythonParserDefinition;
import com.jetbrains.python.fixtures.PyLightFixtureTestCase;

/**
 * @author vlan
 */
@TestDataPath("$CONTENT_ROOT/../testData/psi/")
public class CythonParsingTest extends ParsingTestCase {
  public CythonParsingTest() {
    super("cython", "pyx", new CythonParserDefinition(), new PythonParserDefinition());
    PyLightFixtureTestCase.initPlatformPrefix();
  }

  @Override
  protected String getTestDataPath() {
    return PythonTestUtil.getTestDataPath();
  }

  public void testHelloWorld() {
    doTest();
  }

  public void doTest() {
    doTest(true);
  }
}
