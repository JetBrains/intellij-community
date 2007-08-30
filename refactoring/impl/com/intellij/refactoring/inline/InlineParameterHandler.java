package com.intellij.refactoring.inline;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.Processor;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.codeInspection.sameParameterValue.SameParameterValueInspection;

/**
 * @author yole
 */
public class InlineParameterHandler {
  private InlineParameterHandler() {
  }

  public static void invoke(final Project project, final Editor editor, final PsiParameter psiParameter) {
    String errorMessage = getCannotInlineMessage(psiParameter);
    if (errorMessage != null) {
      CommonRefactoringUtil.showErrorMessage(RefactoringBundle.message("inline.parameter.refactoring"), errorMessage, null, project);
      return;
    }

    final PsiParameterList parameterList = (PsiParameterList) psiParameter.getParent();
    if (!(parameterList.getParent() instanceof PsiMethod)) {
      return;
    }
    final int index = parameterList.getParameterIndex(psiParameter);
    PsiMethod method = (PsiMethod) parameterList.getParent();
    final Ref<PsiExpression> refInitializer = new Ref<PsiExpression>();
    final Ref<PsiExpression> refConstantInitializer = new Ref<PsiExpression>();
    boolean result = ReferencesSearch.search(method).forEach(new Processor<PsiReference>() {
      public boolean process(final PsiReference psiReference) {
        PsiElement element = psiReference.getElement();
        if (element.getParent() instanceof PsiMethodCallExpression) {
          PsiMethodCallExpression methodCall = (PsiMethodCallExpression) element.getParent();
          PsiExpression argument = methodCall.getArgumentList().getExpressions() [index];
          if (!refInitializer.isNull()) {
            return false;
          }
          if (InlineToAnonymousConstructorProcessor.isConstant(argument)) {
            if (refConstantInitializer.isNull()) {
              refConstantInitializer.set(argument);
            }
            else if (!isSameConstant(argument, refConstantInitializer.get())) {
              return false;
            }
          }
          else {
            refInitializer.set(argument);
          }
        }
        return true;
      }
    });
    if (!result || refConstantInitializer.isNull()) {
      CommonRefactoringUtil.showErrorMessage(RefactoringBundle.message("inline.parameter.refactoring"),
                                             "Cannot find constant initializer for parameter", null, project);
      return;
    }

    SameParameterValueInspection.InlineParameterValueFix.inlineSameParameterValue(method, psiParameter, refConstantInitializer.get());
  }

  private static boolean isSameConstant(final PsiExpression expr1, final PsiExpression expr2) {
    boolean expr1Null = InlineToAnonymousConstructorProcessor.ourNullPattern.accepts(expr1);
    boolean expr2Null = InlineToAnonymousConstructorProcessor.ourNullPattern.accepts(expr2);
    if (expr1Null || expr2Null) {
      return expr1Null && expr2Null;
    }
    Object value1 = expr1.getManager().getConstantEvaluationHelper().computeConstantExpression(expr1);
    Object value2 = expr2.getManager().getConstantEvaluationHelper().computeConstantExpression(expr2);
    return value1 != null && value2 != null && value1.equals(value2);
  }

  private static String getCannotInlineMessage(final PsiParameter psiParameter) {
    if (psiParameter.isVarArgs()) {
      return "Inline for varargs parameters is not supported";
    }
    return null;
  }
}