package com.intellij.refactoring.inline;

import com.intellij.codeInspection.sameParameterValue.SameParameterValueInspection;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.RefactoringMessageDialog;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Processor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

/**
 * @author yole
 */
public class InlineParameterHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.inline.InlineParameterHandler");
  public static final String REFACTORING_NAME = RefactoringBundle.message("inline.parameter.refactoring");

  private InlineParameterHandler() {
  }

  public static void invoke(final Project project, final Editor editor, final PsiParameter psiParameter) {
    final PsiParameterList parameterList = (PsiParameterList) psiParameter.getParent();
    if (!(parameterList.getParent() instanceof PsiMethod)) {
      return;
    }
    final int index = parameterList.getParameterIndex(psiParameter);
    final PsiMethod method = (PsiMethod) parameterList.getParent();

    String errorMessage = getCannotInlineMessage(psiParameter, method);
    if (errorMessage != null) {
      CommonRefactoringUtil.showErrorMessage(RefactoringBundle.message("inline.parameter.refactoring"), errorMessage, null, project);
      return;
    }

    final Ref<PsiExpression> refInitializer = new Ref<PsiExpression>();
    final Ref<PsiExpression> refConstantInitializer = new Ref<PsiExpression>();
    final Ref<PsiCallExpression> refMethodCall = new Ref<PsiCallExpression>();
    final List<PsiReference> occurrences = new ArrayList<PsiReference>();
    final Collection<PsiFile> containingFiles = new HashSet<PsiFile>();
    boolean result = ReferencesSearch.search(method).forEach(new Processor<PsiReference>() {
      public boolean process(final PsiReference psiReference) {
        PsiElement element = psiReference.getElement();
        if (element.getParent() instanceof PsiCallExpression) {
          occurrences.add(psiReference);
          containingFiles.add(element.getContainingFile());
          PsiCallExpression methodCall = (PsiCallExpression) element.getParent();
          PsiExpression argument = methodCall.getArgumentList().getExpressions()[index];
          if (!refInitializer.isNull()) {
            return false;
          }
          if (InlineToAnonymousConstructorProcessor.isConstant(argument) || getReferencedFinalField(argument) != null) {
            if (refConstantInitializer.isNull()) {
              refConstantInitializer.set(argument);
            }
            else if (!isSameConstant(argument, refConstantInitializer.get())) {
              return false;
            }
          }
          else {
            refInitializer.set(argument);
            refMethodCall.set(methodCall);
          }
        }
        return true;
      }
    });
    if (occurrences.isEmpty()) {
      CommonRefactoringUtil.showErrorMessage(RefactoringBundle.message("inline.parameter.refactoring"),
                                             "Method has no usages", null, project);
      return;
    }
    if (!result) {
      CommonRefactoringUtil.showErrorMessage(RefactoringBundle.message("inline.parameter.refactoring"),
                                             "Cannot find constant initializer for parameter", null, project);
      return;
    }
    final ApplicationEx app = (ApplicationEx)ApplicationManager.getApplication();
    if ((app.isInternal() || app.isUnitTestMode()) && !refInitializer.isNull()) {
      try {
        new InlineParameterExpressionProcessor(refMethodCall.get(), method, psiParameter, refInitializer.get()).run();
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
      return;
    }
    if (refConstantInitializer.isNull()) {
      CommonRefactoringUtil.showErrorMessage(RefactoringBundle.message("inline.parameter.refactoring"),
                                             "Cannot find constant initializer for parameter", null, project);
      return;
    }

    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      String occurencesString = RefactoringBundle.message("occurences.string", occurrences.size());
      String question = RefactoringBundle.message("inline.parameter.confirmation", psiParameter.getName(),
                                                  refConstantInitializer.get().getText()) + " " + occurencesString;
      RefactoringMessageDialog dialog = new RefactoringMessageDialog(
        REFACTORING_NAME,
        question,
        HelpID.INLINE_VARIABLE,
        "OptionPane.questionIcon",
        true,
        project);
      dialog.show();
      if (!dialog.isOK()){
        return;
      }
    }

    new WriteCommandAction(project,
                           RefactoringBundle.message("inline.parameter.command.name", psiParameter.getName()),
                           containingFiles.toArray(new PsiFile[containingFiles.size()]) ) {
      protected void run(final Result result) throws Throwable {
        SameParameterValueInspection.InlineParameterValueFix.inlineSameParameterValue(method, psiParameter, refConstantInitializer.get());
      }

      protected UndoConfirmationPolicy getUndoConfirmationPolicy() {
        return UndoConfirmationPolicy.DEFAULT;
      }
    }.execute();
  }

  @Nullable
  private static PsiField getReferencedFinalField(final PsiExpression argument) {
    if (argument instanceof PsiReferenceExpression) {
      final PsiElement element = ((PsiReferenceExpression)argument).resolve();
      if (element instanceof PsiField) {
        final PsiField field = (PsiField)element;
        if (field.getModifierList().hasModifierProperty(PsiModifier.STATIC) &&
            field.getModifierList().hasModifierProperty(PsiModifier.FINAL)) {
          return field;
        }
      }
    }
    return null;
  }

  private static boolean isSameConstant(final PsiExpression expr1, final PsiExpression expr2) {
    boolean expr1Null = InlineToAnonymousConstructorProcessor.ourNullPattern.accepts(expr1);
    boolean expr2Null = InlineToAnonymousConstructorProcessor.ourNullPattern.accepts(expr2);
    if (expr1Null || expr2Null) {
      return expr1Null && expr2Null;
    }
    PsiField field1 = getReferencedFinalField(expr1);
    PsiField field2 = getReferencedFinalField(expr2);
    if (field1 != null || field2 != null) {
      return field1 == field2;
    }
    Object value1 = expr1.getManager().getConstantEvaluationHelper().computeConstantExpression(expr1);
    Object value2 = expr2.getManager().getConstantEvaluationHelper().computeConstantExpression(expr2);
    return value1 != null && value2 != null && value1.equals(value2);
  }

  @Nullable
  private static String getCannotInlineMessage(final PsiParameter psiParameter, final PsiMethod method) {
    if (psiParameter.isVarArgs()) {
      return RefactoringBundle.message("inline.parameter.error.varargs");
    }
    if (method.findSuperMethods().length > 0 ||
                 method.getManager().getSearchHelper().findOverridingMethods(method, method.getUseScope(), true).length > 0) {
      return RefactoringBundle.message("inline.parameter.error.hierarchy");
    }
    return null;
  }
}
