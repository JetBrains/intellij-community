// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.refactoring;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.refactoring.inline.PyInlineLocalHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * @author Dennis.Ushakov
 */
public class PyInlineLocalTest extends PyTestCase {
  private void doTest() {
    doTest(null);
  }

  private void doTest(@Nullable String expectedError) {
    final String name = getTestName(true);
    myFixture.configureByFile("/refactoring/inlinelocal/" + name + ".before.py");
    if (!performRefactoring(expectedError)) return;
    myFixture.checkResultByFile("/refactoring/inlinelocal/" + name + ".after.py");
  }

  private boolean performRefactoring(@Nullable String expectedError) {
    try {
      final PsiElement element = TargetElementUtil.findTargetElement(myFixture.getEditor(),
                                                                     TargetElementUtil.getInstance().getReferenceSearchFlags());
      final PyInlineLocalHandler handler = PyInlineLocalHandler.getInstance();
      handler.inlineElement(myFixture.getProject(), myFixture.getEditor(), element);
      if (expectedError != null) fail("expected error: '" + expectedError + "', got none");
    }
    catch (Exception e) {
      if (!Objects.equals(e.getMessage(), expectedError)) {
        e.printStackTrace();
      }
      assertEquals(expectedError, e.getMessage());
      return false;
    }
    return true;
  }

  public void testSimple() {
    doTest();
  }

  public void testPriority() {
    doTest();
  }

  public void testNoDominator() {
    doTest("Cannot perform refactoring.\nCannot find a single definition to inline");
  }

  public void testDoubleDefinition() {
    doTest("Cannot perform refactoring.\nAnother variable 'foo' definition is used together with inlined one");
  }

  public void testMultiple() {
    doTest();
  }

  public void testPy994() {
    doTest();
  }

  public void testPy1585() {
    doTest();
  }

  public void testPy5832() {
    doTest();
  }

  // PY-12401
  public void testComment() {
    doTest();
  }

  // PY-13114
  public void testReferenceInParenthesis() {
    doTest();
  }

  // PY-13114
  public void testMethodCallInlinedAsQualifier() {
    doTest();
  }

  // PY-12409
  public void testResultExceedsRightMargin() {
    final CodeStyleSettings settings = getCodeStyleSettings();
    final CommonCodeStyleSettings commonSettings = settings.getCommonSettings(PythonLanguage.getInstance());

    final int oldRightMargin = settings.getRightMargin(PythonLanguage.getInstance());
    final boolean oldWrapLongLines = commonSettings.WRAP_LONG_LINES;

    settings.setRightMargin(PythonLanguage.getInstance(), 80);
    commonSettings.WRAP_LONG_LINES = true;
    try {
      doTest();
    }
    finally {
      commonSettings.WRAP_LONG_LINES = oldWrapLongLines;
      settings.setRightMargin(PythonLanguage.getInstance(), oldRightMargin);
    }
  }

  public void testOperatorPrecedence() {
    checkOperatorPrecedence("x = 10 ** 2", "power");
    checkOperatorPrecedence("x = 10 * 2", "multiplication");
    checkOperatorPrecedence("x = 10 / 2", "division");
    checkOperatorPrecedence("x = 10 + 2", "addition");
    checkOperatorPrecedence("x = 10 - 2", "subtraction");
    checkOperatorPrecedence("x = 10 << 2", "bitwiseShift");
    checkOperatorPrecedence("x = 10 & 2", "bitwiseAnd");
    checkOperatorPrecedence("x = 10 ^ 2", "bitwiseXor");
    checkOperatorPrecedence("x = 10 | 2", "bitwiseOr");
    checkOperatorPrecedence("x = 10 < 2", "comparison");
    checkOperatorPrecedence("x = not 10", "booleanNot");
    checkOperatorPrecedence("x = 10 and 2", "booleanAnd");
    checkOperatorPrecedence("x = 10 or 2", "booleanOr");
    checkOperatorPrecedence("x = 10 if True else 2", "conditional");
  }

