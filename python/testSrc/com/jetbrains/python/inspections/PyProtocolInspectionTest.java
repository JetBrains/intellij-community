// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections;

import com.intellij.testFramework.LightProjectDescriptor;
import com.jetbrains.python.fixtures.PyInspectionTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PyProtocolInspectionTest extends PyInspectionTestCase {

  // PY-26628
  public void testValidProtocolSubclass() {
    doTest();
  }

  // PY-26628
  public void testIncompatibleProtocolSubclass() {
    doTest();
  }

  // PY-26628
  public void testProtocolBases() {
    doTest();
  }

  // PY-26628
  public void testNewTypeBasedOnProtocol() {
    doTest();
  }

  // PY-26628
  public void testInstanceAndClassChecksOnProtocol() {
    doTest();
  }

  // PY-26628
  public void testProtocolExtBases() {
    myFixture.copyFileToProject(getTestCaseDirectory() + "typing_extensions.py", "typing_extensions.py");
    doTest();
  }

  @Override
  protected void doTest() {
    runWithLanguageLevel(LanguageLevel.PYTHON37, () -> super.doTest());
  }

  @Nullable
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return ourPy3Descriptor;
  }

  @NotNull
  @Override
  protected Class<? extends PyInspection> getInspectionClass() {
    return PyProtocolInspection.class;
  }
}
