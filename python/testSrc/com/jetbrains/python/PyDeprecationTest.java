// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.util.ref.GCWatcher;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.inspections.PyDeprecationInspection;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;


public class PyDeprecationTest extends PyTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.setCaresAboutInjection(false);
  }

  public void testFunction() {
    myFixture.configureByText(PythonFileType.INSTANCE,
                              """
                                def getstatus(file):
                                    ""\"Return output of "ls -ld <file>" in a string.""\"
                                    import warnings
                                    warnings.warn("commands.getstatus() is deprecated", DeprecationWarning, 2)
                                    return getoutput('ls -ld' + mkarg(file))""");
    PyFunction getstatus = ((PyFile) myFixture.getFile()).findTopLevelFunction("getstatus");
    assertEquals("commands.getstatus() is deprecated", getstatus.getDeprecationMessage());
  }

  public void testFunctionStub() {
    myFixture.configureByFile("deprecation/functionStub.py");
    PyFile file = (PyFile)myFixture.getFile();
    assertEquals("commands.getstatus() is deprecated", file.findTopLevelFunction("getstatus").getDeprecationMessage());
    GCWatcher.tracking(file.findTopLevelFunction("getstatus"), ((PsiFileImpl)file).getTreeElement()).ensureCollected();
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

  public void testFileStub() {
    myFixture.configureByFile("deprecation/deprecatedModule.py");
    PyFile file = (PyFile)myFixture.getFile();
    assertEquals("the deprecated module is deprecated; use a non-deprecated module instead", file.getDeprecationMessage());
    GCWatcher.tracking(((PsiFileImpl)file).getStub(), ((PsiFileImpl)file).getTreeElement()).ensureCollected();

    assertNotParsed(file);

    assertEquals("the deprecated module is deprecated; use a non-deprecated module instead", file.getDeprecationMessage());
    assertNotParsed(file);
  }

  // PY-38101
  public void testDeprecatedElementInPyi() {
    myFixture.enableInspections(PyDeprecationInspection.class);
    myFixture.copyDirectoryToProject("deprecation/deprecatedElementInPyi", "");
    myFixture.configureByFile("a.py");
    myFixture.checkHighlighting(true, false, false);
  }

  public void testAbcDeprecatedAbstracts() {
    myFixture.enableInspections(PyDeprecationInspection.class);
    myFixture.configureByFile("deprecation/abcDeprecatedAbstracts.py");
    myFixture.checkHighlighting(true, false, false);
  }
}
