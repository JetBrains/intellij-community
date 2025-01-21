// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.testFramework.LightProjectDescriptor;
import com.jetbrains.python.documentation.docstrings.DocStringFormat;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.inspections.*;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.sdk.PythonSdkType;
import com.jetbrains.python.sdk.PythonSdkUtil;
import org.jetbrains.annotations.Nullable;


public class PythonInspectionsTest extends PyTestCase {

  @Override
  protected @Nullable LightProjectDescriptor getProjectDescriptor() {
    return ourPy2Descriptor;
  }

  public void testReturnValueFromInit() {
    LocalInspectionTool inspection = new PyReturnFromInitInspection();
    doTest(getTestName(true), inspection);
  }

  private void doTest(String testName, LocalInspectionTool localInspectionTool) {
    myFixture.testInspection("inspections/" + testName, new LocalInspectionToolWrapper(localInspectionTool));
  }

  private void doTestWithPy3k(String testName, LocalInspectionTool localInspectionTool) {
    doTestWithLanguageLevel(testName, localInspectionTool, LanguageLevel.PYTHON34);
  }

  private void doTestWithLanguageLevel(String testName,
                                       LocalInspectionTool localInspectionTool,
                                       LanguageLevel languageLevel) {
    runWithLanguageLevel(languageLevel, () -> doTest(testName, localInspectionTool));
  }

  public void testPyMethodFirstArgAssignmentInspection() {
    LocalInspectionTool inspection = new PyMethodFirstArgAssignmentInspection();
    doTest(getTestName(false), inspection);
  }

  public void testPyMethodParametersInspection() {
    doHighlightingTest(PyMethodParametersInspection.class);
  }

  public void testPyMethodParametersInspectionInitSubclass() {
    doHighlightingTest(PyMethodParametersInspection.class, LanguageLevel.PYTHON36);
  }

  public void testPyMethodParametersInspectionClassGetItem() {
    doHighlightingTest(PyMethodParametersInspection.class, LanguageLevel.PYTHON37);
  }

  // PY-14896
  public void testPyMethodParametersInspectionAbstractClassAndStaticMethods() {
    doHighlightingTest(PyMethodParametersInspection.class, LanguageLevel.PYTHON34);
  }

  public void testPyNestedDecoratorsInspection() {
    LocalInspectionTool inspection = new PyNestedDecoratorsInspection();
    doTest(getTestName(false), inspection);
  }

  public void testPyTrailingSemicolonInspection() {
    LocalInspectionTool inspection = new PyTrailingSemicolonInspection();
    doTest(getTestName(false), inspection);
  }

  public void testPyDictCreationInspection() {
    doHighlightingTest(PyDictCreationInspection.class, LanguageLevel.PYTHON26);
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
    doHighlightingTest(PyDefaultArgumentInspection.class);
  }

  public void testPyDocstringInspection() {
    LocalInspectionTool inspection = new PyMissingOrEmptyDocstringInspection();
    doTest(getTestName(false), inspection);
  }

  //PY-3373
  public void testPyDocstringParametersInspection() {
    runWithDocStringFormat(DocStringFormat.REST, () -> doHighlightingTest(PyIncorrectDocstringInspection.class, LanguageLevel.PYTHON34));
  }

