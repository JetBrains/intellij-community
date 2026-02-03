/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.testFramework.TestDataFile;
import com.intellij.testFramework.TestDataPath;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.inspections.PyAbstractClassInspection;
import com.jetbrains.python.inspections.PyMissingConstructorInspection;
import com.jetbrains.python.inspections.PyStatementEffectInspection;
import com.jetbrains.python.inspections.unresolvedReference.PyUnresolvedReferencesInspection;
import com.jetbrains.python.psi.LanguageLevel;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@TestDataPath("$CONTENT_ROOT/../testData/inspections/")
public class Py3QuickFixTest extends PyTestCase {

  // PY-13685
  public void testReplacePrintEnd() {
    doInspectionTest(PyStatementEffectInspection.class, PyPsiBundle.message("QFIX.statement.effect"), true, true);
  }

  // PY-13685
  public void testReplacePrintComment() {
    doInspectionTest(PyStatementEffectInspection.class, PyPsiBundle.message("QFIX.statement.effect"), true, true);
  }

  // PY-13685
  public void testReplaceExecComment() {
    doInspectionTest(PyStatementEffectInspection.class, PyPsiBundle.message("QFIX.statement.effect"), true, true);
  }

  // PY-15867
  public void testAddCallSuperKeywordOnlyParamInSuperInit() {
    doInspectionTest(PyMissingConstructorInspection.class, PyPsiBundle.message("QFIX.add.super"), true, true);
  }

  // PY-15867
  public void testAddCallSuperKeywordOnlyParamInInit() {
    doInspectionTest(PyMissingConstructorInspection.class, PyPsiBundle.message("QFIX.add.super"), true, true);
  }

  // PY-15867
  public void testAddCallSuperSingleStarParamInSuperInit() {
    doInspectionTest(PyMissingConstructorInspection.class, PyPsiBundle.message("QFIX.add.super"), true, true);
  }

  // PY-15867
  public void testAddCallSuperSingleStarParamInSuperInitAndVarargInInit() {
    doInspectionTest(PyMissingConstructorInspection.class, PyPsiBundle.message("QFIX.add.super"), true, true);
  }

  // PY-11561
  public void testAddCallSuperTypeAnnotationsPreserved() {
    doInspectionTest(PyMissingConstructorInspection.class, PyPsiBundle.message("QFIX.add.super"), true, true);
  }

  // PY-16036, PY-11561
  public void testAddCallSuperSelfNameAndAnnotationPreserved() {
    doInspectionTest(PyMissingConstructorInspection.class, PyPsiBundle.message("QFIX.add.super"), true, true);
  }

  // PY-15867
  public void testAddCallSuperNoRequiredKeywordOnlyParamAfterSingleStarInSuperInit() {
    doInspectionTest(PyMissingConstructorInspection.class, PyPsiBundle.message("QFIX.add.super"), true, true);
  }

  // PY-16421
  public void testAddCallSuperSingleStarParamPreserved() {
    doInspectionTest(PyMissingConstructorInspection.class, PyPsiBundle.message("QFIX.add.super"), true, true);
  }

  // PY-15867
  public void testAddCallSuperRequiredKeywordOnlyParamAfterSingleStarInSuperInitIsMerged() {
    doInspectionTest(PyMissingConstructorInspection.class, PyPsiBundle.message("QFIX.add.super"), true, true);
  }

  // PY-16428 
  public void testAddParameterNotAvailableInsideAnnotation() {
    doInspectionTest(PyUnresolvedReferencesInspection.class,
                     PyPsiBundle.message("QFIX.unresolved.reference.add.param", "unresolved"), false, false);
  }

  // PY-8991
  public void testRemoveUnsupportedPrefixFromGluedStringNodesWithSlash() {
    runWithLanguageLevel(LanguageLevel.PYTHON34, () -> {
      myFixture.configureByFile(getTestName(false) + ".py");
      myFixture.checkHighlighting(true, false, false);
      final IntentionAction intentionAction = myFixture.findSingleIntention(PyPsiBundle.message("QFIX.remove.string.prefix", "F"));
      assertNotNull(intentionAction);
      myFixture.launchAction(intentionAction);
      myFixture.checkResultByFile(getTestName(false) + "_after.py");
    });
  }

  // PY-8990
  public void testRemoveUnsupportedPrefixFromGluedStringNodesInParenthesis() {
    runWithLanguageLevel(LanguageLevel.PYTHON34, () -> {
      myFixture.configureByFile(getTestName(false) + ".py");
      myFixture.checkHighlighting(true, false, false);
      final IntentionAction intentionAction = myFixture.findSingleIntention(PyPsiBundle.message("QFIX.remove.string.prefix", "F"));
      assertNotNull(intentionAction);
      myFixture.launchAction(intentionAction);
      myFixture.checkResultByFile(getTestName(false) + "_after.py");
    });
  }