  // PY-15390
  public void testMatMulPrecedence() {
    checkOperatorPrecedence("x = y @ z", "matrixMultiplication");
  }

  // PY-40797
  public void testStringToFString() {
    doTest();
  }

  // PY-40797
  public void testFStringToFString() {
    doTest();
  }

  // PY-40797
  public void testExpressionWithStringsToFString() {
    doTest();
  }

  // PY-40797
  public void testExpressionWithStringsToExpressionInFString() {
    doTest();
  }

  // PY-40797
  public void testStringContainingBackslashesToFString() {
    doTest();
  }

  // PY-40797
  public void testStringContainingQuotesToFString() {
    doTest();
  }

  // PY-40797
  public void testStringToFStringInFString() {
    doTest();
  }

  // PY-40797
  public void testStringToFStringSeveralOccurrences() {
    doTest();
  }

  // PY-40797
  public void testStringToFStringEscapedVariableOccurrence() {
    doTest();
  }

  // PY-40797
  public void testRawStringToFString() {
    doTest();
  }

  // PY-40797
  public void testStringToFStringsDifferentQuotes() {
    doTest();
  }

  // PY-40797
  public void testStringWithQuoteToFStringTripleQuotes() {
    doTest();
  }

  // PY-40797
  public void testMultilineStringToTripleQuotedExpressionFString() {
    doTest();
  }

  // PY-40797
  public void testMultilineStringToTripleQuotedFString() {
    doTest();
  }

  // PY-40797
  public void testExpressionWithStringToFStringsDifferentQuotes() {
    doTest();
  }


  // PY-40797
  public void testStringToFStringAndOtherPlaces() {
    doTest();
  }

  // PY-40797
  public void testStringToFStringFormatPart() {
    doTest();
  }

  // PY-40797
  public void testStringToFStringTypeConversion() {
    doTest();
  }

  // PY-40797
  public void testStringToFStringRemoveFragmentInNestedFString() {
    doTest();
  }

  // PY-40797
  public void testExpressionWithStringsContainingQuotesToFString() {
    doTest(PyPsiBundle.message("refactoring.inline.can.not.string.with.backslashes.or.quotes.to.f.string"));
  }

  // PY-40797
  public void testExpressionWithStringsToFStringInFString() {
    doTest(PyPsiBundle.message("refactoring.inline.can.not.string.to.nested.f.string"));
  }

  // PY-40797
  public void testStringWithQuotesToFStringInFString() {
    doTest(PyPsiBundle.message("refactoring.inline.can.not.string.with.backslashes.or.quotes.to.f.string"));
  }

  // PY-40797
  public void testMultilineStringToFString() {
    doTest(PyPsiBundle.message("refactoring.inline.can.not.multiline.string.to.f.string"));
  }

  // PY-40797
  public void testMultilineStringToExpressionFString() {
    doTest(PyPsiBundle.message("refactoring.inline.can.not.multiline.string.to.f.string"));
  }

  // PY-40797
  public void testStringWithBackslashToFStringNotAvailable() {
    doTest(PyPsiBundle.message("refactoring.inline.can.not.string.with.backslashes.or.quotes.to.f.string"));
  }

  private void checkOperatorPrecedence(@NotNull final String firstLine, @NotNull String resultPrefix) {
    myFixture.configureByFile("/refactoring/inlinelocal/operatorPrecedence/template.py");
    WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> myFixture.getEditor().getDocument().insertString(0, firstLine + "\n"));
    PsiDocumentManager.getInstance(myFixture.getProject()).commitAllDocuments();
    performRefactoring(null);
    myFixture.checkResultByFile("/refactoring/inlinelocal/operatorPrecedence/" + resultPrefix + ".after.py");
    FileDocumentManager.getInstance().reloadFromDisk(myFixture.getDocument(myFixture.getFile()));
  }
}
