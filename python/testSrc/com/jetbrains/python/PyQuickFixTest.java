// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.TestDataFile;
import com.intellij.testFramework.TestDataPath;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.codeInsight.PyCodeInsightSettings;
import com.jetbrains.python.documentation.docstrings.DocStringFormat;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.inspections.*;
import com.jetbrains.python.inspections.unresolvedReference.PyUnresolvedReferencesInspection;
import com.jetbrains.python.packaging.PyPIPackageCache;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.quickFixes.PyRenameElementQuickFixTest;
import org.intellij.lang.regexp.inspection.RegExpRedundantEscapeInspection;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@TestDataPath("$CONTENT_ROOT/../testData/inspections/")
public class PyQuickFixTest extends PyTestCase {

  @Override
  protected @Nullable LightProjectDescriptor getProjectDescriptor() {
    return ourPy2Descriptor;
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    InspectionProfileImpl.INIT_INSPECTIONS = true;
    myFixture.setCaresAboutInjection(false);
    PyRenameElementQuickFixTest.registerTestNameSuggestionProvider(getTestRootDisposable());
  }

  @Override
  protected void tearDown() throws Exception {
    InspectionProfileImpl.INIT_INSPECTIONS = false;
    super.tearDown();
  }

  public void testAddImport() {
    doInspectionTest(new String[]{"AddImport.py", "ImportTarget.py"}, PyUnresolvedReferencesInspection.class,
                     "Import 'ImportTarget'", true, true);
  }

  public void testAddImportDoc() {
    doInspectionTest(new String[]{"AddImportDoc.py", "ImportTarget.py"}, PyUnresolvedReferencesInspection.class,
                     "Import 'ImportTarget'", true, true);
  }

  // PY-728
  public void testAddImportDocComment() {
    doInspectionTest(new String[]{"AddImportDocComment.py", "ImportTarget.py"}, PyUnresolvedReferencesInspection.class,
                     "Import 'ImportTarget'", true, true);
  }

  // PY-42307
  public void testInstallAndImportPackageByNameAlias() {
    //noinspection removal
    PyPIPackageCache.reload(List.of("pandas", "pd"));
    myFixture.enableInspections(PyUnresolvedReferencesInspection.class);
    myFixture.configureByText(PythonFileType.INSTANCE, "pd<caret>.array()");
    myFixture.findSingleIntention("Import 'turtle.pd'"); // standard library
    myFixture.findSingleIntention("Install and import package 'pd'"); // PyPI
    myFixture.findSingleIntention("Install and import package 'pandas'"); // 'pd' is a common import alias for 'pandas' from PyPI
  }

  public void testImportFromModule() {
    doInspectionTest(new String[]{"importFromModule/foo/bar.py", "importFromModule/foo/baz.py", "importFromModule/foo/__init__.py"},
                     PyUnresolvedReferencesInspection.class, "Import 'importFromModule.foo.baz'", true, true);
  }

  // PY-14365
  public void testObjectBaseIsNotShownInAutoImportQuickfix() {
    myFixture.copyDirectoryToProject("objectBaseIsNotShownInAutoImportQuickfix", "");
    myFixture.configureByFile("main.py");
    myFixture.enableInspections(PyUnresolvedReferencesInspection.class);
    final IntentionAction intention = myFixture.findSingleIntention("Import");
    assertNotNull(intention);
    assertEquals("Import 'module.MyOldStyleClass'", intention.getText());
  }

  // PY-6302
  public void testImportFromModuleStar() {
    myFixture.enableInspections(PyUnresolvedReferencesInspection.class);
    myFixture.copyDirectoryToProject("importFromModuleStar", "");
    myFixture.configureFromTempProjectFile("source.py");
    myFixture.checkHighlighting(true, false, false);
    final IntentionAction intentionAction = myFixture.findSingleIntention("Import 'target.xyzzy'");
    assertNotNull(intentionAction);
    myFixture.launchAction(intentionAction);
    myFixture.checkResultByFile("importFromModuleStar/source_after.py");
  }

  public void testQualifyByImport() {
    final PyCodeInsightSettings settings = PyCodeInsightSettings.getInstance();
    boolean oldPreferFrom = settings.PREFER_FROM_IMPORT;
    settings.PREFER_FROM_IMPORT = false;
    try {
      doInspectionTest(new String[]{"QualifyByImport.py", "QualifyByImportFoo.py"}, PyUnresolvedReferencesInspection.class,
                       PyPsiBundle.message("ACT.qualify.with.module"), true, true);
    }
    finally {
      settings.PREFER_FROM_IMPORT = oldPreferFrom;
    }
  }

