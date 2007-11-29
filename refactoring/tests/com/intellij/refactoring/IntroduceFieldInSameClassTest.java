package com.intellij.refactoring;

import com.intellij.codeInsight.CodeInsightTestCase;
import com.intellij.refactoring.introduceField.BaseExpressionToFieldHandler;

/**
 * @author ven
 */
public class IntroduceFieldInSameClassTest extends CodeInsightTestCase {
  public void testInClassInitializer () throws Exception {
    configureByFile("/refactoring/introduceField/before1.java");
    performRefactoring(BaseExpressionToFieldHandler.InitializationPlace.IN_FIELD_DECLARATION);
    checkResultByFile("/refactoring/introduceField/after1.java");
  }

  public void testInElseClause() throws Exception {
    configureByFile("/refactoring/introduceField/beforeElseClause.java");
    performRefactoring(BaseExpressionToFieldHandler.InitializationPlace.IN_CURRENT_METHOD);
    checkResultByFile("/refactoring/introduceField/afterElseClause.java");
  }

  private void performRefactoring(final BaseExpressionToFieldHandler.InitializationPlace initializationPlace) {
    new MockIntroduceFieldHandler(initializationPlace).invoke(myProject, myEditor, myFile, null);
  }
}