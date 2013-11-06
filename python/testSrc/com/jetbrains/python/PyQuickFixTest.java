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
import com.intellij.testFramework.TestDataFile;
import com.intellij.testFramework.TestDataPath;
import com.jetbrains.python.codeInsight.PyCodeInsightSettings;
import com.jetbrains.python.documentation.DocStringFormat;
import com.jetbrains.python.documentation.PyDocumentationSettings;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.inspections.*;
import com.jetbrains.python.psi.LanguageLevel;
import org.jetbrains.annotations.NonNls;

/**
 * @author dcheryasov
 */
@TestDataPath("$CONTENT_ROOT/../testData/inspections/")
public class PyQuickFixTest extends PyTestCase {

  public void testAddImport() {
    doInspectionTest(new String[] { "AddImport.py", "ImportTarget.py" }, PyUnresolvedReferencesInspection.class, "Import 'ImportTarget'", true, true);
  }

  public void testAddImportDoc() {
    doInspectionTest(new String[] { "AddImportDoc.py", "ImportTarget.py" }, PyUnresolvedReferencesInspection.class, "Import 'ImportTarget'", true, true);
  }

  public void testAddImportDocComment() {  // PY-728
    doInspectionTest(new String[] { "AddImportDocComment.py", "ImportTarget.py" }, PyUnresolvedReferencesInspection.class, "Import 'ImportTarget'", true, true);
  }

  public void testImportFromModule() {
    doInspectionTest(new String[] { "importFromModule/foo/bar.py", "importFromModule/foo/baz.py", "importFromModule/foo/__init__.py" },
                     PyUnresolvedReferencesInspection.class, "Import 'importFromModule.foo.baz'", true, true);
  }

  public void testImportFromModuleStar() {  // PY-6302
    myFixture.enableInspections(PyUnresolvedReferencesInspection.class);
    myFixture.copyDirectoryToProject("importFromModuleStar", "");
    myFixture.configureFromTempProjectFile("source.py");
    myFixture.checkHighlighting(true, false, false);
    final IntentionAction intentionAction = myFixture.findSingleIntention("Import 'target.xyzzy()'");
    assertNotNull(intentionAction);
    myFixture.launchAction(intentionAction);
    myFixture.checkResultByFile("importFromModuleStar/source_after.py");
  }

  public void testQualifyByImport() {
    final PyCodeInsightSettings settings = PyCodeInsightSettings.getInstance();
    boolean oldPreferFrom = settings.PREFER_FROM_IMPORT;
    boolean oldHighlightUnused = settings.HIGHLIGHT_UNUSED_IMPORTS;
    settings.PREFER_FROM_IMPORT = false;
    settings.HIGHLIGHT_UNUSED_IMPORTS = false;
    try {
      doInspectionTest(new String[]{"QualifyByImport.py", "QualifyByImportFoo.py"}, PyUnresolvedReferencesInspection.class, PyBundle.message("ACT.qualify.with.module"), true, true);
    }
    finally {
      settings.PREFER_FROM_IMPORT = oldPreferFrom;
      settings.HIGHLIGHT_UNUSED_IMPORTS = oldHighlightUnused;
    }
  }

  public void testAddToImportFromList() {
    final PyCodeInsightSettings settings = PyCodeInsightSettings.getInstance();
    boolean oldHighlightUnused = settings.HIGHLIGHT_UNUSED_IMPORTS;
    settings.HIGHLIGHT_UNUSED_IMPORTS = false;
    try {
      doInspectionTest(new String[]{"AddToImportFromList.py", "AddToImportFromFoo.py"}, PyUnresolvedReferencesInspection.class, "Import 'foo(a) from AddToImportFromFoo'", true, true);
    }
    finally {
      settings.HIGHLIGHT_UNUSED_IMPORTS = oldHighlightUnused;
    }
  }
  // TODO: add a test for multiple variants of above

  // TODO: add tests for stub indexes-based autoimport of unimported somehow.

  public void testAddSelf() {
    doInspectionTest("AddSelf.py", PyMethodParametersInspection.class, PyBundle.message("QFIX.add.parameter.self", "self"), true, true);
  }

  public void testReplacePrint() {
    doInspectionTest("ReplacePrint.py", PyCompatibilityInspection.class, PyBundle.message("QFIX.statement.effect"), true, true);
  }

