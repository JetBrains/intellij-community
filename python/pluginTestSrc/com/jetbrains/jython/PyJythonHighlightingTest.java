// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jython;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.jetbrains.python.PythonHelpersLocator;
import com.jetbrains.python.inspections.PyCallingNonCallableInspection;
import com.jetbrains.python.inspections.unresolvedReference.PyUnresolvedReferencesInspection;


public class PyJythonHighlightingTest extends LightJavaCodeInsightFixtureTestCase {
  public void testCallableJavaClass() {
    doCallableTest();
  }

  public void testCallableStaticMethod() {
    doCallableTest();
  }

  private void doCallableTest() {
    myFixture.configureByFile(getTestName(false) + ".py");
    myFixture.enableInspections(PyCallingNonCallableInspection.class, PyUnresolvedReferencesInspection.class);
    myFixture.checkHighlighting(true, false, false);
  }


  @Override
  protected String getTestDataPath() {
    return PythonHelpersLocator.getPythonCommunityPath() + "/testData/highlighting/jython/";
  }
}
