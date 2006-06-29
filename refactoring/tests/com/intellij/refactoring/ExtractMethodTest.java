package com.intellij.refactoring;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.refactoring.extractMethod.ExtractMethodProcessor;
import com.intellij.refactoring.extractMethod.PrepareFailedException;
import com.intellij.refactoring.util.duplicates.Match;
import com.intellij.testFramework.LightCodeInsightTestCase;
import com.intellij.util.IncorrectOperationException;

import java.util.Iterator;
import java.util.List;

public class ExtractMethodTest extends LightCodeInsightTestCase {
  private static final String BASE_PATH = "/refactoring/extractMethod/";
  private boolean myCatchOnNewLine = true;

  public void testExitPoints1() throws Exception {
    doExitPointsTest(true);
  }

  public void testExitPoints2() throws Exception {
    doTest();
  }

  public void testExitPoints3() throws Exception {
    doExitPointsTest(true);
  }

  public void testExitPoints4() throws Exception {
    doExitPointsTest(false);
  }

  public void testExitPointsInsideLoop() throws Exception {
    doExitPointsTest(true);
  }

  public void testExitPoints5() throws Exception {
    doTest();
  }

  public void testExitPoints6() throws Exception {
    doExitPointsTest(false);
  }

  public void testExitPoints7() throws Exception {
    doExitPointsTest(false);
  }

  public void testExitPoints8() throws Exception {
    doExitPointsTest(false);
  }

  public void testBooleanExpression() throws Exception {
    doTest();
  }

  public void testScr6241() throws Exception {
    doTest();
  }

  public void testScr7091() throws Exception {
    doTest();
  }

  public void testScr10464() throws Exception {
    doTest();
  }

  public void testScr9852() throws Exception {
    doTest();
  }

  public void testUseVarAfterTry() throws Exception {
    doTest();
  }

  public void testOneBranchAssignment() throws Exception {
    doTest();
  }

  public void testExtractFromCodeBlock() throws Exception {
    doTest();
  }

  public void testUnusedInitializedVar() throws Exception {
    doTest();
  }

  public void testTryFinally() throws Exception {
    doTest();
  }

  public void testFinally() throws Exception {
    doTest();
  }

  public void testExtractFromAnonymous() throws Exception {
    doTest();
  }

  public void testSCR12245() throws Exception {
    doTest();
  }

  public void testSCR15815() throws Exception {
    doTest();
  }

  public void testSCR27887() throws Exception {
    doTest();
  }

  public void testSCR28427() throws Exception {
    doTest();
  }

  public void testTryFinallyInsideFor() throws Exception {
    doTest();
  }

  public void testExtractFromTryFinally() throws Exception {
    doTest();
  }

  public void testLesyaBug() throws Exception {
    myCatchOnNewLine = false;
    doTest();
  }

  public void testForEach() throws Exception {
    doTest();
  }

  public void testAnonInner() throws Exception {
    doTest();
  }

  public void testFinalParamUsedInsideAnon() throws Exception {
    CodeStyleSettingsManager.getSettings(getProject()).GENERATE_FINAL_PARAMETERS = false;
    doTest();
  }

  public void testNonFinalWritableParam() throws Exception {
    CodeStyleSettingsManager.getSettings(getProject()).GENERATE_FINAL_PARAMETERS = true;
    doTest();
  }

  public void testExpressionDuplicates() throws Exception {
    doDuplicatesTest();
  }

  public void testCodeDuplicates() throws Exception {
    doDuplicatesTest();
  }

  public void testCodeDuplicates2() throws Exception {
    doDuplicatesTest();
  }

  public void testCodeDuplicates3() throws Exception {
    doDuplicatesTest();
  }

  public void testCodeDuplicates4() throws Exception {
    doDuplicatesTest();
  }

  public void testCodeDuplicates5() throws Exception {
    doDuplicatesTest();
  }

  public void testCodeDuplicatesWithOutputValue() throws Exception {
    doDuplicatesTest();
  }

  public void testCodeDuplicatesWithOutputValue1() throws Exception {
    doDuplicatesTest();
  }

  public void testCodeDuplicatesWithMultExitPoints() throws Exception {
    doDuplicatesTest();
  }

  public void testCodeDuplicatesWithReturn() throws Exception {
    doDuplicatesTest();
  }

  public void testCodeDuplicatesWithReturn2() throws Exception {
    doDuplicatesTest();
  }

  public void testSCR32924() throws Exception {
    doDuplicatesTest();
  }

  public void testFinalOutputVar() throws Exception {
    doDuplicatesTest();
  }

  public void testIdeaDev2291() throws Exception {
    doTest();
  }

  public void testOxfordBug() throws Exception {
    doTest();
  }

  public void testGuardMethodDuplicates() throws Exception {
    doDuplicatesTest();
  }

  public void testGuardMethodDuplicates1() throws Exception {
    doDuplicatesTest();
  }

  public void testInstanceMethodDuplicatesInStaticContext() throws Exception {
    doDuplicatesTest();
  }


  public void testLValueNotDuplicate() throws Exception {
    doDuplicatesTest();
  }

  private void doDuplicatesTest() throws Exception {
    doTest(true);
  }

  public void testExtractFromFinally() throws Exception {
    doTest();
  }

  public void testNoShortCircuit() throws Exception {
    doTest();
  }


  private void doExitPointsTest(boolean shouldSucceed) throws Exception {
    String fileName = getTestName(false) + ".java";
    configureByFile(BASE_PATH + fileName);
    boolean success = performAction(false, false);
    assertEquals(shouldSucceed, success);
  }

  void doTest() throws Exception {
    final CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(getProject());
    settings.ELSE_ON_NEW_LINE = true;
    settings.CATCH_ON_NEW_LINE = myCatchOnNewLine;
    doTest(true);
  }

  private void doTest(boolean duplicates) throws Exception {
    configureByFile(BASE_PATH + getTestName(false) + ".java");
    boolean success = performAction(true, duplicates);
    assertTrue(success);
    checkResultByFile(BASE_PATH + getTestName(false) + "_after.java");
  }

  private boolean performAction(boolean doRefactor, boolean replaceAllDuplicates) throws Exception {
    return performExtractMethod(doRefactor, replaceAllDuplicates, getEditor(), getFile(), getProject());
  }

  public static boolean performExtractMethod(boolean doRefactor, boolean replaceAllDuplicates, Editor editor, PsiFile file, Project project)
    throws PrepareFailedException, IncorrectOperationException {
    int startOffset = editor.getSelectionModel().getSelectionStart();
    int endOffset = editor.getSelectionModel().getSelectionEnd();

    PsiElement[] elements;
    PsiExpression expr = CodeInsightUtil.findExpressionInRange(file, startOffset, endOffset);
    if (expr != null) {
      elements = new PsiElement[]{expr};
    }
    else {
      elements = CodeInsightUtil.findStatementsInRange(file, startOffset, endOffset);
    }
    assertTrue(elements.length > 0);

    final ExtractMethodProcessor processor =
      new ExtractMethodProcessor(project, editor, elements, null, "Extract Method", "newMethod", null);
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