package com.intellij.refactoring;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.extractMethod.ExtractMethodProcessor;
import com.intellij.refactoring.extractMethod.PrepareFailedException;
import com.intellij.refactoring.util.duplicates.Match;
import com.intellij.testFramework.LightCodeInsightTestCase;
import com.intellij.util.IncorrectOperationException;
import com.intellij.openapi.editor.Editor;

import java.util.Iterator;
import java.util.List;

public class ExtractMethodTest extends LightCodeInsightTestCase {
  private static final String BASE_PATH = "/refactoring/extractMethod/";

  public void testExitPoints1() throws Exception {
    doExitPointsTest(true);
  }

  public void testExitPoints2() throws Exception {
    doExitPointsTest(false);
  }

  public void testExitPoints3() throws Exception {
    doExitPointsTest(true);
  }

  public void testExitPoints4() throws Exception {
    doExitPointsTest(false);
  }

  public void testExitPointsInsideLoop() throws Exception {
    doExitPointsTest(false);
  }

  public void testExitPoints5() throws Exception { doTest(); }

  public void testBooleanExpression() throws Exception { doTest(); }

  public void testScr6241() throws Exception { doTest(); }

  public void testScr7091() throws Exception { doTest(); }

  public void testScr10464() throws Exception { doTest(); }

  public void testScr9852() throws Exception { doTest(); }

  public void testUseVarAfterTry() throws Exception { doTest(); }

  public void testOneBranchAssignment() throws Exception { doTest(); }

  public void testExtractFromCodeBlock() throws Exception { doTest(); }

  public void testUnusedInitializedVar() throws Exception { doTest(); }

  public void testTryFinally() throws Exception { doTest(); }

  public void testFinally() throws Exception { doTest(); }

  public void testExtractFromAnonymous() throws Exception { doTest(); }

  public void testSCR12245() throws Exception { doTest(); }

  public void testSCR15815() throws Exception { doTest(); }

  public void testSCR27887() throws Exception { doTest(); }
  public void testSCR28427() throws Exception { doTest(); }
  public void testTryFinallyInsideFor() throws Exception { doTest(); }

  public void testExtractFromTryFinally() throws Exception { doTest(); }

  public void testLesyaBug() throws Exception { doTest(); }

  public void testForEach() throws Exception { doTest(); }

  public void testAnonInner() throws Exception { doTest(); }

  public void testExpressionDuplicates() throws Exception { doDuplicatesTest(); }
  public void testCodeDuplicates() throws Exception { doDuplicatesTest(); }
  public void testCodeDuplicates2() throws Exception { doDuplicatesTest(); }
  public void testCodeDuplicates3() throws Exception { doDuplicatesTest(); }
  public void testCodeDuplicates4() throws Exception { doDuplicatesTest(); }
  public void testCodeDuplicates5() throws Exception { doDuplicatesTest(); }
  public void testCodeDuplicatesWithOutputValue() throws Exception { doDuplicatesTest(); }
  public void testCodeDuplicatesWithOutputValue1() throws Exception { doDuplicatesTest(); }
  public void testCodeDuplicatesWithReturn() throws Exception { doDuplicatesTest(); }
  public void testCodeDuplicatesWithReturn2() throws Exception { doDuplicatesTest(); }
  public void testSCR32924() throws Exception { doDuplicatesTest(); }
  public void testFinalOutputVar() throws Exception { doDuplicatesTest(); }

  private void doDuplicatesTest() throws Exception {
    doTest(true);
  }

  public void testExtractFromFinally() throws Exception { doTest(); }


  private void doExitPointsTest(boolean shouldSucceed) throws Exception {
    String fileName = getTestName(false) + ".java";
    configureByFile(BASE_PATH + fileName);
    boolean success = performAction(false, false);
    assertEquals(shouldSucceed, success);
  }

  private void doTest() throws Exception {
    doTest(true);
  }

  private void doTest(boolean duplicates) throws Exception {
    configureByFile(BASE_PATH + getTestName(false) + ".java");
    boolean success = performAction(true, duplicates);
    assertTrue(success);
    checkResultByFile(BASE_PATH + getTestName(false) + "_after.java");
  }

  private boolean performAction(boolean doRefactor, boolean replaceAllDuplicates) throws Exception {
    return performExtractMethod(doRefactor, replaceAllDuplicates, getEditor(), getFile());
  }

  public static boolean performExtractMethod(boolean doRefactor,
                                     boolean replaceAllDuplicates,
                                     Editor editor,
                                     PsiFile file) throws PrepareFailedException, IncorrectOperationException {
    int startOffset = editor.getSelectionModel().getSelectionStart();
    int endOffset = editor.getSelectionModel().getSelectionEnd();

    PsiElement[] elements;
    PsiExpression expr = CodeInsightUtil.findExpressionInRange(file, startOffset, endOffset);
    if (expr != null) {
      elements = new PsiElement[]{expr};
    } else {
      elements = CodeInsightUtil.findStatementsInRange(file, startOffset, endOffset);
    }
    assertTrue(elements != null && elements.length > 0);

    final ExtractMethodProcessor processor = new ExtractMethodProcessor(getProject(), editor, file, elements,
            null, "Extract Method", "newMethod", null
    );
    processor.setShowErrorDialogs(false);

    if (!processor.prepare()) {
      return false;
    }

    if (doRefactor) {
      processor.testRun();
    }

    if (replaceAllDuplicates) {
      final List<Match> duplicates = processor.getDuplicates();
      for (Iterator<Match> iterator = duplicates.iterator(); iterator.hasNext();) {
        final Match expressionMatch = iterator.next();
        processor.processMatch(expressionMatch);
      }
    }

    return true;
  }
}