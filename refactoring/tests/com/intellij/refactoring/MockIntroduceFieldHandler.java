package com.intellij.refactoring;

import com.intellij.refactoring.introduceField.IntroduceFieldHandler;
import com.intellij.refactoring.introduceField.BaseExpressionToFieldHandler;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.codeStyle.SuggestedNameInfo;

/**
 * @author ven
 */
public class MockIntroduceFieldHandler extends IntroduceFieldHandler {
  protected Settings showRefactoringDialog(Project project,
                                                                        PsiClass parentClass,
                                                                        PsiExpression expr,
                                                                        PsiType type,
                                                                        PsiExpression[] occurences,
                                                                        PsiElement anchorElement,
                                                                        PsiElement anchorElementIfAll) {
    SuggestedNameInfo name = CodeStyleManager.getInstance(project).suggestVariableName(VariableKind.FIELD, null, expr, type);
    return new Settings(name.names[0], true,
            true, true, BaseExpressionToFieldHandler.InitializationPlace.IN_FIELD_DECLARATION,
            PsiModifier.PUBLIC,
            null,
            type, true,
            null, false);
  }
}
