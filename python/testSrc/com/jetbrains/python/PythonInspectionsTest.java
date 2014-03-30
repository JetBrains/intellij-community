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

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.jetbrains.python.documentation.DocStringFormat;
import com.jetbrains.python.documentation.PyDocumentationSettings;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.inspections.*;
import com.jetbrains.python.psi.LanguageLevel;

/**
 * @author yole
 */
public class PythonInspectionsTest extends PyTestCase {
  public void testReturnValueFromInit() {
    LocalInspectionTool inspection = new PyReturnFromInitInspection();
    doTest(getTestName(true), inspection);
  }

  private void doTest(String testName, LocalInspectionTool localInspectionTool) {
    myFixture.testInspection("inspections/" + testName, new LocalInspectionToolWrapper(localInspectionTool));
  }

  private void doTestWithPy3k(String testName, LocalInspectionTool localInspectionTool) {
    doTestWithLanguageLevel(testName, localInspectionTool, LanguageLevel.PYTHON30);
  }

  private void doTestWithLanguageLevel(String testName,
                                       LocalInspectionTool localInspectionTool,
                                       LanguageLevel languageLevel) {
    setLanguageLevel(languageLevel);
    try {
      doTest(testName, localInspectionTool);
    }
    finally {
      setLanguageLevel(null);
    }
  }

  public void testPyMethodFirstArgAssignmentInspection() {
    LocalInspectionTool inspection = new PyMethodFirstArgAssignmentInspection();
    doTest(getTestName(false), inspection);
  }

  public void testPyMethodParametersInspection() {
    doHighlightingTest(PyMethodParametersInspection.class);
  }

  public void testPyNestedDecoratorsInspection() {
    LocalInspectionTool inspection = new PyNestedDecoratorsInspection();
    doTest(getTestName(false), inspection);
  }

  public void testPyRedeclarationInspection() {
    doHighlightingTest(PyRedeclarationInspection.class);
  }

  public void testPyStringFormatInspection() {
    LocalInspectionTool inspection = new PyStringFormatInspection();
    doTest(getTestName(false), inspection);
  }

  public void testPyTrailingSemicolonInspection() {
    LocalInspectionTool inspection = new PyTrailingSemicolonInspection();
    doTest(getTestName(false), inspection);
  }

  public void testPyUnusedLocalVariableInspection() {
    PyUnusedLocalInspection inspection = new PyUnusedLocalInspection();
    inspection.ignoreTupleUnpacking = false;
    inspection.ignoreLambdaParameters = false;
    doHighlightingTest(inspection, LanguageLevel.PYTHON27);
  }

  public void testPyUnusedLocalVariableInspection3K() {
    doHighlightingTest(PyUnusedLocalInspection.class, LanguageLevel.PYTHON30);
  }

  public void testPyUnusedVariableTupleUnpacking() {
    doHighlightingTest(PyUnusedLocalInspection.class, LanguageLevel.PYTHON26);
  }

  public void testPyUnusedLocalFunctionInspection() {
    PyUnusedLocalInspection inspection = new PyUnusedLocalInspection();
    doTest(getTestName(false), inspection);
  }

  public void testPyDictCreationInspection() {
    doHighlightingTest(PyDictCreationInspection.class, LanguageLevel.PYTHON26);
  }

  public void testPyTupleAssignmentBalanceInspection() {
    LocalInspectionTool inspection = new PyTupleAssignmentBalanceInspection();
    doTest(getTestName(false), inspection);
  }

  public void testPyTupleAssignmentBalanceInspection2() {
    LocalInspectionTool inspection = new PyTupleAssignmentBalanceInspection();
    doTestWithPy3k(getTestName(false), inspection);
  }

  public void testPyClassicStyleClassInspection() {
    doHighlightingTest(PyClassicStyleClassInspection.class);
  }

  public void testPyExceptClausesOrderInspection() {
    doHighlightingTest(PyExceptClausesOrderInspection.class, LanguageLevel.PYTHON26);
  }

  public void testPyExceptionInheritInspection() {
    doHighlightingTest(PyExceptionInheritInspection.class);
  }

  public void testPyDefaultArgumentInspection() {
    LocalInspectionTool inspection = new PyDefaultArgumentInspection();
    doTest(getTestName(false), inspection);
  }

  public void testPyRaisingNewStyleClassInspection() {
    LocalInspectionTool inspection = new PyRaisingNewStyleClassInspection();
    doTestWithLanguageLevel(getTestName(false), inspection, LanguageLevel.PYTHON24);
  }

  public void testPyDocstringInspection() {
    LocalInspectionTool inspection = new PyDocstringInspection();
    doTest(getTestName(false), inspection);
  }

  public void testPyDocstringParametersInspection() {     //PY-3373
    PyDocumentationSettings documentationSettings = PyDocumentationSettings.getInstance(myFixture.getModule());
    documentationSettings.setFormat(DocStringFormat.EPYTEXT);
    try {
      doHighlightingTest(PyDocstringInspection.class, LanguageLevel.PYTHON33);
    }
    finally {
      documentationSettings.setFormat(DocStringFormat.PLAIN);
    }
  }

  public void testPyStatementEffectInspection() {
    doHighlightingTest(PyStatementEffectInspection.class, LanguageLevel.PYTHON26);
  }

  public void testPySimplifyBooleanCheckInspection() {
    doHighlightingTest(PySimplifyBooleanCheckInspection.class, LanguageLevel.PYTHON26);
  }

  public void testPyFromFutureImportInspection() {
    doHighlightingTest(PyFromFutureImportInspection.class, LanguageLevel.PYTHON26);
  }

