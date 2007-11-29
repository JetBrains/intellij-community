package com.intellij.refactoring;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.refactoring.introduceField.IntroduceFieldHandler;

/**
 * @author ven
 */
public class MockIntroduceFieldHandler extends IntroduceFieldHandler {
  private InitializationPlace myInitializationPlace;

  public MockIntroduceFieldHandler(final InitializationPlace initializationPlace) {
    myInitializationPlace = initializationPlace;
  }

  protected Settings showRefactoringDialog(Project project,
                                                                        PsiClass parentClass,
                                                                        PsiExpression expr,
                                                                        PsiType type,
                                                                        PsiExpression[] occurences,
                                                                        PsiElement anchorElement,
                                                                        PsiElement anchorElementIfAll) {
    SuggestedNameInfo name = CodeStyleManager.getInstance(project).suggestVariableName(VariableKind.FIELD, null, expr, type);
    return new Settings(name.names[0], true,
            true, true, myInitializationPlace,
            PsiModifier.PUBLIC,
            null,
            type, true,
            null, false);
  }
}