  public void testAddSelfFunction() { //PY-4556
    doInspectionTest("AddSelfFunction.py",
                     PyUnresolvedReferencesInspection.class, PyBundle.message("QFIX.unresolved.reference", "get_a", "self"), true, true);
  }

  public void testAddSelfToClassmethod() { //PY-9721
    doInspectionTest("AddSelfToClassmethod.py",
                     PyUnresolvedReferencesInspection.class, PyBundle.message("QFIX.unresolved.reference", "foo", "cls"), true, true);
  }

  public void testAddCls() {
    doInspectionTest("AddCls.py", PyMethodParametersInspection.class, PyBundle.message("QFIX.add.parameter.self", "cls"), true, true);
  }

  public void testRenameToSelf() {
    doInspectionTest("RenameToSelf.py", PyMethodParametersInspection.class, PyBundle.message("QFIX.rename.parameter.to.$0", "self"), true,
                     true);
  }

  public void testRemoveTrailingSemicolon() {
    doInspectionTest("RemoveTrailingSemicolon.py", PyTrailingSemicolonInspection.class, PyBundle.message("QFIX.remove.trailing.semicolon"),
                     true, true);
  }

  public void testDictCreation() {
    doInspectionTest("DictCreation.py", PyDictCreationInspection.class, PyBundle.message("QFIX.dict.creation"), true, true);
  }

  public void testDictCreationTuple() {  //PY-6283
    doInspectionTest("DictCreationTuple.py", PyDictCreationInspection.class, PyBundle.message("QFIX.dict.creation"), true, true);
  }

  public void testDictCreationDuplicate() {  //PY-7318
    doInspectionTest("DictCreationDuplicate.py", PyDictCreationInspection.class, PyBundle.message("QFIX.dict.creation"), true, true);
  }

  public void testTransformClassicClass() {
    doInspectionTest("TransformClassicClass.py", PyClassicStyleClassInspection.class,
                     PyBundle.message("QFIX.classic.class.transform"), true, true);
  }

  public void testAddGlobalQuickFix() {
    doInspectionTest("AddGlobalStatement.py", PyUnboundLocalVariableInspection.class,
                     PyBundle.message("QFIX.add.global"), true, true);
  }

  public void testAddGlobalExistingQuickFix() {
    doInspectionTest("AddGlobalExistingStatement.py", PyUnboundLocalVariableInspection.class,
                     PyBundle.message("QFIX.add.global"), true, true);
  }

  public void testSimplifyBooleanCheckQuickFix() {
    doInspectionTest("SimplifyBooleanCheck.py", PySimplifyBooleanCheckInspection.class,
                     PyBundle.message("QFIX.simplify.$0", "b"), true, true);
  }

  public void testFromFutureImportQuickFix() {
    doInspectionTest("MoveFromFutureImport.py", PyFromFutureImportInspection.class,
                     PyBundle.message("QFIX.move.from.future.import"), true, true);
  }

  public void testFromFutureImportQuickFixDocString() {  // PY-10080
    doInspectionTest("MoveFromFutureImportDocString.py", PyFromFutureImportInspection.class,
                     PyBundle.message("QFIX.move.from.future.import"), true, true);
  }

  public void testComparisonWithNoneQuickFix() {
    doInspectionTest("ComparisonWithNone.py", PyComparisonWithNoneInspection.class,
                     PyBundle.message("QFIX.replace.equality"), true, true);
  }

  public void testAddClassFix() {
    doInspectionTest("AddClass.py", PyUnresolvedReferencesInspection.class, "Create class 'Xyzzy'", true, true);
  }

  public void testFieldFromUnusedParameter() {  // PY-1398
    doInspectionTest("FieldFromUnusedParameter.py", PyUnusedLocalInspection.class, "Add field 'foo' to class A", true, true);
  }

  public void testFieldFromUnusedParameterKeyword() {  // PY-1602
    doInspectionTest("FieldFromUnusedParameterKeyword.py", PyUnusedLocalInspection.class, "Add field 'foo' to class A", true, true);
  }

  public void testAddFunctionToModule() {  // PY-1602
    doInspectionTest(
      "AddFunctionToModule.py",
      PyUnresolvedReferencesInspection.class,
      PyBundle.message("QFIX.NAME.add.function.$0.to.module.$1", "frob", "AddFunctionToModule.py"),
      true, true
    );
  }

