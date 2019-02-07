// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections;

import com.intellij.testFramework.LightProjectDescriptor;
import com.jetbrains.python.fixtures.PyInspectionTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class PyAsyncCallInspectionTest extends PyInspectionTestCase {

  // PY-17292
  public void testAsyncFromAsyncCall() {
    doTest();
  }

  // PY-17292
  public void testCorrectAsyncioCorCall() {
    doMultiFileTest("a.py");
  }

  // PY-17292
  public void testTypesCorFromAsyncCall() {
    doMultiFileTest("a.py");
  }

  // PY-17292
  public void testAsyncioCorFromAsyncioCorCall() {
    doMultiFileTest("a.py");
  }

  // PY-17292
  public void testFutureLikeFromAsyncCall() {
    doTest();
  }

  // PY-17292
  public void testCorrectCalls() {
    doTest();
  }

  // PY-31598
  public void testScheduleFutureBuiltin() {
    doMultiFileTest("a.py");
  }

  // PY-31598
  public void testScheduleTask37() {
    doMultiFileTest37("a.py");
  }

  @Override
  protected void doMultiFileTest(@NotNull String filename) {
    runWithLanguageLevel(LanguageLevel.PYTHON35, () -> super.doMultiFileTest(filename));
  }

  protected void doMultiFileTest37(@NotNull String filename) {
    runWithLanguageLevel(LanguageLevel.PYTHON37, () -> super.doMultiFileTest(filename));
  }

  @Override
  protected void doTest() {
    runWithLanguageLevel(LanguageLevel.PYTHON35, () -> super.doTest());
  }

  @NotNull
  @Override
  protected Class<? extends PyInspection> getInspectionClass() {
    return PyAsyncCallInspection.class;
  }

  @Nullable
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return ourPy3Descriptor;
  }
}