  public void testAddToImportFromList() {
    doInspectionTest(new String[]{"AddToImportFromList.py", "AddToImportFromFoo.py"}, PyUnresolvedReferencesInspection.class,
                     "Import 'add_to_import_test_unique_name from AddToImportFromFoo'", true, true);
  }
  // TODO: add a test for multiple variants of above

  // TODO: add tests for stub indexes-based autoimport of unimported somehow.

  public void testAddSelf() {
    doInspectionTest(PyMethodParametersInspection.class, PyPsiBundle.message("QFIX.add.parameter.self", "self"), true, true);
  }

  public void testReplacePrint() {
    doInspectionTest(PyCompatibilityInspection.class, PyPsiBundle.message("QFIX.statement.effect"), true, true);
  }

  // PY-22045
  public void testBatchReplacePrintInsertsFutureImportOnlyOnce() {
    doInspectionTest(
      PyCompatibilityInspection.class,
      "Fix all 'Code is incompatible with specific Python versions' problems in file",
      true,
      true
    );
  }

  // PY-4556
  public void testAddSelfFunction() {
    doInspectionTest("AddSelfFunction.py", PyUnresolvedReferencesInspection.class,
                     PyPsiBundle.message("QFIX.unresolved.reference", "get_a", "self"), true, true);
  }

  // PY-9721
  public void testAddSelfToClassmethod() {
    doInspectionTest("AddSelfToClassmethod.py", PyUnresolvedReferencesInspection.class,
                     PyPsiBundle.message("QFIX.unresolved.reference", "foo", "cls"), true, true);
  }

  public void testAddCls() {
    doInspectionTest(PyMethodParametersInspection.class, PyPsiBundle.message("QFIX.add.parameter.self", "cls"), true, true);
  }

  public void testRenameToSelf() {
    doInspectionTest(PyMethodParametersInspection.class, PyPsiBundle.message("QFIX.rename.parameter", "self"), true, true);
  }

  public void testRemoveTrailingSemicolon() {
    doInspectionTest(PyTrailingSemicolonInspection.class, PyPsiBundle.message("QFIX.remove.trailing.semicolon"), true, true);
  }

  public void testDictCreation() {
    doInspectionTest(PyDictCreationInspection.class, PyPsiBundle.message("QFIX.dict.creation"), true, true);
  }

  // PY-6283
  public void testDictCreationTuple() {
    doInspectionTest(PyDictCreationInspection.class, PyPsiBundle.message("QFIX.dict.creation"), true, true);
  }

  // PY-7318
  public void testDictCreationDuplicate() {
    doInspectionTest(PyDictCreationInspection.class, PyPsiBundle.message("QFIX.dict.creation"), true, true);
  }

  // PY-40177
  public void testDictCreationWithDoubleStars() {
    runWithLanguageLevel(LanguageLevel.getLatest(),
                         () -> doInspectionTest(PyDictCreationInspection.class, PyPsiBundle.message("QFIX.dict.creation"), true, true));
  }

  public void testTransformClassicClass() {
    doInspectionTest(PyClassicStyleClassInspection.class, PyPsiBundle.message("QFIX.classic.class.transform"), true, true);
  }

  public void testAddGlobalStatement() {
    doInspectionTest(PyUnboundLocalVariableInspection.class, PyPsiBundle.message("QFIX.add.global"), true, true);
  }

  public void testAddGlobalExistingStatement() {
    doInspectionTest(PyUnboundLocalVariableInspection.class, PyPsiBundle.message("QFIX.add.global"), true, true);
  }

  public void testSimplifyBooleanCheck() {
    doInspectionTest(PySimplifyBooleanCheckInspection.class, PyPsiBundle.message("QFIX.simplify.boolean.expression", "b"), true, true);
  }

  public void testMoveFromFutureImport() {
    doInspectionTest(PyFromFutureImportInspection.class, PyPsiBundle.message("QFIX.move.from.future.import"), true, true);
  }

  // PY-10080
  public void testMoveFromFutureImportDocString() {
    doInspectionTest(PyFromFutureImportInspection.class, PyPsiBundle.message("QFIX.move.from.future.import"), true, true);
  }

  // PY-23475
  public void testMoveFromFutureImportAboveModuleLevelDunder() {
    doInspectionTest(PyFromFutureImportInspection.class, PyPsiBundle.message("QFIX.move.from.future.import"), true, true);
  }

