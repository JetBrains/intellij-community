package com.intellij.refactoring;

import com.intellij.codeInsight.CodeInsightTestCase;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.psi.PsiExpression;

/**
 * @author ven
 */
public class IntroduceFieldInSameClassTest extends CodeInsightTestCase {
  public void testInClassInitializer () throws Exception {
    configureByFile("/refactoring/introduceField/before1.java");
    performRefactoring();
    checkResultByFile("/refactoring/introduceField/after1.java");
  }

  private void performRefactoring() {
    new MockIntroduceFieldHandler().invoke(myProject, myEditor, myFile, null);
  }
}
