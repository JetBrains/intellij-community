// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python;

import com.jetbrains.python.allure.Layers;
import com.jetbrains.python.allure.Subsystems;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.idea.TestFor;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.inspections.PyInspection;
import com.jetbrains.python.inspections.unresolvedReference.PyUnresolvedReferencesInspection;
import com.jetbrains.python.inspections.unusedLocal.PyUnusedLocalVariableInspection;

import java.util.List;

@Subsystems.Inspections
@Layers.Functional
public class PySuppressInspectionsTest extends PyTestCase {
  public void testSuppressedForStatement() {
    doTestHighlighting(PyUnresolvedReferencesInspection.class);
  }

  public void testSuppressedForMethod() {
    doTestHighlighting(PyUnresolvedReferencesInspection.class);
  }

  public void testSuppressedForClass() {
    doTestHighlighting(PyUnresolvedReferencesInspection.class);
  }

  public void testSuppressedUnusedLocal() {
    doTestHighlighting(PyUnusedLocalVariableInspection.class);
  }

  public void testSuppressForImport() {  // PY-2240
    doTestHighlighting(PyUnresolvedReferencesInspection.class);
  }
  
  public void testSuppressInsideInjection() {
    doTestHighlighting(PyUnresolvedReferencesInspection.class);
  }

  public void testSuppressOutsideInjection() {
    doTestHighlighting(PyUnresolvedReferencesInspection.class);
  }
  
  private void doTestHighlighting(final Class<? extends PyInspection> inspectionClass) {
    myFixture.configureByFile("inspections/suppress/" + getTestName(true) + ".py");
    myFixture.enableInspections(inspectionClass);
    myFixture.checkHighlighting(true, false, true);
  }

  public void testSuppressForStatement() {
    myFixture.configureByFile("inspections/suppress/suppressForStatement.py");
    myFixture.enableInspections(PyUnresolvedReferencesInspection.class);
    final List<IntentionAction> intentions = myFixture.filterAvailableIntentions("Suppress for a statement");
    // Rename reference, Ignore unresolved references, Ignore all unresolved attributes of
    assertEquals(3, intentions.size());
    final IntentionAction suppressAction = intentions.get(0);
    myFixture.launchAction(suppressAction);
    myFixture.checkResultByFile("inspections/suppress/suppressForStatement.after.py");
  }

  // the kebab-case alias (`PyUnresolvedReferences` -> `unresolved-references`) suppresses too.
  @TestFor(issues = "PY-90265")
  public void testSuppressedByKebabAlias() {
    myFixture.configureByText("a.py", "# noinspection unresolved-references\nprint(xxx)");
    myFixture.enableInspections(PyUnresolvedReferencesInspection.class);
    myFixture.checkHighlighting(true, false, true);
  }
}