  public void testComparisonWithNone() {
    doInspectionTest(PyComparisonWithNoneInspection.class, PyPsiBundle.message("QFIX.replace.equality"), true, true);
  }

  public void testAddClassFix() {
    doInspectionTest("AddClass.py", PyUnresolvedReferencesInspection.class, "Create class 'Xyzzy'", true, true);
  }

  // PY-42389
  public void testAddClassFixPython3() {
    runWithLanguageLevel(LanguageLevel.getLatest(), () ->
                         doInspectionTest(PyUnresolvedReferencesInspection.class, "Create class 'Xyzzy'", true, true));
  }

  // PY-21204
  public void testAddClassFromTypeComment() {
    doInspectionTest(PyUnresolvedReferencesInspection.class, "Create class 'MyClass'", true, true);
  }

  // PY-21204
  public void testAddClassFromFString() {
    runWithLanguageLevel(LanguageLevel.PYTHON36, 
                         () -> doInspectionTest(PyUnresolvedReferencesInspection.class, "Create class 'MyClass'", true, true));
  }

  // PY-33802
  public void testAddClassToImportedModule() {
    doMultiFilesInspectionTest(PyUnresolvedReferencesInspection.class,
                               PyPsiBundle.message("QFIX.create.class.in.module", "Clzz", "mod.py"), "mod.py");
  }

  // PY-33802
  public void testAddClassToImportedPackage() {
    doMultiFilesInspectionTest(PyUnresolvedReferencesInspection.class,
                               PyPsiBundle.message("QFIX.create.class.in.module", "Clzz", "__init__.py"), "pkg/__init__.py");
  }

  // PY-33802
  public void testAddClassToModuleInFromImport() {
    doMultiFilesInspectionTest(PyUnresolvedReferencesInspection.class,
                               PyPsiBundle.message("QFIX.create.class.in.module", "Clzz", "mod.py"), "mod.py");
  }

  // PY-33802
  public void testAddClassToPackageInFromImport() {
    doMultiFilesInspectionTest(PyUnresolvedReferencesInspection.class,
                               PyPsiBundle.message("QFIX.create.class.in.module", "Clzz", "__init__.py"),
                               "mypack/__init__.py");
  }

  // PY-21204
  public void testAddFunctionFromFString() {
    runWithLanguageLevel(LanguageLevel.PYTHON36,
                         () -> doInspectionTest(PyUnresolvedReferencesInspection.class, PyPsiBundle.message("QFIX.NAME.unresolved.reference.create.function", "my_function"), true, true));
  }

  // PY-1465
  public void testAddFunctionToModuleInImport() {
    doMultiFilesInspectionTest(PyUnresolvedReferencesInspection.class,
                               PyPsiBundle.message("QFIX.create.function.in.module", "func", "mod.py"),
                               "mod.py");
  }

  // PY-34710
  public void testAddFunctionToModuleInFromImport() {
    doMultiFilesInspectionTest(PyUnresolvedReferencesInspection.class,
                               PyPsiBundle.message("QFIX.create.function.in.module", "foo", "mod.py"),
                               "mod.py");
  }

  // PY-34710
  public void testAddFunctionToPackageInFromImport() {
    doMultiFilesInspectionTest(PyUnresolvedReferencesInspection.class,
                               PyPsiBundle.message("QFIX.create.function.in.module", "foo", "__init__.py"),
                               "mypack/__init__.py");
  }

  // PY-1470
  public void testRedundantParentheses() {
    String[] testFiles = {"RedundantParentheses.py"};
    myFixture.enableInspections(PyRedundantParenthesesInspection.class);
    myFixture.configureByFiles(testFiles);
    myFixture.checkHighlighting(true, false, true);
    final IntentionAction intentionAction = myFixture.findSingleIntention(PyPsiBundle.message("QFIX.redundant.parentheses"));
    assertNotNull(intentionAction);
    myFixture.launchAction(intentionAction);
    myFixture.checkResultByFile(graftBeforeExt(testFiles[0], "_after"));
  }

  // PY-3095
  public void testRedundantParenthesesBoolean() {
    doInspectionTest(PyRedundantParenthesesInspection.class, PyPsiBundle.message("QFIX.redundant.parentheses"), true, true);
  }

  // PY-3239
  public void testRedundantParenthesesMore() {
    doInspectionTest(PyRedundantParenthesesInspection.class, PyPsiBundle.message("QFIX.redundant.parentheses"), true, true);
  }

