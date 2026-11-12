// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections;

import com.jetbrains.python.allure.Layers;
import com.jetbrains.python.allure.Subsystems;

import com.intellij.testFramework.LightProjectDescriptor;
import com.jetbrains.python.fixtures.PyInspectionTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Legacy, use a `PyCodeInsightTestCase` suite instead.
 * <p>
 * Purely Python 2 related tests, safe to delete without a second thought if we drop Python 2 support.
 * <p>
 * This class runs at {@link LanguageLevel#PYTHON27} (see {@link #getProjectDescriptor()}). All
 * Python-3-relevant value from these tests is being migrated to the modern inline-assertion type
 * tests under {@code com.jetbrains.python.types} (a {@code PyCodeInsightTestCase} suite); whatever
 * remains here is Python-2-specific (e.g. {@code str}/{@code unicode}, old-style classes,
 * {@code long})
 * <p>
 * NOTE: upon removal, remove the corresponding python fixtures
 */
@Subsystems.Inspections
@Layers.Functional
public class PyTypeCheckerInspectionTest extends PyInspectionTestCase {

  @Override
  protected @Nullable LightProjectDescriptor getProjectDescriptor() {
    return ourPy2Descriptor;
  }

  @NotNull
  @Override
  protected Class<? extends PyInspection> getInspectionClass() {
    return PyTypeCheckerInspection.class;
  }

  @Override
  protected boolean isLowerCaseTestFile() {
    return false;
  }

  public void testNotImportedClassInDocString() {
    doMultiFileTest();
  }

  public void testCallableInstanceAgainstCallable() {
    runWithLanguageLevel(LanguageLevel.PYTHON35, this::doTest);
  }

  // PY-38412
  public void testTypedDictInStub() {
    runWithLanguageLevel(LanguageLevel.getLatest(), this::doMultiFileTest);
  }

}
