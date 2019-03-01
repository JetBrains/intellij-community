// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections.flake8;

import com.google.common.collect.Sets;
import com.intellij.codeInspection.InspectionEP;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.LocalInspectionEP;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixture4TestCase;
import org.junit.Before;
import org.junit.ComparisonFailure;
import org.junit.Test;

import java.util.Set;

public class FlakeInspectionSuppressorTest extends LightPlatformCodeInsightFixture4TestCase {
  private Set<String> excluded =
    Sets.newHashSet("PyInterpreterInspection", "PyMandatoryEncodingInspection", "PyMissingOrEmptyDocstringInspection");

  @Before
  public void setup() {
    InspectionProfileEntry[] inspections = LocalInspectionEP.LOCAL_INSPECTION.extensions()
      .map(InspectionEP::instantiateTool)
      .filter(e -> e.getShortName().startsWith("Py"))
      .filter(e -> !excluded.contains(e.getShortName()))
      .toArray(InspectionProfileEntry[]::new);
    myFixture.enableInspections(inspections);
  }

  /**
   * Test that markers are suppressing errors
   */
  @Test
  public void testErrorSuppression() {
    //language=Python
    assertNoErrors("def foo():\n    x = 1 # noqa");

    // testing prefix matching # noqa
    //language=Python
    assertNoErrors("def foo():\n    x = 1 # noqa123   ");

    //language=Python
    assertNoErrors("# flake8: noqa\ndef foo():\n    x = 1");

    // testing prefix matching # flake8: noqa
    //language=Python
    assertNoErrors("# flake8: noqa123   \ndef foo():\n    x = 1");
  }

  /**
   * Tests that we're not suppressing more than we should
   */
  @Test
  public void testNoErrorSuppression() {
    //language=Python
    assertErrors("def foo():\n    x = 1");

    // noq instead of noqa must not suppress inspections
    //language=Python
    assertErrors("def foo():\n    x = 1 # noq");

    //language=Python
    assertErrors("def foo():\n    x = 1");
  }

  private void assertNoErrors(String code) {
    myFixture.configureByText("file.py", code);
    myFixture.checkHighlighting();
  }

  private void assertErrors(String code) {
    try {
      myFixture.configureByText("file.py", code);
      myFixture.checkHighlighting();
    }
    catch (ComparisonFailure ignored) {
      // this is expected because errors must be added by inspections
    }
  }
}