  // PY-12679
  public void testRedundantParenthesesParenthesizedExpression() {
    doInspectionTest(PyRedundantParenthesesInspection.class, PyPsiBundle.message("QFIX.redundant.parentheses"), true, true);
  }

  public void testRedundantParenthesesMultipleParentheses() {
    doInspectionTest(PyRedundantParenthesesInspection.class, PyPsiBundle.message("QFIX.redundant.parentheses"), true, true);
  }

  // PY-15506
  public void testEmptyListOfBaseClasses() {
    doInspectionTest(PyRedundantParenthesesInspection.class, PyPsiBundle.message("QFIX.redundant.parentheses"), true, true);
  }

  // PY-18203
  public void testRedundantParenthesesInTuples() {
    doInspectionTest(PyRedundantParenthesesInspection.class, PyPsiBundle.message("QFIX.redundant.parentheses"), true, true);
  }

  // PY-1020
  public void testChainedComparisons() {
    doInspectionTest(PyChainedComparisonsInspection.class, PyPsiBundle.message("QFIX.chained.comparison"), true, true);
  }

  // PY-3126
  public void testChainedComparison1() {
    doInspectionTest(PyChainedComparisonsInspection.class, PyPsiBundle.message("QFIX.chained.comparison"), true, true);
  }

  // PY-3126
  public void testChainedComparison2() {
    doInspectionTest(PyChainedComparisonsInspection.class, PyPsiBundle.message("QFIX.chained.comparison"), true, true);
  }

  // PY-3126
  public void testChainedComparison3() {
    doInspectionTest(PyChainedComparisonsInspection.class, PyPsiBundle.message("QFIX.chained.comparison"), true, true);
  }

  // PY-5623
  public void testChainedComparison4() {
    doInspectionTest(PyChainedComparisonsInspection.class, PyPsiBundle.message("QFIX.chained.comparison"), true, true);
  }

  // PY-6467
  public void testChainedComparison5() {
    doInspectionTest(PyChainedComparisonsInspection.class, PyPsiBundle.message("QFIX.chained.comparison"), true, true);
  }

  // PY-20004
  public void testChainedComparison7() {
    doInspectionTest(PyChainedComparisonsInspection.class, PyPsiBundle.message("QFIX.chained.comparison"), true, true);
  }

  // PY-14002
  public void testChainedComparisonWithCommonBinaryExpression() {
    doInspectionTest(PyChainedComparisonsInspection.class, PyPsiBundle.message("QFIX.chained.comparison"), true, true);
  }

  // PY-19583
  public void testChainedComparison6() {
    doInspectionTest(PyChainedComparisonsInspection.class, "Simplify chained comparison", true, true);
  }

  // PY-24942
  public void testChainedComparison8() {
    doInspectionTest(PyChainedComparisonsInspection.class, "Simplify chained comparison", true, true);
  }

  // PY-29121
  public void testChainedComparison9() {
    doInspectionTest(PyChainedComparisonsInspection.class, "Simplify chained comparison", true, true);
  }

  // PY-1362, PY-2585
  public void testStatementEffect() {
    doInspectionTest(PyStatementEffectInspection.class, PyPsiBundle.message("QFIX.statement.effect"), true, true);
  }

  // PY-1265
  public void testStatementEffectIntroduceVariable() {
    doInspectionTest(PyStatementEffectInspection.class, PyPsiBundle.message("QFIX.introduce.variable"), true, true);
  }

  // PY-2092
  public void testUnresolvedRefCreateFunction() {
    doInspectionTest(PyUnresolvedReferencesInspection.class,
                     PyPsiBundle.message("QFIX.NAME.unresolved.reference.create.function", "ref"), true, true);
  }

  public void testUnresolvedRefCreateAsyncFunction() {
    runWithLanguageLevel(LanguageLevel.getLatest(),
                         () -> doInspectionTest(PyUnresolvedReferencesInspection.class,
                                                PyPsiBundle.message("QFIX.NAME.unresolved.reference.create.function", "ref"), true, true));
  }

  public void testUnresolvedRefNoCreateFunction() {
    myFixture.enableInspections(PyUnresolvedReferencesInspection.class);
    myFixture.configureByFile("UnresolvedRefNoCreateFunction.py");
    myFixture.checkHighlighting(true, false, false);
    final IntentionAction intentionAction = myFixture.getAvailableIntention(PyPsiBundle.message("QFIX.NAME.unresolved.reference.create.function", "ref"));
    assertNull(intentionAction);
  }

