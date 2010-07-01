package com.jetbrains.python;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.jetbrains.python.fixtures.PyLightFixtureTestCase;
import com.jetbrains.python.inspections.*;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.impl.PythonLanguageLevelPusher;

/**
 * @author yole
 */
public class PythonInspectionsTest extends PyLightFixtureTestCase {
  public void testReturnValueFromInit() throws Throwable {
    LocalInspectionTool inspection = new PyReturnFromInitInspection();
    doTest(getTestName(true), inspection);
  }

  private void doTest(String testName, LocalInspectionTool localInspectionTool) throws Exception {
    myFixture.testInspection("inspections/" + testName, new LocalInspectionToolWrapper(localInspectionTool));
  }

  private void doTestWithPy3k(String testName, LocalInspectionTool localInspectionTool) throws Throwable {
    doTestWithLanguageLevel(testName, localInspectionTool, LanguageLevel.PYTHON30);
  }

  private void doTestWithLanguageLevel(String testName,
                                       LocalInspectionTool localInspectionTool,
                                       LanguageLevel languageLevel) throws Throwable {
    PythonLanguageLevelPusher.setForcedLanguageLevel(myFixture.getProject(), languageLevel);
    try {
      doTest(testName, localInspectionTool);
    }
    finally {
      PythonLanguageLevelPusher.setForcedLanguageLevel(myFixture.getProject(), null);
    }
  }

  public void testPyMethodFirstArgAssignmentInspection() throws Throwable {
    LocalInspectionTool inspection = new PyMethodFirstArgAssignmentInspection();
    doTest(getTestName(false), inspection);
  }

  public void testPyUnreachableCodeInspection() throws Throwable {
    LocalInspectionTool inspection = new PyUnreachableCodeInspection();
    doTest(getTestName(false), inspection);
  }

  public void testPyUnresolvedReferencesInspection() throws Throwable {
    LocalInspectionTool inspection = new PyUnresolvedReferencesInspection();
    doTest(getTestName(false), inspection);
  }

  public void testPyArgumentListInspection() throws Throwable {
    LocalInspectionTool inspection = new PyArgumentListInspection();
    doTest(getTestName(false), inspection);
  }

  public void testPyMethodParametersInspection() throws Throwable {
    LocalInspectionTool inspection = new PyMethodParametersInspection();
    doTest(getTestName(false), inspection);
  }

  public void testPyArgumentListInspection3K() throws Throwable {
    LocalInspectionTool inspection = new PyArgumentListInspection();
    doTestWithPy3k(getTestName(false), inspection);
  }

  public void testPyRedeclarationInspection() throws Throwable {
    LocalInspectionTool inspection = new PyRedeclarationInspection();
    doTest(getTestName(false), inspection);
  }

  public void testPyStringFormatInspection() throws Throwable {
    LocalInspectionTool inspection = new PyStringFormatInspection();
    doTest(getTestName(false), inspection);
  }

  public void testPyMethodOverridingInspection() throws Throwable {
    LocalInspectionTool inspection = new PyMethodOverridingInspection();
    doTest(getTestName(false), inspection);
  }

  public void testPyTrailingSemicolonInspection() throws Throwable {
    LocalInspectionTool inspection = new PyTrailingSemicolonInspection();
    doTest(getTestName(false), inspection);
  }

  public void testPyUnusedLocalVariableInspection() throws Throwable {
    LocalInspectionTool inspection = new PyUnusedLocalVariableInspection();
    doTest(getTestName(false), inspection);
  }

  public void testPyDictCreationInspection() throws Throwable {
    LocalInspectionTool inspection = new PyDictCreationInspection();
    doTest(getTestName(false), inspection);
  }

  public void testPyDeprecatedModulesInspection() throws Throwable {
    PythonLanguageLevelPusher.setForcedLanguageLevel(myFixture.getProject(), LanguageLevel.PYTHON25);
    try {
      LocalInspectionTool inspection = new PyDeprecatedModulesInspection();
      doTest(getTestName(false), inspection);
    }
    finally {
      PythonLanguageLevelPusher.setForcedLanguageLevel(myFixture.getProject(), null);
    }
  }

  public void testPyTupleAssignmentBalanceInspection() throws Throwable {
    LocalInspectionTool inspection = new PyTupleAssignmentBalanceInspection();
    doTest(getTestName(false), inspection);
  }