  // PY-9795
  public void testGoogleDocstringParametersInspection() {
    runWithDocStringFormat(DocStringFormat.GOOGLE, () -> doHighlightingTest(PyIncorrectDocstringInspection.class, LanguageLevel.PYTHON34));
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

  public void testPySuperArgumentsInspection() {
    LocalInspectionTool inspection = new PySuperArgumentsInspection();
    doTest(getTestName(false), inspection);
  }

  public void testPyByteLiteralInspection() {
    doHighlightingTest(PyByteLiteralInspection.class);
  }

  public void testPyTupleItemAssignmentInspection() {
    LocalInspectionTool inspection = new PyTupleItemAssignmentInspection();
    doTest(getTestName(false), inspection);
  }

  public void testPyInitNewSignatureInspection() {
    final String folderPath = "inspections/" + getTestName(false) + "/";

    myFixture.copyDirectoryToProject(folderPath, "");
    myFixture.configureFromTempProjectFile("test.py");
    myFixture.enableInspections(PyInitNewSignatureInspection.class);
    myFixture.checkHighlighting(true, false, true);
  }

  private void doHighlightingTest(final Class<? extends PyInspection> inspectionClass) {
    myFixture.configureByFile("inspections/" + getTestName(false) + "/test.py");
    myFixture.enableInspections(inspectionClass);
    myFixture.checkHighlighting(true, false, true);
  }

  private void doHighlightingTest(final Class<? extends PyInspection> inspectionClass, final LanguageLevel languageLevel) {
    runWithLanguageLevel(languageLevel, () -> doHighlightingTest(inspectionClass));
  }

  private void doHighlightingTest(InspectionProfileEntry entry, final LanguageLevel languageLevel) {
    runWithLanguageLevel(languageLevel, () -> doHighlightingTest(entry));
  }

  private void doHighlightingTest(InspectionProfileEntry entry) {
    myFixture.configureByFile("inspections/" + getTestName(false) + "/test.py");
    myFixture.enableInspections(entry);
    myFixture.checkHighlighting(true, false, true);
  }

  public void testPyPropertyDefinitionInspection26() {
    doHighlightingTest(PyPropertyDefinitionInspection.class, LanguageLevel.PYTHON26);
  }

  // PY-11426
  public void testPyPropertyDefinitionInspection33() {
    doHighlightingTest(PyPropertyDefinitionInspection.class, LanguageLevel.PYTHON34);
  }

  // PY-40180
  public void testEmptyProtocolProperty() {
    doHighlightingTest(PyPropertyDefinitionInspection.class, LanguageLevel.getLatest());
  }

  public void testInconsistentIndentation() {
    doHighlightingTest(PyInconsistentIndentationInspection.class, LanguageLevel.PYTHON26);
  }

  public void testPyChainedComparisonsInspection() {
    doHighlightingTest(PyChainedComparisonsInspection.class);
  }

  public void testPyChainedComparisonsInspectionWithConstantInTheMiddle() {
    myFixture.configureByFile("inspections/" + getTestName(false) + "/test.py");
    PyChainedComparisonsInspection inspection = new PyChainedComparisonsInspection();
    myFixture.enableInspections(inspection);
    inspection.ignoreConstantInTheMiddle = true;
    myFixture.checkHighlighting(true, false, true);
  }

  public void testPyBroadExceptionInspection() {
    doHighlightingTest(PyBroadExceptionInspection.class);
  }

  public void testPyDictDuplicateKeysInspection() {
    doHighlightingTest(PyDictDuplicateKeysInspection.class, LanguageLevel.PYTHON37);
  }

  public void testPyListCreationInspection() {         //PY-2823
    doHighlightingTest(PyListCreationInspection.class);
  }

  public void testPyUnnecessaryBackslashInspection() {    //PY-2952
    runWithLanguageLevel(LanguageLevel.PYTHON27, () -> doHighlightingTest(PyUnnecessaryBackslashInspection.class));
  }

  public void testPySingleQuotedDocstringInspection() {    //PY-1445
    doHighlightingTest(PySingleQuotedDocstringInspection.class);
  }

  public void testPyArgumentEqualDefaultInspection() {    //PY-3125
    final LanguageLevel languageLevel = PythonSdkType.getLanguageLevelForSdk(PythonSdkUtil.findPythonSdk(myFixture.getModule()));

    runWithAdditionalFileInLibDir(
      PyBuiltinCache.getBuiltinsFileName(languageLevel),
      """
        class property(object):
          def __init__(self, fget=None, fset=None, fdel=None, doc=None):
            pass
        def open(file, mode='r', buffering=None, encoding=None, errors=None, newline=None, closefd=True):
          pass""",
      (__) -> doHighlightingTest(PyArgumentEqualDefaultInspection.class)
    );
  }

  public void testPyArgumentEqualDefaultInspectionPy3() {
    doHighlightingTest(PyArgumentEqualDefaultInspection.class, LanguageLevel.PYTHON34);
  }

  public void testPyNonAsciiCharInspection() {    //PY-5868
    doHighlightingTest(PyNonAsciiCharInspection.class);
  }

  public void testPyNonAsciiCharReferenceInspection() {
    doHighlightingTest(PyNonAsciiCharInspection.class);
  }

  public void testPySetFunctionToLiteralInspection() {    //PY-3120
    runWithLanguageLevel(LanguageLevel.PYTHON27, () -> doHighlightingTest(PySetFunctionToLiteralInspection.class));
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

  // PY-21645
  public void testInspectionsDisabledInFunctionTypeComments() {
    myFixture.enableInspections(PyIncorrectDocstringInspection.class);
    myFixture.enableInspections(PyMissingOrEmptyDocstringInspection.class);
    myFixture.enableInspections(PySingleQuotedDocstringInspection.class);
    myFixture.enableInspections(PyByteLiteralInspection.class);
    myFixture.enableInspections(PyMandatoryEncodingInspection.class);
    myFixture.enableInspections(PyNonAsciiCharInspection.class);

    myFixture.configureByFile("inspections/" + getTestName(false) + "/test.py");
    myFixture.checkHighlighting(true, false, true);
  }

  // PY-32364
  public void testAddEncodingInDisabledPy3() {
    final PyMandatoryEncodingInspection inspection = new PyMandatoryEncodingInspection();
    inspection.myAllPythons = false;
    doHighlightingTest(inspection, LanguageLevel.PYTHON34);
  }

  // PY-32364
  public void testAddEncodingInEnabledPy3() {
    final PyMandatoryEncodingInspection inspection = new PyMandatoryEncodingInspection();
    inspection.myAllPythons = true;
    doHighlightingTest(inspection, LanguageLevel.PYTHON34);
  }
}