  public void testRedundantParentheses() {  // PY-1470
    String[] testFiles = new String[]{"RedundantParentheses.py"};
    myFixture.enableInspections(PyRedundantParenthesesInspection.class);
    myFixture.configureByFiles(testFiles);
    myFixture.checkHighlighting(true, false, true);
    final IntentionAction intentionAction = myFixture.findSingleIntention(PyBundle.message("QFIX.redundant.parentheses"));
    assertNotNull(intentionAction);
    myFixture.launchAction(intentionAction);
    myFixture.checkResultByFile(graftBeforeExt(testFiles[0], "_after"));

  }

  public void testRedundantParenthesesBoolean() {  // PY-3095
    doInspectionTest("RedundantParenthesesBoolean.py", PyRedundantParenthesesInspection.class,
                          PyBundle.message("QFIX.redundant.parentheses"), true, true);
  }

  public void testRedundantParenthesesMore() {  // PY-3239
    doInspectionTest("RedundantParenthesesMore.py", PyRedundantParenthesesInspection.class,
                          PyBundle.message("QFIX.redundant.parentheses"), true, true);
  }

  public void testChainedComparisons() {  // PY-1020
    doInspectionTest("ChainedComparisons.py", PyChainedComparisonsInspection.class,
                          PyBundle.message("QFIX.chained.comparison"), true, true);
  }

  public void testChainedComparison1() {  // PY-3126
    doInspectionTest("ChainedComparison1.py", PyChainedComparisonsInspection.class,
                          PyBundle.message("QFIX.chained.comparison"), true, true);
  }

  public void testChainedComparison2() {  // PY-3126
    doInspectionTest("ChainedComparison2.py", PyChainedComparisonsInspection.class,
                          PyBundle.message("QFIX.chained.comparison"), true, true);
  }

  public void testChainedComparison3() {  // PY-3126
    doInspectionTest("ChainedComparison3.py", PyChainedComparisonsInspection.class,
                          PyBundle.message("QFIX.chained.comparison"), true, true);
  }

  public void testChainedComparison4() {  // PY-5623
    doInspectionTest("ChainedComparison4.py", PyChainedComparisonsInspection.class,
                          PyBundle.message("QFIX.chained.comparison"), true, true);
  }

  public void testChainedComparison5() {  // PY-6467
    doInspectionTest("ChainedComparison5.py", PyChainedComparisonsInspection.class,
                     PyBundle.message("QFIX.chained.comparison"), true, true);
  }

  public void testStatementEffect() {  // PY-1362, PY-2585
    doInspectionTest("StatementEffect.py", PyStatementEffectInspection.class,
                          PyBundle.message("QFIX.statement.effect"), true, true);
  }

  public void testStatementEffectPrint() {
    setLanguageLevel(LanguageLevel.PYTHON33);
    try {
      doInspectionTest("StatementEffectPrint.py", PyStatementEffectInspection.class,
                          PyBundle.message("QFIX.statement.effect"), true, true);
    } finally {
      setLanguageLevel(LanguageLevel.getDefault());
    }
  }

  public void testStatementEffectIntroduceVariable() {  // PY-1265
    doInspectionTest("StatementEffectIntroduceVariable.py", PyStatementEffectInspection.class,
                          PyBundle.message("QFIX.statement.effect.introduce.variable"), true, true);
  }

  public void testUnresolvedWith() {  // PY-2083
    setLanguageLevel(LanguageLevel.PYTHON25);
    doInspectionTest("UnresolvedWith.py", PyUnresolvedReferencesInspection.class,
                          PyBundle.message("QFIX.unresolved.reference.add.future"), true, true);
  }

  public void testUnresolvedRefCreateFunction() {  // PY-2092
    doInspectionTest("UnresolvedRefCreateFunction.py", PyUnresolvedReferencesInspection.class,
                          PyBundle.message("QFIX.unresolved.reference.create.function.$0", "ref"), true, true);
  }

  public void testReplaceNotEqOperator() {
    doInspectionTest("ReplaceNotEqOperator.py", PyCompatibilityInspection.class,
                     PyBundle.message("INTN.replace.noteq.operator"), true, true);
  }

  public void testListCreation() {
    doInspectionTest("ListCreation.py", PyListCreationInspection.class,
                     PyBundle.message("QFIX.list.creation"), true, true);
  }

