/*
 * User: anna
 * Date: 28-Feb-2008
 */
package com.intellij.refactoring;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.refactoring.util.duplicates.MethodDuplicatesHandler;
import com.intellij.testFramework.LightCodeInsightTestCase;

public class FindMethodDuplicatesTest extends LightCodeInsightTestCase{
  private void doTest() throws Exception {
    doTest(true);
  }

  private void doTest(final boolean shouldSucceed) throws Exception {
     final String filePath = "/refactoring/methodDuplicates/" + getTestName(false) + ".java";
    configureByFile(filePath);
    final PsiElement targetElement = TargetElementUtilBase.findTargetElement(getEditor(), TargetElementUtilBase.ELEMENT_NAME_ACCEPTED);
    assertTrue("<caret> is not on method name", targetElement instanceof PsiMethod);
    final PsiMethod psiMethod = (PsiMethod)targetElement;

    try {
      MethodDuplicatesHandler.invokeOnScope(getProject(), psiMethod, new AnalysisScope(getFile()));
    }
    catch (RuntimeException e) {
      if (shouldSucceed) {
        fail("duplicates were not found");
      }
      return;
    }
    if (shouldSucceed) {
      checkResultByFile(filePath + ".after");
    } else {
      fail("duplicates found");
    }
  }

  public void testAnonymousTest() throws Exception {
    doTest();
  }

  public void testAnonymousTest1() throws Exception {
    doTest();
  }

  public void testAnonymousTest2() throws Exception {
    doTest(false);
  }

  public void testReturnVoidTest() throws Exception {
    doTest();
  }

  public void testThisReferenceTest() throws Exception {
    doTest();
  }

  public void testAddStaticTest() throws Exception {
    doTest();
  }

  public void testStaticMethodReplacement() throws Exception {
    doTest();
  }

  public void testRefReplacement() throws Exception {
    doTest();
  }

  public void testRefReplacement1() throws Exception {
    doTest();
  }

  public void testReturnVariable() throws Exception {
    doTest();
  }

  public void testReturnExpression() throws Exception {
    doTest(false);
  }

  public void testTypeInheritance() throws Exception {
    doTest();
  }

  public void testTypeInheritance1() throws Exception {
    doTest(false);
  }
}