  public void testReplaceNotEqOperator() {
    doInspectionTest(PyCompatibilityInspection.class, PyPsiBundle.message("INTN.replace.noteq.operator"), true, true);
  }

  public void testListCreation() {
    doInspectionTest(PyListCreationInspection.class, PyPsiBundle.message("QFIX.list.creation"), true, true);
  }

  // PY-16194
  public void testListCreationOnlyConsecutiveAppends() {
    doInspectionTest(PyListCreationInspection.class, PyPsiBundle.message("QFIX.list.creation"), true, true);
  }

  // PY-1445
  public void testConvertSingleQuotedDocstring() {
    getIndentOptions().INDENT_SIZE = 2;
    doInspectionTest(PySingleQuotedDocstringInspection.class, PyPsiBundle.message("QFIX.convert.single.quoted.docstring"), true, true);
  }

  // PY-8926
  public void testConvertSingleQuotedDocstringEscape() {
    getIndentOptions().INDENT_SIZE = 2;
    doInspectionTest(PySingleQuotedDocstringInspection.class, PyPsiBundle.message("QFIX.convert.single.quoted.docstring"), true, true);
  }

  // PY-3127
  public void testDefaultArgument() {
    doInspectionTest(PyDefaultArgumentInspection.class, PyPsiBundle.message("QFIX.default.argument"), true, true);
  }

  public void testDefaultArgumentEmptyList() {
    doInspectionTest(PyDefaultArgumentInspection.class, PyPsiBundle.message("QFIX.default.argument"), true, true);
  }

  // PY-17392
  public void testDefaultArgumentCommentsInsideParameters() {
    doInspectionTest(PyDefaultArgumentInspection.class, PyPsiBundle.message("QFIX.default.argument"), true, true);
  }

  // PY-3125
  public void testArgumentEqualDefault() {
    doInspectionTest(PyArgumentEqualDefaultInspection.class, PyPsiBundle.message("QFIX.remove.argument.equal.default"), true, true);
  }

  // PY-3315
  public void testAddCallSuper() {
    doInspectionTest(PyMissingConstructorInspection.class, PyPsiBundle.message("QFIX.add.super"), true, true);
  }

  // PY-4017
  public void testAddCallSuper1() {
    doInspectionTest(PyMissingConstructorInspection.class, PyPsiBundle.message("QFIX.add.super"), true, true);
  }

  // PY-8654
  public void testAddCallSuperPass() {
    doInspectionTest(PyMissingConstructorInspection.class, PyPsiBundle.message("QFIX.add.super"), true, true);
  }

  // PY-15867
  public void testAddCallSuperOptionalAndRequiredParamsNameCollision() {
    doInspectionTest(PyMissingConstructorInspection.class, PyPsiBundle.message("QFIX.add.super"), true, true);
  }

  // PY-15927
  public void testAddCallSuperConflictingTupleParam() {
    doInspectionTest(PyMissingConstructorInspection.class, PyPsiBundle.message("QFIX.add.super"), true, true);
  }

  // PY-16036
  public void testAddCallSuperSelfNamePreserved() {
    doInspectionTest(PyMissingConstructorInspection.class, PyPsiBundle.message("QFIX.add.super"), true, true);
  }

  // PY-16420
  public void testAddCallSuperRepeatedOptionalParamsPassedToSuperConstructor() {
    doInspectionTest(PyMissingConstructorInspection.class, PyPsiBundle.message("QFIX.add.super"), true, true);
  }

  // PY-16420
  public void testAddCallSuperRepeatedOptionalTupleParamsPassedToSuperConstructor() {
    doInspectionTest(PyMissingConstructorInspection.class, PyPsiBundle.message("QFIX.add.super"), true, true);
  }

  // PY-16289
  public void testAddCallSuperCommentAfterColonPreserved() {
    doInspectionTest(PyMissingConstructorInspection.class, PyPsiBundle.message("QFIX.add.super"), true, true);
  }

  // PY-16348
  public void testAddCallSuperCommentsInFunctionBodyPreserved() {
    doInspectionTest(PyMissingConstructorInspection.class, PyPsiBundle.message("QFIX.add.super"), true, true);
  }

  // PY-491, PY-13297
  public void testAddEncoding() {
    doInspectionTest(PyMandatoryEncodingInspection.class, PyPsiBundle.message("QFIX.add.encoding"), true, true);
  }

  // PY-13297
  public void testAddEncodingAtLastLine() {
    doInspectionTest(PyMandatoryEncodingInspection.class, PyPsiBundle.message("QFIX.add.encoding"), true, true);
  }

