package com.jetbrains.python;

import com.intellij.testFramework.ParsingTestCase;
import com.intellij.testFramework.TestDataPath;
import com.jetbrains.cython.CythonTokenSetContributor;
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
  protected void setUp() throws Exception {
    super.setUp();
    // These extensions are registered in XML for IDE
    registerExtensionPoint(PythonDialectsTokenSetContributor.EP_NAME, PythonDialectsTokenSetContributor.class);
    registerExtension(PythonDialectsTokenSetContributor.EP_NAME, new PythonTokenSetContributor());
    registerExtension(PythonDialectsTokenSetContributor.EP_NAME, new CythonTokenSetContributor());
  }

  @Override
  protected String getTestDataPath() {
    return PythonTestUtil.getTestDataPath();
  }

  public void testHelloWorld() {
    doTest();
  }

  public void testVariables() {
    doTest();
  }

  public void testExpressions() {
    doTest();
  }

  public void testFunctions() {
    doTest();
  }

  public void testMacros() {
    doTest();
  }

  public void testImports() {
    doTest();
  }

  public void testCdefBlocks() {
    doTest();
  }

  public void testStructs() {
    doTest();
  }

  public void testEnums() {
    doTest();
  }

  public void testTypedefs() {
    doTest();
  }

  public void testForLoops() {
    doTest();
  }

  public void testIncludes() {
    doTest();
  }

  public void testClasses() {
    doTest();
  }

  public void doTest() {
    doTest(true);
  }
}
