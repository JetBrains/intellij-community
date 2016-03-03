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
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.testFramework.TestDataFile;
import com.intellij.testFramework.TestDataPath;
import com.jetbrains.python.codeInsight.PyCodeInsightSettings;
import com.jetbrains.python.documentation.docstrings.DocStringFormat;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.inspections.*;
import com.jetbrains.python.inspections.unresolvedReference.PyUnresolvedReferencesInspection;
import com.jetbrains.python.psi.LanguageLevel;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author dcheryasov
 */
@TestDataPath("$CONTENT_ROOT/../testData/inspections/")
public class PyQuickFixTest extends PyTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    InspectionProfileImpl.INIT_INSPECTIONS = true;
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
      doInspectionTest(new String[]{"QualifyByImport.py", "QualifyByImportFoo.py"}, PyUnresolvedReferencesInspection.class,
                       PyBundle.message("ACT.qualify.with.module"), true, true);
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
      doInspectionTest(new String[]{"AddToImportFromList.py", "AddToImportFromFoo.py"}, PyUnresolvedReferencesInspection.class,
                       "Import 'foo(a) from AddToImportFromFoo'", true, true);
    }
    finally {
      settings.HIGHLIGHT_UNUSED_IMPORTS = oldHighlightUnused;
    }
  }
  // TODO: add a test for multiple variants of above

  // TODO: add tests for stub indexes-based autoimport of unimported somehow.

  public void testAddSelf() {
    doInspectionTest(PyMethodParametersInspection.class, PyBundle.message("QFIX.add.parameter.self", "self"), true, true);
  }

  public void testReplacePrint() {
    doInspectionTest(PyCompatibilityInspection.class, PyBundle.message("QFIX.statement.effect"), true, true);
  }

  // PY-4556
  public void testAddSelfFunction() {
    doInspectionTest("AddSelfFunction.py", PyUnresolvedReferencesInspection.class,
                     PyBundle.message("QFIX.unresolved.reference", "get_a", "self"), true, true);
  }

  // PY-9721
  public void testAddSelfToClassmethod() {
    doInspectionTest("AddSelfToClassmethod.py", PyUnresolvedReferencesInspection.class,
                     PyBundle.message("QFIX.unresolved.reference", "foo", "cls"), true, true);
  }

  public void testAddCls() {
    doInspectionTest(PyMethodParametersInspection.class, PyBundle.message("QFIX.add.parameter.self", "cls"), true, true);
  }

  public void testRenameToSelf() {
    doInspectionTest(PyMethodParametersInspection.class, PyBundle.message("QFIX.rename.parameter.to.$0", "self"), true, true);
  }

  public void testRemoveTrailingSemicolon() {
    doInspectionTest(PyTrailingSemicolonInspection.class, PyBundle.message("QFIX.remove.trailing.semicolon"), true, true);
  }

  public void testDictCreation() {
    doInspectionTest(PyDictCreationInspection.class, PyBundle.message("QFIX.dict.creation"), true, true);
  }

  // PY-6283
  public void testDictCreationTuple() {
    doInspectionTest(PyDictCreationInspection.class, PyBundle.message("QFIX.dict.creation"), true, true);
  }

  // PY-7318
  public void testDictCreationDuplicate() {
    doInspectionTest(PyDictCreationInspection.class, PyBundle.message("QFIX.dict.creation"), true, true);
  }

  public void testTransformClassicClass() {
    doInspectionTest(PyClassicStyleClassInspection.class, PyBundle.message("QFIX.classic.class.transform"), true, true);
  }

  public void testAddGlobalStatement() {
    doInspectionTest(PyUnboundLocalVariableInspection.class, PyBundle.message("QFIX.add.global"), true, true);
  }

  public void testAddGlobalExistingStatement() {
    doInspectionTest(PyUnboundLocalVariableInspection.class, PyBundle.message("QFIX.add.global"), true, true);
  }

  public void testSimplifyBooleanCheck() {
    doInspectionTest(PySimplifyBooleanCheckInspection.class, PyBundle.message("QFIX.simplify.$0", "b"), true, true);
  }

  public void testMoveFromFutureImport() {
    doInspectionTest(PyFromFutureImportInspection.class, PyBundle.message("QFIX.move.from.future.import"), true, true);
  }

  // PY-10080
  public void testMoveFromFutureImportDocString() {
    doInspectionTest(PyFromFutureImportInspection.class, PyBundle.message("QFIX.move.from.future.import"), true, true);
  }

  public void testComparisonWithNone() {
    doInspectionTest(PyComparisonWithNoneInspection.class, PyBundle.message("QFIX.replace.equality"), true, true);
  }

  public void testAddClassFix() {
    doInspectionTest("AddClass.py", PyUnresolvedReferencesInspection.class, "Create class 'Xyzzy'", true, true);
  }

  // PY-1602
  public void testAddFunctionToModule() {
    doInspectionTest(
      "AddFunctionToModule.py",
      PyUnresolvedReferencesInspection.class,
      PyBundle.message("QFIX.NAME.add.function.$0.to.module.$1", "frob", "AddFunctionToModule.py"),
      true, true
    );
  }

  // PY-1470
  public void testRedundantParentheses() {
    String[] testFiles = new String[]{"RedundantParentheses.py"};
    myFixture.enableInspections(PyRedundantParenthesesInspection.class);
    myFixture.configureByFiles(testFiles);
    myFixture.checkHighlighting(true, false, true);
    final IntentionAction intentionAction = myFixture.findSingleIntention(PyBundle.message("QFIX.redundant.parentheses"));
    assertNotNull(intentionAction);
    myFixture.launchAction(intentionAction);
    myFixture.checkResultByFile(graftBeforeExt(testFiles[0], "_after"));
  }

  // PY-3095
  public void testRedundantParenthesesBoolean() {
    doInspectionTest(PyRedundantParenthesesInspection.class, PyBundle.message("QFIX.redundant.parentheses"), true, true);
  }

  // PY-3239
  public void testRedundantParenthesesMore() {
    doInspectionTest(PyRedundantParenthesesInspection.class, PyBundle.message("QFIX.redundant.parentheses"), true, true);
  }

  // PY-12679
  public void testRedundantParenthesesParenthesizedExpression() {
    doInspectionTest(PyRedundantParenthesesInspection.class, PyBundle.message("QFIX.redundant.parentheses"), true, true);
  }

  // PY-15506
  public void testEmptyListOfBaseClasses() {
    doInspectionTest(PyRedundantParenthesesInspection.class, PyBundle.message("QFIX.redundant.parentheses"), true, true);
  }

  // PY-18203
  public void testRedundantParenthesesInTuples() {
    doInspectionTest(PyRedundantParenthesesInspection.class, PyBundle.message("QFIX.redundant.parentheses"), true, true);
  }

  // PY-1020
  public void testChainedComparisons() {
    doInspectionTest(PyChainedComparisonsInspection.class, PyBundle.message("QFIX.chained.comparison"), true, true);
  }

  // PY-3126
  public void testChainedComparison1() {
    doInspectionTest(PyChainedComparisonsInspection.class, PyBundle.message("QFIX.chained.comparison"), true, true);
  }

  // PY-3126
  public void testChainedComparison2() {
    doInspectionTest(PyChainedComparisonsInspection.class, PyBundle.message("QFIX.chained.comparison"), true, true);
  }

  // PY-3126
  public void testChainedComparison3() {
    doInspectionTest(PyChainedComparisonsInspection.class, PyBundle.message("QFIX.chained.comparison"), true, true);
  }

  // PY-5623
  public void testChainedComparison4() {
    doInspectionTest(PyChainedComparisonsInspection.class, PyBundle.message("QFIX.chained.comparison"), true, true);
  }

  // PY-6467
  public void testChainedComparison5() {
    doInspectionTest(PyChainedComparisonsInspection.class, PyBundle.message("QFIX.chained.comparison"), true, true);
  }

  // PY-14002
  public void testChainedComparisonWithCommonBinaryExpression() {
    doInspectionTest(PyChainedComparisonsInspection.class, PyBundle.message("QFIX.chained.comparison"), true, true);
  }

  // PY-1362, PY-2585
  public void testStatementEffect() {
    doInspectionTest(PyStatementEffectInspection.class, PyBundle.message("QFIX.statement.effect"), true, true);
  }

  // PY-1265
  public void testStatementEffectIntroduceVariable() {
    doInspectionTest(PyStatementEffectInspection.class, PyBundle.message("QFIX.statement.effect.introduce.variable"), true, true);
  }

  // PY-2083
  public void testUnresolvedWith() {
    runWithLanguageLevel(LanguageLevel.PYTHON25, new Runnable() {
      @Override
      public void run() {
        doInspectionTest(PyUnresolvedReferencesInspection.class, PyBundle.message("QFIX.unresolved.reference.add.future"), true, true);
      }
    });
  }

  // PY-2092
  public void testUnresolvedRefCreateFunction() {
    doInspectionTest(PyUnresolvedReferencesInspection.class,
                     PyBundle.message("QFIX.unresolved.reference.create.function.$0", "ref"), true, true);
  }

  public void testUnresolvedRefNoCreateFunction() {
    myFixture.enableInspections(PyUnresolvedReferencesInspection.class);
    myFixture.configureByFile("UnresolvedRefNoCreateFunction.py");
    myFixture.checkHighlighting(true, false, false);
    final IntentionAction intentionAction = myFixture.getAvailableIntention(PyBundle.message("QFIX.unresolved.reference.create.function.$0", "ref"));
    assertNull(intentionAction);
  }

  public void testReplaceNotEqOperator() {
    doInspectionTest(PyCompatibilityInspection.class, PyBundle.message("INTN.replace.noteq.operator"), true, true);
  }

  public void testListCreation() {
    doInspectionTest(PyListCreationInspection.class, PyBundle.message("QFIX.list.creation"), true, true);
  }

  // PY-1445
  public void testConvertSingleQuotedDocstring() {
    getIndentOptions().INDENT_SIZE = 2;
    doInspectionTest(PySingleQuotedDocstringInspection.class, PyBundle.message("QFIX.convert.single.quoted.docstring"), true, true);
  }

  // PY-8926
  public void testConvertSingleQuotedDocstringEscape() {
    getIndentOptions().INDENT_SIZE = 2;
    doInspectionTest(PySingleQuotedDocstringInspection.class, PyBundle.message("QFIX.convert.single.quoted.docstring"), true, true);
  }

  // PY-3127
  public void testDefaultArgument() {
    doInspectionTest(PyDefaultArgumentInspection.class, PyBundle.message("QFIX.default.argument"), true, true);
  }

  public void testDefaultArgumentEmptyList() {
    doInspectionTest(PyDefaultArgumentInspection.class, PyBundle.message("QFIX.default.argument"), true, true);
  }

  // PY-17392
  public void testDefaultArgumentCommentsInsideParameters() {
    doInspectionTest(PyDefaultArgumentInspection.class, PyBundle.message("QFIX.default.argument"), true, true);
  }

  // PY-3125
  public void testArgumentEqualDefault() {
    doInspectionTest(PyArgumentEqualDefaultInspection.class, PyBundle.message("QFIX.remove.argument.equal.default"), true, true);
  }

  // PY-3315
  public void testAddCallSuper() {
    doInspectionTest(PyMissingConstructorInspection.class, PyBundle.message("QFIX.add.super"), true, true);
  }

  // PY-4017
  public void testAddCallSuper1() {
    doInspectionTest(PyMissingConstructorInspection.class, PyBundle.message("QFIX.add.super"), true, true);
  }

  // PY-8654
  public void testAddCallSuperPass() {
    doInspectionTest(PyMissingConstructorInspection.class, PyBundle.message("QFIX.add.super"), true, true);
  }

  // PY-15867
  public void testAddCallSuperOptionalAndRequiredParamsNameCollision() {
    doInspectionTest(PyMissingConstructorInspection.class, PyBundle.message("QFIX.add.super"), true, true);
  }

  // PY-15927
  public void testAddCallSuperConflictingTupleParam() {
    doInspectionTest(PyMissingConstructorInspection.class, PyBundle.message("QFIX.add.super"), true, true);
  }

  // PY-16036
  public void testAddCallSuperSelfNamePreserved() {
    doInspectionTest(PyMissingConstructorInspection.class, PyBundle.message("QFIX.add.super"), true, true);
  }

  // PY-16420
  public void testAddCallSuperRepeatedOptionalParamsPassedToSuperConstructor() {
    doInspectionTest(PyMissingConstructorInspection.class, PyBundle.message("QFIX.add.super"), true, true);
  }

  // PY-16420
  public void testAddCallSuperRepeatedOptionalTupleParamsPassedToSuperConstructor() {
    doInspectionTest(PyMissingConstructorInspection.class, PyBundle.message("QFIX.add.super"), true, true);
  }

  // PY-16289
  public void testAddCallSuperCommentAfterColonPreserved() {
    doInspectionTest(PyMissingConstructorInspection.class, PyBundle.message("QFIX.add.super"), true, true);
  }

  // PY-16348
  public void testAddCallSuperCommentsInFunctionBodyPreserved() {
    doInspectionTest(PyMissingConstructorInspection.class, PyBundle.message("QFIX.add.super"), true, true);
  }

  // PY-491, PY-13297
  public void testAddEncoding() {
    doInspectionTest(PyMandatoryEncodingInspection.class, PyBundle.message("QFIX.add.encoding"), true, true);
  }

  // PY-13297
  public void testAddEncodingAtLastLine() {
    doInspectionTest(PyMandatoryEncodingInspection.class, PyBundle.message("QFIX.add.encoding"), true, true);
  }

  // PY-3348
  public void testRemoveDecorator() {
    doInspectionTest(PyDecoratorInspection.class, PyBundle.message("QFIX.remove.decorator"), true, true);
  }

  public void testAddParameter() {
    doInspectionTest(PyUnresolvedReferencesInspection.class,
                     PyBundle.message("QFIX.unresolved.reference.add.param.$0", "test"), true, true);
  }

  // PY-6595
  public void testRenameUnresolvedReference() {
    doInspectionTest(PyUnresolvedReferencesInspection.class, PyBundle.message("QFIX.rename.unresolved.reference"), true, true);
  }

  // PY-3120
  public void testSetFunctionToLiteral() {
    runWithLanguageLevel(LanguageLevel.PYTHON27, new Runnable() {
      @Override
      public void run() {
        doInspectionTest(PySetFunctionToLiteralInspection.class, PyBundle.message("QFIX.replace.function.set.with.literal"), true, true);
      }
    });
  }

  public void testDictComprehensionToCall() {
    doInspectionTest(PyCompatibilityInspection.class, PyBundle.message("INTN.convert.dict.comp.to"), true, true);
  }

  // PY-3394
  public void testDocstringParams() {
    getIndentOptions().INDENT_SIZE = 2;
    runWithDocStringFormat(DocStringFormat.EPYTEXT, new Runnable() {
      public void run() {
        doInspectionTest(PyIncorrectDocstringInspection.class, PyBundle.message("QFIX.docstring.add.$0", "b"), true, true);
      }
    });
  }

  public void testDocstringParams1() {
    getIndentOptions().INDENT_SIZE = 2;
    runWithDocStringFormat(DocStringFormat.EPYTEXT, new Runnable() {
      public void run() {
        doInspectionTest(PyIncorrectDocstringInspection.class, PyBundle.message("QFIX.docstring.remove.$0", "c"), true, true);
      }
    });
  }

  // PY-4964
  public void testDocstringParams2() {
    runWithDocStringFormat(DocStringFormat.EPYTEXT, new Runnable() {
      public void run() {
        doInspectionTest(PyIncorrectDocstringInspection.class, PyBundle.message("QFIX.docstring.add.$0", "ham"), true, true);
      }
    });
  }

  // PY-9795
  public void testGoogleDocStringAddParam() {
    runWithDocStringFormat(DocStringFormat.GOOGLE, new Runnable() {
      @Override
      public void run() {
        doInspectionTest(PyIncorrectDocstringInspection.class, PyBundle.message("QFIX.docstring.add.$0", "b"), true, true);
      }
    });
  }

  // PY-9795
  public void testGoogleDocStringRemoveParam() {
    runWithDocStringFormat(DocStringFormat.GOOGLE, new Runnable() {
      @Override
      public void run() {
        doInspectionTest(PyIncorrectDocstringInspection.class, PyBundle.message("QFIX.docstring.remove.$0", "c"), true, true);
      }
    });
  }

  // PY-9795
  public void testGoogleDocStringRemoveParamWithSection() {
    runWithDocStringFormat(DocStringFormat.GOOGLE, new Runnable() {
      @Override
      public void run() {
        doInspectionTest(PyIncorrectDocstringInspection.class, PyBundle.message("QFIX.docstring.remove.$0", "c"), true, true);
      }
    });
  }

  // PY-16761
  public void testGoogleDocStringRemovePositionalVararg() {
    runWithDocStringFormat(DocStringFormat.GOOGLE, new Runnable() {
      @Override
      public void run() {
        doInspectionTest(PyIncorrectDocstringInspection.class, PyBundle.message("QFIX.docstring.remove.$0", "args"), true, true);
      }
    });
  }

  // PY-16761
  public void testGoogleDocStringRemoveKeywordVararg() {
    runWithDocStringFormat(DocStringFormat.GOOGLE, new Runnable() {
      @Override
      public void run() {
        doInspectionTest(PyIncorrectDocstringInspection.class, PyBundle.message("QFIX.docstring.remove.$0", "kwargs"), true, true);
      }
    });
  }

  // PY-16908
  public void testNumpyDocStringRemoveFirstOfCombinedParams() {
    runWithDocStringFormat(DocStringFormat.NUMPY, new Runnable() {
      @Override
      public void run() {
        doInspectionTest(PyIncorrectDocstringInspection.class, PyBundle.message("QFIX.docstring.remove.$0", "x"), true, true);
      }
    });
  }

  // PY-16908
  public void testNumpyDocStringRemoveMidOfCombinedParams() {
    runWithDocStringFormat(DocStringFormat.NUMPY, new Runnable() {
      @Override
      public void run() {
        doInspectionTest(PyIncorrectDocstringInspection.class, PyBundle.message("QFIX.docstring.remove.$0", "y"), true, true);
      }
    });
  }
  
  // PY-16908
  public void testNumpyDocStringRemoveLastOfCombinedParams() {
    runWithDocStringFormat(DocStringFormat.NUMPY, new Runnable() {
      @Override
      public void run() {
        doInspectionTest(PyIncorrectDocstringInspection.class, PyBundle.message("QFIX.docstring.remove.$0", "z"), true, true);
      }
    });
  }

  // PY-16908
  public void testNumpyDocStringRemoveCombinedVarargParam() {
    runWithDocStringFormat(DocStringFormat.NUMPY, new Runnable() {
      @Override
      public void run() {
        doInspectionTest(PyIncorrectDocstringInspection.class, PyBundle.message("QFIX.docstring.remove.$0", "args"), true, true);
      }
    });
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

  // PY-3051
  public void testUnresolvedRefTrueFalse() {
    doInspectionTest(PyUnresolvedReferencesInspection.class, PyBundle.message("QFIX.unresolved.reference.replace.$0", "True"), true, true);
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

  @Override
  @NonNls
  protected String getTestDataPath() {
    return PythonTestUtil.getTestDataPath() + "/inspections/";
  }

  private void doInspectionTest(@NotNull Class inspectionClass,
                                @NotNull String quickFixName,
                                boolean applyFix,
                                boolean available) {
    doInspectionTest(getTestName(false) + ".py", inspectionClass, quickFixName, applyFix, available);
  }

  protected void doInspectionTest(@TestDataFile @NonNls @NotNull String testFileName,
                                  @NotNull Class inspectionClass,
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
   * @throws Exception
   */
  @SuppressWarnings("Duplicates")
  protected void doInspectionTest(@NonNls @NotNull String[] testFiles,
                                  @NotNull Class inspectionClass,
                                  @NonNls @NotNull String quickFixName,
                                  boolean applyFix,
                                  boolean available) {
    myFixture.enableInspections(inspectionClass);
    myFixture.configureByFiles(testFiles);
    myFixture.checkHighlighting(true, false, false);
    final List<IntentionAction> intentionActions = myFixture.filterAvailableIntentions(quickFixName);
    if (available) {
      if (intentionActions.isEmpty()) {
        throw new AssertionError("Quickfix \"" + quickFixName + "\" is not available");
      }
      if (intentionActions.size() > 1) {
        throw new AssertionError("There are more than one quickfix with the name \"" + quickFixName + "\"");
      }
      if (applyFix) {
        myFixture.launchAction(intentionActions.get(0));
        myFixture.checkResultByFile(graftBeforeExt(testFiles[0], "_after"), true);
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
    return name.substring(0, dotpos) + insertion + name.substring(dotpos, name.length());
  }
}
