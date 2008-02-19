package com.intellij.refactoring;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.refactoring.introduceField.IntroduceFieldHandler;

/**
 * @author ven
 */
public class MockIntroduceFieldHandler extends IntroduceFieldHandler {
  private InitializationPlace myInitializationPlace;
  private boolean myDeclareStatic;

  public MockIntroduceFieldHandler(final InitializationPlace initializationPlace, final boolean declareStatic) {
    myInitializationPlace = initializationPlace;
    myDeclareStatic = declareStatic;
  }

  protected Settings showRefactoringDialog(Project project,
                                                                        PsiClass parentClass,
                                                                        PsiExpression expr,
                                                                        PsiType type,
                                                                        PsiExpression[] occurences,
                                                                        PsiElement anchorElement,
                                                                        PsiElement anchorElementIfAll) {
    SuggestedNameInfo name = JavaCodeStyleManager.getInstance(project).suggestVariableName(VariableKind.FIELD, null, expr, type);
    return new Settings(name.names[0], true, myDeclareStatic, true, myInitializationPlace,
            PsiModifier.PUBLIC,
            null,
            type, true,
            null, false);
  }
}
