/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.jetbrains.python;

import com.intellij.testFramework.PlatformTestUtil;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.inspections.PyDeprecationInspection;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;

/**
 * @author yole
 */
public class PyDeprecationTest extends PyTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.setCaresAboutInjection(false);
  }

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
    PyFile file = (PyFile)myFixture.getFile();
    assertEquals("commands.getstatus() is deprecated", file.findTopLevelFunction("getstatus").getDeprecationMessage());
    PlatformTestUtil.tryGcSoftlyReachableObjects();
    assertNotParsed(file);
    
    assertEquals("commands.getstatus() is deprecated", file.findTopLevelFunction("getstatus").getDeprecationMessage());
    assertNotParsed(file);
  }

  public void testDeprecatedAsFallback() {
    myFixture.enableInspections(PyDeprecationInspection.class);
    myFixture.configureByFiles("deprecation/deprecatedAsFallback.py", "deprecation/tmp.py");
    myFixture.checkHighlighting(true, false, false);
  }

  public void testDeprecatedFallback2() {
    myFixture.enableInspections(PyDeprecationInspection.class);
    myFixture.configureByFiles("deprecation/deprecatedFallback2.py", "deprecation/tmp.py", "deprecation/deprecatedAsFallback.py");
    myFixture.checkHighlighting(true, false, false);
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

  public void testAbcDeprecatedAbstracts() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON34,
      () -> {
        myFixture.enableInspections(PyDeprecationInspection.class);
        myFixture.configureByFile("deprecation/abcDeprecatedAbstracts.py");
        myFixture.checkHighlighting(true, false, false);
      }
    );
  }

  public void testFileStub() {
    myFixture.configureByFile("deprecation/deprecatedModule.py");
    PyFile file = (PyFile)myFixture.getFile();
    assertEquals("the deprecated module is deprecated; use a non-deprecated module instead", file.getDeprecationMessage());
    PlatformTestUtil.tryGcSoftlyReachableObjects();
    assertNotParsed(file);

    assertEquals("the deprecated module is deprecated; use a non-deprecated module instead", file.getDeprecationMessage());
    assertNotParsed(file);
  }

  // PY-28053
  public void testHashlibMd5() {
    myFixture.enableInspections(PyDeprecationInspection.class);
    myFixture.copyDirectoryToProject("deprecation/hashlibMd5", "");
    myFixture.configureByFile("a.py");
    myFixture.checkHighlighting(true, false, false);
  }
}