  public void testPyTupleAssignmentBalanceInspection2() throws Throwable {
    LocalInspectionTool inspection = new PyTupleAssignmentBalanceInspection();
    doTestWithPy3k(getTestName(false), inspection);
  }

  public void testPyClassicStyleClassInspection() throws Throwable {
    LocalInspectionTool inspection = new PyClassicStyleClassInspection();
    doTest(getTestName(false), inspection);
  }

  public void testPyExceptClausesOrderInspection() throws Throwable {
    LocalInspectionTool inspection = new PyExceptClausesOrderInspection();
    doTest(getTestName(false), inspection);
  }

  public void testPyExceptionInheritInspection() throws Throwable {
    LocalInspectionTool inspection = new PyExceptionInheritInspection();
    doTest(getTestName(false), inspection);
  }

  public void testPyDefaultArgumentInspection() throws Throwable {
    LocalInspectionTool inspection = new PyDefaultArgumentInspection();
    doTest(getTestName(false), inspection);
  }

  public void testPyRaisingNewStyleClassInspection() throws Throwable {
    LocalInspectionTool inspection = new PyRaisingNewStyleClassInspection();
    doTestWithLanguageLevel(getTestName(false), inspection, LanguageLevel.PYTHON24);
  }
  
  public void testPyUnboundLocalVariableInspection() throws Throwable {
    LocalInspectionTool inspection = new PyUnboundLocalVariableInspection();
    doTest(getTestName(false), inspection);
  }
  
  public void testPyDocstringInspection() throws Throwable {
    LocalInspectionTool inspection = new PyDocstringInspection();
    doTest(getTestName(false), inspection);
  }

  public void testPyStatementEffectInspection() throws Throwable {
    LocalInspectionTool inspection = new PyStatementEffectInspection();
    doTest(getTestName(false), inspection);
  }

  public void testPySimplifyBooleanCheckInspection() throws Throwable {
    LocalInspectionTool inspection = new PySimplifyBooleanCheckInspection();
    doTest(getTestName(false), inspection);
  }

  public void testPyFromFutureImportInspection() throws Throwable {
    myFixture.configureByFile("inspections/" + getTestName(true) + "/test.py");
    myFixture.enableInspections(PyFromFutureImportInspection.class);
    myFixture.checkHighlighting(true, false, false);
  }

  public void testPyFromFutureImportInspectionDocString() throws Throwable {
    myFixture.configureByFile("inspections/PyFromFutureImportInspection/module_docstring.py");
    myFixture.enableInspections(PyFromFutureImportInspection.class);
    myFixture.checkHighlighting(true, false, false);
  }

  public void testPyComparisonWithNoneInspection() throws Throwable {
    LocalInspectionTool inspection = new PyComparisonWithNoneInspection();
    doTest(getTestName(false), inspection);
  }

  public void testPyStringExceptionInspection() throws Throwable {
    LocalInspectionTool inspection = new PyStringExceptionInspection();
    doTest(getTestName(false), inspection);
  }

  public void testPySuperArgumentsInspection() throws Throwable {
    LocalInspectionTool inspection = new PySuperArgumentsInspection();
    doTest(getTestName(false), inspection);
  }

  public void testPyByteLiteralInspection() throws Throwable {
    LocalInspectionTool inspection = new PyByteLiteralInspection();
    doTest(getTestName(false), inspection);
  }

  public void testPyTupleItemAssignmentInspection() throws Throwable {
    LocalInspectionTool inspection = new PyTupleItemAssignmentInspection();
    doTest(getTestName(false), inspection);
  }

  public void testPyInitNewSignatureInspection() throws Exception {
    LocalInspectionTool inspection = new PyInitNewSignatureInspection();
    doTest(getTestName(false), inspection);
  }

  public void testPyCallingNonCallableInspection() throws Exception {
    myFixture.configureByFile("inspections/" + getTestName(true) + "/test.py");
    myFixture.enableInspections(PyCallingNonCallableInspection.class);
    myFixture.checkHighlighting(true, false, false);
  }

  public void testPyPropertyAccessInspection() throws Throwable {
    PythonLanguageLevelPusher.setForcedLanguageLevel(myFixture.getProject(), LanguageLevel.PYTHON26);
    try {
      LocalInspectionTool inspection = new PyPropertyAccessInspection();
      doTest(getTestName(false), inspection);
    }
    finally {
      PythonLanguageLevelPusher.setForcedLanguageLevel(myFixture.getProject(), null);
    }
  }
}