  // PY-3348
  public void testRemoveDecorator() {
    doInspectionTest(PyDecoratorInspection.class, PyPsiBundle.message("QFIX.remove.decorator"), true, true);
  }

  public void testAddParameter() {
    doInspectionTest(PyUnresolvedReferencesInspection.class,
                     PyPsiBundle.message("QFIX.unresolved.reference.add.param", "test"), true, true);
  }

  // PY-6595
  public void testRenameUnresolvedReference() {
    doInspectionTest(PyUnresolvedReferencesInspection.class, PyPsiBundle.message("QFIX.rename.unresolved.reference"), true, true);
  }

  // PY-3120
  public void testSetFunctionToLiteral() {
    runWithLanguageLevel(LanguageLevel.PYTHON27, () -> doInspectionTest(PySetFunctionToLiteralInspection.class, PyPsiBundle.message("QFIX.replace.function.set.with.literal"), true, true));
  }

  // PY-3394
  public void testDocstringParams() {
    getIndentOptions().INDENT_SIZE = 2;
    runWithDocStringFormat(DocStringFormat.REST,
                           () -> doInspectionTest(PyIncorrectDocstringInspection.class, PyPsiBundle.message("QFIX.docstring.add.parameter", "b"), true, true));
  }

  public void testDocstringParams1() {
    getIndentOptions().INDENT_SIZE = 2;
    runWithDocStringFormat(DocStringFormat.REST,
                           () -> doInspectionTest(PyIncorrectDocstringInspection.class, PyPsiBundle.message("QFIX.docstring.remove.parameter", "c"), true, true));
  }

  // PY-4964
  public void testDocstringParams2() {
    runWithDocStringFormat(DocStringFormat.REST,
                           () -> doInspectionTest(PyIncorrectDocstringInspection.class, PyPsiBundle.message("QFIX.docstring.add.parameter", "ham"), true, true));
  }

  // PY-9795
  public void testGoogleDocStringAddParam() {
    runWithDocStringFormat(DocStringFormat.GOOGLE,
                           () -> doInspectionTest(PyIncorrectDocstringInspection.class, PyPsiBundle.message("QFIX.docstring.add.parameter", "b"), true, true));
  }

  // PY-9795
  public void testGoogleDocStringRemoveParam() {
    runWithDocStringFormat(DocStringFormat.GOOGLE,
                           () -> doInspectionTest(PyIncorrectDocstringInspection.class, PyPsiBundle.message("QFIX.docstring.remove.parameter", "c"), true, true));
  }

  // PY-9795
  public void testGoogleDocStringRemoveParamWithSection() {
    runWithDocStringFormat(DocStringFormat.GOOGLE,
                           () -> doInspectionTest(PyIncorrectDocstringInspection.class, PyPsiBundle.message("QFIX.docstring.remove.parameter", "c"), true, true));
  }

  // PY-16761
  public void testGoogleDocStringRemovePositionalVararg() {
    runWithDocStringFormat(DocStringFormat.GOOGLE,
                           () -> doInspectionTest(PyIncorrectDocstringInspection.class, PyPsiBundle.message("QFIX.docstring.remove.parameter", "args"), true, true));
  }

  // PY-16761
  public void testGoogleDocStringRemoveKeywordVararg() {
    runWithDocStringFormat(DocStringFormat.GOOGLE, () -> doInspectionTest(PyIncorrectDocstringInspection.class, PyPsiBundle.message("QFIX.docstring.remove.parameter", "kwargs"), true, true));
  }

  // PY-16908
  public void testNumpyDocStringRemoveFirstOfCombinedParams() {
    runWithDocStringFormat(DocStringFormat.NUMPY,
                           () -> doInspectionTest(PyIncorrectDocstringInspection.class, PyPsiBundle.message("QFIX.docstring.remove.parameter", "x"), true, true));
  }

  // PY-16908
  public void testNumpyDocStringRemoveMidOfCombinedParams() {
    runWithDocStringFormat(DocStringFormat.NUMPY,
                           () -> doInspectionTest(PyIncorrectDocstringInspection.class, PyPsiBundle.message("QFIX.docstring.remove.parameter", "y"), true, true));
  }
  
  // PY-16908
  public void testNumpyDocStringRemoveLastOfCombinedParams() {
    runWithDocStringFormat(DocStringFormat.NUMPY,
                           () -> doInspectionTest(PyIncorrectDocstringInspection.class, PyPsiBundle.message("QFIX.docstring.remove.parameter", "z"), true, true));
  }