  public void testConvertSingleQuotedDocstring() {                      //PY-1445
    doInspectionTest("ConvertSingleQuotedDocstring.py", PySingleQuotedDocstringInspection.class,
                     PyBundle.message("QFIX.convert.single.quoted.docstring"), true, true);
  }

  public void testConvertSingleQuotedDocstringEscape() {                      //PY-8926
    doInspectionTest("ConvertSingleQuotedDocstringEscape.py", PySingleQuotedDocstringInspection.class,
                     PyBundle.message("QFIX.convert.single.quoted.docstring"), true, true);
  }

  public void testDefaultArgument() {                      //PY-3127
    doInspectionTest("DefaultArgument.py", PyDefaultArgumentInspection.class,
                     PyBundle.message("QFIX.default.argument"), true, true);
  }

  public void testPyArgumentEqualDefault() {                      //PY-3125
    doInspectionTest("ArgumentEqualDefault.py", PyArgumentEqualDefaultInspection.class,
                     PyBundle.message("QFIX.remove.argument.equal.default"), true, true);
  }

  public void testAddCallSuper() {                      //PY-3315
    doInspectionTest("AddCallSuper.py", PyMissingConstructorInspection.class,
                     PyBundle.message("QFIX.add.super"), true, true);
  }

  public void testAddCallSuper1() {                      //PY-4017
    doInspectionTest("AddCallSuper1.py", PyMissingConstructorInspection.class,
                     PyBundle.message("QFIX.add.super"), true, true);
  }

  public void testAddCallSuperPass() {                      //PY-8654
    doInspectionTest("AddCallSuperPass.py", PyMissingConstructorInspection.class,
                     PyBundle.message("QFIX.add.super"), true, true);
  }

  public void testAddEncoding() {                      //PY-491
    doInspectionTest("AddEncoding.py", PyMandatoryEncodingInspection.class,
                     PyBundle.message("QFIX.add.encoding"), true, true);
  }

  public void testRemoveDecorator() {                      //PY-3348
    doInspectionTest("RemoveDecorator.py", PyDecoratorInspection.class,
                     PyBundle.message("QFIX.remove.decorator"), true, true);
  }

  public void testAddParameter() {
    doInspectionTest("AddParameter.py", PyUnresolvedReferencesInspection.class,
                     PyBundle.message("QFIX.unresolved.reference.add.param.$0", "test"), true, true);
  }

  public void testMoveDocstring() {                      //PY-4398
    doInspectionTest("MoveDocstring.py", PyStatementEffectInspection.class,
                     PyBundle.message("QFIX.statement.effect.move.docstring"), true, true);
  }

  public void testRenameUnresolvedReference() {                      //PY-6595
    doInspectionTest("RenameUnresolvedReference.py", PyUnresolvedReferencesInspection.class,
                     PyBundle.message("QFIX.rename.unresolved.reference"), true, true);
  }

  public void testSetFunctionToLiteral() {                      //PY-3120
    setLanguageLevel(LanguageLevel.PYTHON27);
    doInspectionTest("SetFunctionToLiteral.py", PySetFunctionToLiteralInspection.class,
                     PyBundle.message("QFIX.replace.function.set.with.literal"), true, true);
  }

  public void testDocstringParams() {                      //PY-3394
    PyDocumentationSettings documentationSettings = PyDocumentationSettings.getInstance(myFixture.getModule());
    documentationSettings.setFormat(DocStringFormat.EPYTEXT);
    try {
      doInspectionTest("DocstringParams.py", PyDocstringInspection.class,
                     PyBundle.message("QFIX.docstring.add.$0", "b"), true, true);
    }
    finally {
      documentationSettings.setFormat(DocStringFormat.PLAIN);
    }
  }

  public void testDocstringParams1() {
    PyDocumentationSettings documentationSettings = PyDocumentationSettings.getInstance(myFixture.getModule());
    documentationSettings.setFormat(DocStringFormat.EPYTEXT);
    try {
      doInspectionTest("DocstringParams1.py", PyDocstringInspection.class,
                     PyBundle.message("QFIX.docstring.remove.$0", "c"), true, true);
    }
    finally {
      documentationSettings.setFormat(DocStringFormat.PLAIN);
    }
  }

