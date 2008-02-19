package com.intellij.refactoring;

import com.intellij.codeInsight.CodeInsightTestCase;
import com.intellij.refactoring.introduceField.BaseExpressionToFieldHandler;

/**
 * @author ven
 */
public class IntroduceFieldInSameClassTest extends CodeInsightTestCase {
  public void testInClassInitializer () throws Exception {
    configureByFile("/refactoring/introduceField/before1.java");
    performRefactoring(BaseExpressionToFieldHandler.InitializationPlace.IN_FIELD_DECLARATION, true);
    checkResultByFile("/refactoring/introduceField/after1.java");
  }

  public void testInElseClause() throws Exception {
    configureByFile("/refactoring/introduceField/beforeElseClause.java");
    performRefactoring(BaseExpressionToFieldHandler.InitializationPlace.IN_CURRENT_METHOD, true);
    checkResultByFile("/refactoring/introduceField/afterElseClause.java");
  }

  public void testOuterClass() throws Exception {
    configureByFile("/refactoring/introduceField/beforeOuterClass.java");
    performRefactoring(BaseExpressionToFieldHandler.InitializationPlace.IN_CONSTRUCTOR, false);
    checkResultByFile("/refactoring/introduceField/afterOuterClass.java");
  }

  private void performRefactoring(final BaseExpressionToFieldHandler.InitializationPlace initializationPlace, final boolean declareStatic) {
    new MockIntroduceFieldHandler(initializationPlace, declareStatic).invoke(myProject, myEditor, myFile, null);
  }
}