  // PY-16908
  public void testNumpyDocStringRemoveCombinedVarargParam() {
    runWithDocStringFormat(DocStringFormat.NUMPY,
                           () -> doInspectionTest(PyIncorrectDocstringInspection.class, PyPsiBundle.message("QFIX.docstring.remove.parameter", "args"), true, true));
  }

  public void testUnnecessaryBackslash() {
    String[] testFiles = {"UnnecessaryBackslash.py"};
    myFixture.enableInspections(PyUnnecessaryBackslashInspection.class);
    myFixture.configureByFiles(testFiles);
    myFixture.checkHighlighting(true, false, true);
    IntentionAction intentionAction = myFixture.getAvailableIntention(PyPsiBundle.message("QFIX.remove.unnecessary.backslash"));
    assertNotNull(intentionAction);
    myFixture.launchAction(intentionAction);
    myFixture.checkResultByFile(graftBeforeExt(testFiles[0], "_after"));
  }

  // PY-3051
  public void testUnresolvedRefTrueFalse() {
    doInspectionTest(PyUnresolvedReferencesInspection.class, PyPsiBundle.message("QFIX.replace.with.true.or.false", "True"), true, true);
  }

  public void testUnnecessaryBackslashInArgumentList() {
    String[] testFiles = {"UnnecessaryBackslashInArguments.py"};
    myFixture.enableInspections(PyUnnecessaryBackslashInspection.class);
    myFixture.configureByFiles(testFiles);
    myFixture.checkHighlighting(true, false, true);
    IntentionAction intentionAction = myFixture.getAvailableIntention(PyPsiBundle.message("QFIX.remove.unnecessary.backslash"));
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
    final IntentionAction intentionAction = myFixture.getAvailableIntention(PyBundle.message("QFIX.NAME.rename.element"));
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
    final IntentionAction intentionAction = myFixture.getAvailableIntention(PyBundle.message("QFIX.NAME.rename.element"));
    assertNotNull(intentionAction);
    myFixture.launchAction(intentionAction);
    myFixture.checkResultByFile(graftBeforeExt(fileName, "_after"));
  }

  public void testIgnoreShadowingBuiltins() {
    myFixture.configureByFile("IgnoreShadowingBuiltins.py");
    myFixture.enableInspections(PyShadowingBuiltinsInspection.class);
    final IntentionAction intentionAction = myFixture.getAvailableIntention("Ignore shadowed built-in name \"open\"");
    assertNotNull(intentionAction);
    myFixture.launchAction(intentionAction);
    myFixture.checkHighlighting(true, false, true);
  }

  public void testImplementAbstractProperty() {
    doInspectionTest("ImplementAbstractProperty.py", PyAbstractClassInspection.class, PyBundle.message("QFIX.NAME.implement.methods"),
                     true, true);
  }

  public void testImplementAbstractProperty1() {
    doInspectionTest("ImplementAbstractProperty.py", PyAbstractClassInspection.class, PyBundle.message("QFIX.NAME.implement.methods"),
                     true, true);
  }

  public void testImplementAbstractOrder() {
    doInspectionTest("ImplementAbstractOrder.py",
                     PyAbstractClassInspection.class,
                     PyBundle.message("QFIX.NAME.implement.methods"),
                     true,
                     true);
  }

  public void testRemovingUnderscoresInNumericLiterals() {
    myFixture.configureByText(PythonFileType.INSTANCE, "1_0_0");

    final IntentionAction action = myFixture.findSingleIntention(PyPsiBundle.message("QFIX.NAME.remove.underscores.in.numeric"));
    myFixture.launchAction(action);

    myFixture.checkResult("100");
  }

  // PY-20452
  public void testRemoveRedundantEscapeInOnePartRegExp() {
    myFixture.enableInspections(new RegExpRedundantEscapeInspection());
    myFixture.configureByText(PythonFileType.INSTANCE, "import re\nre.compile(\"(?P<foo>((<caret>\\/(?P<bar>.+))?))\")");

    final List<IntentionAction> quickFixes = myFixture.getAllQuickFixes();
    assertEquals(1, quickFixes.size());

    final IntentionAction removeRedundantEscapeFix = quickFixes.get(0);
    assertEquals("Remove redundant escape", removeRedundantEscapeFix.getText());

    myFixture.launchAction(removeRedundantEscapeFix);
    myFixture.checkResult("import re\nre.compile(\"(?P<foo>((/(?P<bar>.+))?))\")");
  }

