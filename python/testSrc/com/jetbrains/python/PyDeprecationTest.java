package com.jetbrains.python;

import com.jetbrains.python.fixtures.PyLightFixtureTestCase;
import com.jetbrains.python.inspections.PyDeprecationInspection;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;

/**
 * @author yole
 */
public class PyDeprecationTest extends PyLightFixtureTestCase {
  public void testFunction() {
    myFixture.configureByText(PythonFileType.INSTANCE,
                              "def getstatus(file):\n" +
                              "    \"\"\"Return output of \"ls -ld <file>\" in a string.\"\"\"\n" +
                              "    import warnings\n" +
                              "    warnings.warn(\"commands.getstatus() is deprecated\", DeprecationWarning, 2)\n" +
                              "    return getoutput('ls -ld' + mkarg(file))");
    PyFunction getstatus = ((PyFile) myFixture.getFile()).findTopLevelFunction("getstatus");
    assertEquals("commands.getstatus() is deprecated", getstatus.getDeprecationMessage());
  }

  public void testFunctionStub() {
    myFixture.configureByFile("deprecation/functionStub.py");
    PyFunction getstatus = ((PyFile) myFixture.getFile()).findTopLevelFunction("getstatus");
    assertEquals("commands.getstatus() is deprecated", getstatus.getDeprecationMessage());
    assertNotParsed((PyFile) myFixture.getFile());
  }

  public void testDeprecatedProperty() {
    myFixture.enableInspections(PyDeprecationInspection.class);
    myFixture.configureByFile("deprecation/deprecatedProperty.py");
    myFixture.checkHighlighting(true, false, false);
  }

  public void testDeprecatedImport() {
    myFixture.enableInspections(PyDeprecationInspection.class);
    myFixture.configureByFiles("deprecation/deprecatedImport.py", "deprecation/deprecatedModule.py");
    myFixture.checkHighlighting(true, false, false);
  }

  public void testFileStub() {
    myFixture.configureByFile("deprecation/deprecatedModule.py");
    assertEquals("the deprecated module is deprecated; use a non-deprecated module instead", ((PyFile) myFixture.getFile()).getDeprecationMessage());
    assertNotParsed((PyFile) myFixture.getFile());
  }
}
