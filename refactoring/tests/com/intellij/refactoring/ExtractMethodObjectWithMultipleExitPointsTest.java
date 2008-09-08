/*
 * User: anna
 * Date: 06-May-2008
 */
package com.intellij.refactoring;

import com.intellij.codeInsight.CodeInsightTestCase;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.refactoring.extractMethodObject.ExtractMethodObjectProcessor;
import com.intellij.refactoring.util.duplicates.DuplicatesImpl;

public class ExtractMethodObjectWithMultipleExitPointsTest extends CodeInsightTestCase {

  private void doTest() throws Exception {
    doTest(true);
  }

  private void doTest(boolean createInnerClass) throws Exception {
    final String testName = getTestName(true);
    configureByFile("/refactoring/extractMethodObject/multipleExitPoints/" + testName + ".java");
    int startOffset = myEditor.getSelectionModel().getSelectionStart();
    int endOffset = myEditor.getSelectionModel().getSelectionEnd();

    PsiElement[] elements;
    PsiExpression expr = CodeInsightUtil.findExpressionInRange(myFile, startOffset, endOffset);
    if (expr != null) {
      elements = new PsiElement[]{expr};
    }
    else {
      elements = CodeInsightUtil.findStatementsInRange(myFile, startOffset, endOffset);
    }

    final ExtractMethodObjectProcessor processor =
        new ExtractMethodObjectProcessor(getProject(), getEditor(), elements, "Inner");
    final ExtractMethodObjectProcessor.MyExtractMethodProcessor extractProcessor = processor.getExtractProcessor();
    extractProcessor.setShowErrorDialogs(false);
    extractProcessor.prepare();
    extractProcessor.testRun();
    processor.setCreateInnerClass(createInnerClass);
    processor.run();
    DuplicatesImpl.processDuplicates(extractProcessor, getProject(), getEditor());
    processor.getMethod().delete();
    checkResultByFile("/refactoring/extractMethodObject/multipleExitPoints/" + testName + ".java" + ".after");
  }

  public void testStaticInner() throws Exception {
    doTest();
  }

  public void testInputOutput() throws Exception {
    doTest();
  }

  public void testOutputVarsReferences() throws Exception {
    doTest();
  }

}