  // PY-20452
  public void testRemoveRedundantEscapeInMultiPartRegExp() {
    myFixture.enableInspections(new RegExpRedundantEscapeInspection());
    myFixture.configureByText(PythonFileType.INSTANCE, """
      import re
      re.compile("(?P<foo>"
                 "((<caret>\\/(?P<bar>.+))?))")""");

    final List<IntentionAction> quickFixes = myFixture.getAllQuickFixes();
    assertEquals(1, quickFixes.size());

    final IntentionAction removeRedundantEscapeFix = quickFixes.get(0);
    assertEquals("Remove redundant escape", removeRedundantEscapeFix.getText());

    myFixture.launchAction(removeRedundantEscapeFix);
    myFixture.checkResult("""
                            import re
                            re.compile("(?P<foo>"
                                       "((/(?P<bar>.+))?))")""");
  }

  // PY-8174
  public void testChangeSignatureKeywordAndPositionalParameters() {
    doInspectionTest(PyArgumentListInspection.class, "<html>Change the signature of f(x, foo)</html>", true, true);
  }

  // PY-8174
  public void testChangeSignatureAddKeywordOnlyParameter() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON34,
      () -> doInspectionTest(PyArgumentListInspection.class, "<html>Change the signature of func(x, *args, foo)</html>", true, true)
    );
  }

  // PY-8174
  public void testChangeSignatureNewParametersNames() {
    doInspectionTest(PyArgumentListInspection.class, "<html>Change the signature of func(i1)</html>", true, true);
  }

  // PY-53671
  public void testChangeSignatureOfExportedBoundMethod() {
    runWithLanguageLevel(LanguageLevel.getLatest(), () -> {
      doMultiFilesInspectionTest(PyArgumentListInspection.class, "<html>Change the signature of method(self, a, b)</html>", "mod.py");
    });
  }

  // PY-8174
  public void testChangeSignatureParametersDefaultValues() {
    doInspectionTest(PyArgumentListInspection.class, "<html>Change the signature of func()</html>", true, true);
  }

  public void testAddKwargsToNewMethodIncompatibleWithInit() {
    doInspectionTest(PyInitNewSignatureInspection.class, "<html>Change the signature of __new__(cls)</html>", true, true);
  }

  public void testAddKwargsToIncompatibleOverridingMethod() {
    doInspectionTest(PyMethodOverridingInspection.class, "<html>Change the signature of m(self)</html>", true, true);
  }

  // PY-30789
  public void testSetImportedABCMetaAsMetaclassPy2() {
    doInspectionTest("PyAbstractClassInspection/quickFix/SetImportedABCMetaAsMetaclassPy2/main.py",
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
        final List<String> intentionNames = ContainerUtil.map(myFixture.getAvailableIntentions(), IntentionAction::getText);
        throw new AssertionError("Quickfix starting with \"" + quickFixName + "\" is not available. " +
                                 "Available intentions:\n" + StringUtil.join(intentionNames, "\n"));
      }
      if (intentionActions.size() > 1) {
        throw new AssertionError("There are more than one quickfix with the name \"" + quickFixName + "\"");
      }
      if (applyFix) {
        myFixture.launchAction(intentionActions.get(0));
        NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
        myFixture.checkResultByFile(graftBeforeExt(testFiles[0], "_after"), true);
      }
    }
    else {
      assertEmpty("Quick fix \"" + quickFixName + "\" should not be available", intentionActions);
    }
  }

  private void doMultiFilesInspectionTest(@NotNull Class<? extends LocalInspectionTool> inspectionClass,
                                          @NotNull String intentionStr,
                                          @NotNull String modifiedFile) {
    myFixture.enableInspections(inspectionClass);
    myFixture.copyDirectoryToProject(getTestName(true), "");
    myFixture.configureFromTempProjectFile(getTestName(true) + ".py");
    myFixture.checkHighlighting(true, false, false);
    final IntentionAction intentionAction = myFixture.findSingleIntention(intentionStr);
    assertNotNull(intentionAction);
    myFixture.launchAction(intentionAction);
    final String expectedFile = getTestName(true) + "/" + graftBeforeExt(modifiedFile, "_after");
    myFixture.checkResultByFile(modifiedFile, expectedFile, true);
  }

  // Turns "name.ext" to "name_insertion.ext"

  @NonNls
  private static String graftBeforeExt(String name, String insertion) {
    int dotpos = name.indexOf('.');
    if (dotpos < 0) dotpos = name.length();
    return name.substring(0, dotpos) + insertion + name.substring(dotpos);
  }
}
