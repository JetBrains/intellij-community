/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.codeInsight.intention.IntentionAction;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.inspections.PyInspection;
import com.jetbrains.python.inspections.PyUnusedLocalInspection;
import com.jetbrains.python.inspections.unresolvedReference.PyUnresolvedReferencesInspection;

import java.util.List;

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
    doTestHighlighting(PyUnusedLocalInspection.class);
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
    final List<IntentionAction> intentions = myFixture.filterAvailableIntentions("Suppress for statement");
    assertEquals(3, intentions.size());  // Rename reference, Ignore unresolved reference, Mark all unresolved attributes
    final IntentionAction suppressAction = intentions.get(0);
    myFixture.launchAction(suppressAction);
    myFixture.checkResultByFile("inspections/suppress/suppressForStatement.after.py");
  }
}
