/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.jetbrains.python.inspections;

import com.intellij.testFramework.LightProjectDescriptor;
import com.jetbrains.python.fixtures.PyInspectionTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PyDataclassInspectionTest extends PyInspectionTestCase {

  // PY-27398
  public void testMutatingFrozen() {
    doTest();
  }

  // PY-26354
  public void testMutatingFrozenAttrs() {
    doTest();
  }

  // PY-27398
  public void testOrderAndNotEq() {
    doTest();
  }

  // PY-27398
  public void testDefaultFieldValue() {
    doTest();
  }

  // PY-27398
  public void testFieldsOrder() {
    doTest();
  }

  // PY-26354
  public void testAttrsFieldsOrder() {
    doTest();
  }

  // PY-27398
  public void testComparisonForOrdered() {
    doTest();
  }

  // PY-27398
  public void testComparisonForUnordered() {
    doTest();
  }

  // PY-27398
  public void testComparisonForOrderedAndUnordered() {
    doTest();
  }

  // PY-27398
  public void testHelpersArgument() {
    doTest();
  }

  // PY-27398
  public void testAccessToInitVar() {
    doTest();
  }

  // PY-27398
  public void testUselessInitVar() {
    doTest();
  }

  // PY-27398
  public void testUselessDunderPostInit() {
    doTest();
  }

  // PY-27398
  public void testWrongDunderPostInitSignature() {
    doTest();
  }

  // PY-27398
  public void testFieldDefaultAndDefaultFactory() {
    doTest();
  }

  // PY-27398
  public void testUselessReprEq() {
    doTest();
  }

  // PY-27398
  public void testUselessOrder() {
    doTest();
  }

  // PY-27398
  public void testUselessFrozen() {
    doTest();
  }

  // PY-27398
  public void testUselessUnsafeHash() {
    doTest();
  }

  @Override
  protected void doTest() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON37,
      () -> {
        myFixture.copyFileToProject(getTestCaseDirectory() + "/dataclasses.py", "dataclasses.py");
        super.doTest();
        assertProjectFilesNotParsed(myFixture.getFile());
      }
    );
  }

  @Nullable
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return ourPy3Descriptor;
  }

  @NotNull
  @Override
  protected Class<? extends PyInspection> getInspectionClass() {
    return PyDataclassInspection.class;
  }
}