  public void testDocstringParams2() {                      //PY-4964
    PyDocumentationSettings documentationSettings = PyDocumentationSettings.getInstance(myFixture.getModule());
    documentationSettings.setFormat(DocStringFormat.EPYTEXT);
    try {
      doInspectionTest("DocstringParams2.py", PyDocstringInspection.class,
                     PyBundle.message("QFIX.docstring.add.$0", "ham"), true, true);
    }
    finally {
      documentationSettings.setFormat(DocStringFormat.PLAIN);
    }
  }

  public void testUnnecessaryBackslash() {
    String[] testFiles = new String[]{"UnnecessaryBackslash.py"};
    myFixture.enableInspections(PyUnnecessaryBackslashInspection.class);
    myFixture.configureByFiles(testFiles);
    myFixture.checkHighlighting(true, false, true);
    IntentionAction intentionAction = myFixture.getAvailableIntention(PyBundle.message("QFIX.remove.unnecessary.backslash"));
    assertNotNull(intentionAction);
    myFixture.launchAction(intentionAction);
    myFixture.checkResultByFile(graftBeforeExt(testFiles[0], "_after"));
  }

  public void testUnresolvedRefTrueFalse() {                      //PY-3051
    doInspectionTest("UnresolvedRefTrueFalse.py", PyUnresolvedReferencesInspection.class,
                     PyBundle.message("QFIX.unresolved.reference.replace.$0", "True"), true, true);
  }

  public void testUnnecessaryBackslashInArgumentList() {
    String[] testFiles = new String[]{"UnnecessaryBackslashInArguments.py"};
    myFixture.enableInspections(PyUnnecessaryBackslashInspection.class);
    myFixture.configureByFiles(testFiles);
    myFixture.checkHighlighting(true, false, true);
    IntentionAction intentionAction = myFixture.getAvailableIntention(PyBundle.message("QFIX.remove.unnecessary.backslash"));
    assertNotNull(intentionAction);
    myFixture.launchAction(intentionAction);
    myFixture.checkResultByFile(graftBeforeExt(testFiles[0], "_after"));
  }

  // PY-8788
  public void testRenameShadowingBuiltins() {
    final String fileName = "RenameShadowingBuiltins.py";
    myFixture.configureByFile(fileName);
    myFixture.enableInspections(PyShadowingBuiltinsInspection.class);
    myFixture.checkHighlighting(true, false, true);
    final IntentionAction intentionAction = myFixture.getAvailableIntention("Rename element");
    assertNotNull(intentionAction);
    myFixture.launchAction(intentionAction);
    myFixture.checkResultByFile(graftBeforeExt(fileName, "_after"));
  }

  // PY-8788
  public void testRenameFunctionShadowingBuiltins() {
    final String fileName = "RenameFunctionShadowingBuiltins.py";
    myFixture.configureByFile(fileName);
    myFixture.enableInspections(PyShadowingBuiltinsInspection.class);
    myFixture.checkHighlighting(true, false, true);
    final IntentionAction intentionAction = myFixture.getAvailableIntention("Rename element");
    assertNotNull(intentionAction);
    myFixture.launchAction(intentionAction);
    myFixture.checkResultByFile(graftBeforeExt(fileName, "_after"));
  }

  @Override
  @NonNls
  protected String getTestDataPath() {
    return PythonTestUtil.getTestDataPath() + "/inspections/";
  }

  protected void doInspectionTest(@TestDataFile @NonNls String testFileName,
                                  final Class inspectionClass,
                                  @NonNls String quickFixName,
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
   * @throws Exception
   */
  protected void doInspectionTest(@NonNls String[] testFiles,
                                  final Class inspectionClass,
                                  @NonNls String quickFixName,
                                  boolean applyFix,
                                  boolean available) {
    myFixture.enableInspections(inspectionClass);
    myFixture.configureByFiles(testFiles);
    myFixture.checkHighlighting(true, false, false);
    final IntentionAction intentionAction = myFixture.findSingleIntention(quickFixName);
    if (available) {
      assertNotNull(intentionAction);
      if (applyFix) {
        myFixture.launchAction(intentionAction);

        myFixture.checkResultByFile(graftBeforeExt(testFiles[0], "_after"));
      }
    }
    else {
      assertNull(intentionAction);
    }
  }

  // Turns "name.ext" to "name_insertion.ext"

  @NonNls
  private static String graftBeforeExt(String name, String insertion) {
    int dotpos = name.indexOf('.');
    if (dotpos < 0) dotpos = name.length();
    return name.substring(0, dotpos) + insertion + name.substring(dotpos, name.length());
  }
}