  public void testPyFromFutureImportInspectionDocString() {
    myFixture.configureByFile("inspections/PyFromFutureImportInspection/module_docstring.py");
    myFixture.enableInspections(PyFromFutureImportInspection.class);
    myFixture.checkHighlighting(true, false, false);
  }

  public void testPyComparisonWithNoneInspection() {
    LocalInspectionTool inspection = new PyComparisonWithNoneInspection();
    doTest(getTestName(false), inspection);
  }

  public void testPyStringExceptionInspection() {
    LocalInspectionTool inspection = new PyStringExceptionInspection();
    doTest(getTestName(false), inspection);
  }

  public void testPySuperArgumentsInspection() {
    LocalInspectionTool inspection = new PySuperArgumentsInspection();
    doTest(getTestName(false), inspection);
  }

  public void testPyByteLiteralInspection() {
    LocalInspectionTool inspection = new PyByteLiteralInspection();
    doTest(getTestName(false), inspection);
  }

  public void testPyTupleItemAssignmentInspection() {
    LocalInspectionTool inspection = new PyTupleItemAssignmentInspection();
    doTest(getTestName(false), inspection);
  }

  public void testPyInitNewSignatureInspection() {
    LocalInspectionTool inspection = new PyInitNewSignatureInspection();
    doTest(getTestName(false), inspection);
  }

  public void testPyCallByClassInspection() {
    doHighlightingTest(PyCallByClassInspection.class); // ok, we can handle insanely long lines :)
  }

  private void doHighlightingTest(final Class<? extends PyInspection> inspectionClass) {
    myFixture.configureByFile("inspections/" + getTestName(false) + "/test.py");
    myFixture.enableInspections(inspectionClass);
    myFixture.checkHighlighting(true, false, true);
  }

  private void doHighlightingTest(final Class<? extends PyInspection> inspectionClass, final LanguageLevel languageLevel) {
    setLanguageLevel(languageLevel);
    try {
      doHighlightingTest(inspectionClass);
    }
    finally {
      setLanguageLevel(null);
    }
  }

  private void doHighlightingTest(InspectionProfileEntry entry, final LanguageLevel languageLevel) {
    setLanguageLevel(languageLevel);
    try {
      doHighlightingTest(entry);
    }
    finally {
      setLanguageLevel(null);
    }
  }

  private void doHighlightingTest(InspectionProfileEntry entry) {
    myFixture.configureByFile("inspections/" + getTestName(false) + "/test.py");
    myFixture.enableInspections(entry);
    myFixture.checkHighlighting(true, false, true);
  }

  public void testPyPropertyDefinitionInspection25() {
    doTestWithLanguageLevel(getTestName(false), new PyPropertyDefinitionInspection(), LanguageLevel.PYTHON25);
  }

  public void testPyPropertyDefinitionInspection26() {
    doTestWithLanguageLevel(getTestName(false), new PyPropertyDefinitionInspection(), LanguageLevel.PYTHON26);
  }

  public void testInconsistentIndentation() {
    doHighlightingTest(PyInconsistentIndentationInspection.class, LanguageLevel.PYTHON26);
  }

  public void testPyChainedComparisonsInspection() {
    doHighlightingTest(PyChainedComparisonsInspection.class);
  }

  public void testPyBroadExceptionInspection() {
    doHighlightingTest(PyBroadExceptionInspection.class);
  }

  public void testPyDictDuplicateKeysInspection() {
    doHighlightingTest(PyDictDuplicateKeysInspection.class);
  }


  public void testPyTupleAssignmentBalanceInspection3() {
    try {
      setLanguageLevel(LanguageLevel.PYTHON27);
      doHighlightingTest(PyTupleAssignmentBalanceInspection.class);
    } finally {
      setLanguageLevel(null);
    }
  }

  public void testPyListCreationInspection() {         //PY-2823
    doHighlightingTest(PyListCreationInspection.class);
  }

  public void testPyStringFormatInspection1() {    //PY-2836
    doHighlightingTest(PyStringFormatInspection.class);
  }

  public void testPyStringFormatInspectionSlice() {    //PY-6756
    doHighlightingTest(PyStringFormatInspection.class);
  }

  public void testPyUnnecessaryBackslashInspection() {    //PY-2952
    setLanguageLevel(LanguageLevel.PYTHON27);
    doHighlightingTest(PyUnnecessaryBackslashInspection.class);
  }

  public void testPySingleQuotedDocstringInspection() {    //PY-1445
    doHighlightingTest(PySingleQuotedDocstringInspection.class);
  }

  public void testPyArgumentEqualDefaultInspection() {    //PY-3125
    doHighlightingTest(PyArgumentEqualDefaultInspection.class);
  }

  public void testPyNonAsciiCharInspection() {    //PY-5868
    doHighlightingTest(PyNonAsciiCharInspection.class);
  }

  public void testPySetFunctionToLiteralInspection() {    //PY-3120
    setLanguageLevel(LanguageLevel.PYTHON27);
    doHighlightingTest(PySetFunctionToLiteralInspection.class);
  }

  public void testPyDecoratorInspection() {    //PY-3348
    doHighlightingTest(PyDecoratorInspection.class);
  }

  // PY-5807
  public void testPyShadowingBuiltinsInspection() {
    doHighlightingTest(PyShadowingBuiltinsInspection.class);
  }

  public void testPyShadowingNamesInspection() {
    doHighlightingTest(PyShadowingNamesInspection.class);
  }
}
