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
package com.jetbrains.python.quickFixes;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.testFramework.TestDataPath;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.PyQuickFixTestCase;
import com.jetbrains.python.inspections.PyAttributeOutsideInitInspection;
import com.jetbrains.python.psi.LanguageLevel;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

@TestDataPath("$CONTENT_ROOT/../testData/quickFixes/PyMoveAttributeToInitQuickFixTest")
public class PyMoveAttributeToInitQuickFixTest extends PyQuickFixTestCase {

  public void testMoveToInit() {
    doQuickFixTest(PyAttributeOutsideInitInspection.class, PyPsiBundle.message("QFIX.move.attribute"));
  }

  public void testCreateInit() {
    doQuickFixTest(PyAttributeOutsideInitInspection.class, PyPsiBundle.message("QFIX.move.attribute"));
  }

  public void testAddPass() {
    doQuickFixTest(PyAttributeOutsideInitInspection.class, PyPsiBundle.message("QFIX.move.attribute"));
  }

  public void testRemovePass() {
    doQuickFixTest(PyAttributeOutsideInitInspection.class, PyPsiBundle.message("QFIX.move.attribute"));
  }

  public void testSkipDocstring() {
    doQuickFixTest(PyAttributeOutsideInitInspection.class, PyPsiBundle.message("QFIX.move.attribute"));
  }

  public void testAddSuperCall() {
    runWithLanguageLevel(LanguageLevel.PYTHON27, () -> {
      doQuickFixTest(PyAttributeOutsideInitInspection.class, PyPsiBundle.message("QFIX.move.attribute"));
    });
  }

  public void testAddSuperCallOldStyle() {
    runWithLanguageLevel(LanguageLevel.PYTHON27, () -> {
      doQuickFixTest(PyAttributeOutsideInitInspection.class, PyPsiBundle.message("QFIX.move.attribute"));
    });
  }

  // PY-33055
  public void testAddFieldButNotMoveIfParameterAssigned() {
    doCheckSuggestedQuickFixes(PyAttributeOutsideInitInspection.class,
                               List.of(PyPsiBundle.message("QFIX.add.field.to.class", "y", "Classifier")),
                               List.of(PyPsiBundle.message("QFIX.move.attribute")));
  }

  // PY-33055
  public void testBothAddFieldAndMoveIfCallAssigned() {
    doCheckSuggestedQuickFixes(PyAttributeOutsideInitInspection.class,
                               List.of(PyPsiBundle.message("QFIX.move.attribute"),
                                       PyPsiBundle.message("QFIX.add.field.to.class", "x", "Clazz")),
                               List.of());
  }

  // PY-33055
  public void testAddFieldButNotMoveIfLocalVariableAssigned() {
    doCheckSuggestedQuickFixes(PyAttributeOutsideInitInspection.class,
                               List.of(PyPsiBundle.message("QFIX.add.field.to.class", "x", "Clazz")),
                               List.of(PyPsiBundle.message("QFIX.move.attribute")));
  }

  // PY-33055
  public void testAddFieldButNotMoveIfLocalFunctionAssigned() {
    doCheckSuggestedQuickFixes(PyAttributeOutsideInitInspection.class,
                               List.of(PyPsiBundle.message("QFIX.add.field.to.class", "x", "Clazz")),
                               List.of(PyPsiBundle.message("QFIX.move.attribute")));
  }

  // PY-33055
  public void testBothAddFieldAndMoveIfAssignedValueHasReferenceToSelf() {
    doCheckSuggestedQuickFixes(PyAttributeOutsideInitInspection.class,
                               List.of(PyPsiBundle.message("QFIX.move.attribute"),
                                       PyPsiBundle.message("QFIX.add.field.to.class", "y", "Classifier")),
                               List.of());
  }

  // PY-33055
  public void testBothAddFieldAndMoveIfTopLevelVarAssigned() {
    doCheckSuggestedQuickFixes(PyAttributeOutsideInitInspection.class,
                               List.of(PyPsiBundle.message("QFIX.move.attribute"),
                                       PyPsiBundle.message("QFIX.add.field.to.class", "x", "Clazz")),
                               List.of());
  }

  // PY-33055
  public void testAddFieldButNotMoveIfImportInMethodBody() {
    doCheckSuggestedQuickFixes(PyAttributeOutsideInitInspection.class,
                               List.of(PyPsiBundle.message("QFIX.add.field.to.class", "attr", "C")),
                               List.of(PyPsiBundle.message("QFIX.move.attribute")));
  }

  protected void doCheckSuggestedQuickFixes(@NotNull Class inspectionClass,
                                            @NotNull Collection<String> presentHints,
                                            @NotNull Collection<String> absentHints) {
    final String testFileName = getTestName(true);
    myFixture.enableInspections(inspectionClass);
    myFixture.configureByFile(testFileName + ".py");
    myFixture.checkHighlighting(true, false, false);
    for (String hint: presentHints) {
      myFixture.findSingleIntention(hint);
    }
    for (String hint: absentHints) {
      List<IntentionAction> ints = myFixture.filterAvailableIntentions(hint);
      assertEmpty(ints);
    }
  }

  public void testPropertyNegative() {
    doInspectionTest(PyAttributeOutsideInitInspection.class);
  }

  public void testPy3K() {
    doQuickFixTest(PyAttributeOutsideInitInspection.class, PyPsiBundle.message("QFIX.move.attribute"));
  }

}