  // PY-30789
  public void testAddABCToSuperclasses() {
    final String[] testFiles = {
      "PyAbstractClassInspection/quickFix/AddABCToSuperclasses/main.py",
      "PyAbstractClassInspection/quickFix/AddABCToSuperclasses/main_import.py"
    };

    doInspectionTest(testFiles,
                     PyAbstractClassInspection.class,
                     "Add '" + PyNames.ABC + "' to superclasses",
                     true,
                     true);
  }

  // PY-30789
  public void testAddImportedABCToSuperclasses() {
    doInspectionTest("PyAbstractClassInspection/quickFix/AddImportedABCToSuperclasses/main.py",
                     PyAbstractClassInspection.class,
                     "Add '" + PyNames.ABC + "' to superclasses",
                     true,
                     true);
  }

  // PY-12132
  public void testAddABCToSuperclassesCaretAtAbstractMethod() {
    doInspectionTest("PyAbstractClassInspection/quickFix/AddABCToSuperclassesCaretAtAbstractMethod/main.py",
                     PyAbstractClassInspection.class,
                     "Add '" + PyNames.ABC + "' to superclasses",
                     true,
                     true);
  }

  // PY-30789
  public void testSetABCMetaAsMetaclassPy3() {
    final String[] testFiles = {
      "PyAbstractClassInspection/quickFix/SetABCMetaAsMetaclassPy3/main.py",
      "PyAbstractClassInspection/quickFix/SetABCMetaAsMetaclassPy3/main_import.py"
    };

    doInspectionTest(testFiles,
                     PyAbstractClassInspection.class,
                     "Set '" + PyNames.ABC_META + "' as metaclass",
                     true,
                     true);
  }

  // PY-30789
  public void testSetImportedABCMetaAsMetaclassPy3() {
    doInspectionTest("PyAbstractClassInspection/quickFix/SetImportedABCMetaAsMetaclassPy3/main.py",
                     PyAbstractClassInspection.class,
                     "Set '" + PyNames.ABC_META + "' as metaclass",
                     true,
                     true);
  }

  // PY-12132
  public void testSetABCMetaAsMetaclassPy3CaretAtAbstractMethod() {
    doInspectionTest("PyAbstractClassInspection/quickFix/SetABCMetaAsMetaclassPy3CaretAtAbstractMethod/main.py",
                     PyAbstractClassInspection.class,
                     "Set '" + PyNames.ABC_META + "' as metaclass",
                     true,
                     true);
  }

  @Override
  @NonNls
  protected String getTestDataPath() {
    return PythonTestUtil.getTestDataPath() + "/inspections/";
  }

  private void doInspectionTest(@NotNull Class<? extends LocalInspectionTool> inspectionClass,
                                @NotNull String quickFixName,
                                boolean applyFix,
                                boolean available) {
    doInspectionTest(getTestName(false) + ".py", inspectionClass, quickFixName, applyFix, available);
  }

  protected void doInspectionTest(@TestDataFile @NonNls @NotNull String testFileName,
                                  @NotNull Class<? extends LocalInspectionTool> inspectionClass,
                                  @NonNls @NotNull String quickFixName,
                                  boolean applyFix,
                                  boolean available) {
    doInspectionTest(new String[]{testFileName}, inspectionClass, quickFixName, applyFix, available);
  }

  /**
   * Runs daemon passes and looks for given fix within infos.
   *
   * @param testFiles       names of files to participate; first is used for inspection and then for check by "_after".
   * @param inspectionClass what inspection to run
   * @param quickFixName    how the resulting fix should be named (the human-readable name users see)
   * @param applyFix        true if the fix needs to be applied
   * @param available       true if the fix should be available, false if it should be explicitly not available.
   */
  protected void doInspectionTest(@NonNls String @NotNull [] testFiles,
                                  @NotNull Class<? extends LocalInspectionTool> inspectionClass,
                                  @NonNls @NotNull String quickFixName,
                                  boolean applyFix,
                                  boolean available) {
    myFixture.enableInspections(inspectionClass);
    myFixture.configureByFiles(testFiles);
    myFixture.checkHighlighting(true, false, false);
    final List<IntentionAction> intentionActions = myFixture.filterAvailableIntentions(quickFixName);
    if (available) {
      if (intentionActions.isEmpty()) {
        throw new AssertionError("Quickfix \"" + quickFixName + "\" is not available. All quickfixes:\n"+myFixture.getAvailableIntentions());
      }
      if (intentionActions.size() > 1) {
        throw new AssertionError("There are more than one quickfix with the name \"" + quickFixName + "\". All quickfixes:\n"+myFixture.getAvailableIntentions());
      }
      if (applyFix) {
        myFixture.launchAction(intentionActions.get(0));
        myFixture.checkResultByFile(graftBeforeExt(testFiles[0], "_after"));
      }
    }
    else {
      assertEmpty("Quick fix \"" + quickFixName + "\" should not be available", intentionActions);
    }
  }

  // Turns "name.ext" to "name_insertion.ext"

  @NonNls
  private static String graftBeforeExt(String name, String insertion) {
    int dotpos = name.indexOf('.');
    if (dotpos < 0) dotpos = name.length();
    return name.substring(0, dotpos) + insertion + name.substring(dotpos);
  }
}
