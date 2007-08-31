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
import com.intellij.psi.controlFlow.DefUseUtil;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.InlineUtil;
import com.intellij.refactoring.util.RefactoringMessageDialog;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Processor;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author yole
 */
public class InlineParameterHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.inline.InlineParameterHandler");
  private static final String REFACTORING_NAME = RefactoringBundle.message("inline.parameter.refactoring");

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
    final PsiMethod method = (PsiMethod) parameterList.getParent();
    final Ref<PsiExpression> refInitializer = new Ref<PsiExpression>();
    final Ref<PsiExpression> refConstantInitializer = new Ref<PsiExpression>();
    final Ref<PsiMethodCallExpression> refMethodCall = new Ref<PsiMethodCallExpression>();
    final List<PsiReference> occurrences = new ArrayList<PsiReference>();
    final Collection<PsiFile> containingFiles = new HashSet<PsiFile>();
    boolean result = ReferencesSearch.search(method).forEach(new Processor<PsiReference>() {
      public boolean process(final PsiReference psiReference) {
        PsiElement element = psiReference.getElement();
        if (element.getParent() instanceof PsiMethodCallExpression) {
          occurrences.add(psiReference);
          containingFiles.add(element.getContainingFile());
          PsiMethodCallExpression methodCall = (PsiMethodCallExpression) element.getParent();
          PsiExpression argument = methodCall.getArgumentList().getExpressions()[index];
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
        tryInlineReferenceArgument(refMethodCall.get(), method, psiParameter, refInitializer.get());
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

  private static void tryInlineReferenceArgument(PsiMethodCallExpression methodCall, final PsiMethod method, final PsiParameter parameter,
                                                 final PsiExpression initializer) throws IncorrectOperationException {
    final PsiMethod callingMethod = PsiTreeUtil.getParentOfType(methodCall, PsiMethod.class);
    int parameterIndex = method.getParameterList().getParameterIndex(parameter);
    final Map<PsiLocalVariable, PsiElement> localReplacements = new HashMap<PsiLocalVariable, PsiElement>();
    final PsiExpression[] arguments = methodCall.getArgumentList().getExpressions();
    for(int i=0; i<arguments.length; i++) {
      if (i != parameterIndex && arguments [i] instanceof PsiReferenceExpression) {
        final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)arguments[i];
        final PsiElement element = referenceExpression.resolve();
        if (element instanceof PsiLocalVariable) {
          final PsiParameter param = method.getParameterList().getParameters()[i];
          final PsiExpression paramRef = method.getManager().getElementFactory().createExpressionFromText(param.getName(), method);
          localReplacements.put((PsiLocalVariable) element, paramRef);
        }
      }
    }

    initializer.accept(new PsiRecursiveElementVisitor() {
      public void visitReferenceExpression(final PsiReferenceExpression expression) {
        final PsiElement element = expression.resolve();
        if (element instanceof PsiLocalVariable) {
          final PsiLocalVariable localVariable = (PsiLocalVariable)element;
          if (localReplacements.containsKey(localVariable)) return;
          final PsiElement[] elements = DefUseUtil.getDefs(callingMethod.getBody(), localVariable, expression);
          if (elements.length == 1) {
            PsiExpression localInitializer = null;
            if (elements [0] instanceof PsiLocalVariable) {
              localInitializer = ((PsiLocalVariable) elements [0]).getInitializer();
            }
            else if (elements [0] instanceof PsiAssignmentExpression) {
              localInitializer = ((PsiAssignmentExpression) elements [0]).getRExpression();
            }
            if (localInitializer != null) {
              if (InlineToAnonymousConstructorProcessor.isConstant(localInitializer)) {
                localReplacements.put(localVariable, localInitializer);
              }
            }
          }
        }
      }
    });

    PsiExpression initializerInMethod = (PsiExpression) initializer.copy();
    final Map<PsiElement, PsiElement> elementsToReplace = new HashMap<PsiElement, PsiElement>();
    final boolean canEvaluate = replaceLocals(localReplacements, initializerInMethod, elementsToReplace);
    if (!canEvaluate) {
      CommonRefactoringUtil.showErrorMessage(RefactoringBundle.message("inline.parameter.refactoring"),
                                             "Parameter initializer depends on values which are not available inside the method and cannot be inlined",
                                             null, method.getProject());
      return;
    }

    final Collection<PsiReference> parameterRefs = ReferencesSearch.search(parameter).findAll();

    String question = RefactoringBundle.message("inline.parameter.confirmation", parameter.getName(),
                                                initializer.getText());
    InlineParameterDialog dlg = new InlineParameterDialog(REFACTORING_NAME, question, HelpID.INLINE_VARIABLE,
                                                          "OptionPane.questionIcon", true, method.getProject());
    if (!dlg.showDialog()) {
      return;
    }
    final boolean createLocal = dlg.isCreateLocal();

    for(Map.Entry<PsiElement, PsiElement> e: elementsToReplace.entrySet()) {
      try {
        if (e.getKey() == initializerInMethod) {
          initializerInMethod = (PsiExpression) initializerInMethod.replace(e.getValue());
        }
        else {
          e.getKey().replace(e.getValue());
        }
      }
      catch (IncorrectOperationException e1) {
        LOG.error(e1);
      }
    }

    final Collection<PsiFile> containingFiles = new HashSet<PsiFile>();
    containingFiles.add(method.getContainingFile());
    containingFiles.add(methodCall.getContainingFile());
    final Project project = method.getProject();
    final PsiExpression initializerInMethod1 = initializerInMethod;
    new WriteCommandAction(project,
                           RefactoringBundle.message("inline.parameter.command.name", parameter.getName()),
                           containingFiles.toArray(new PsiFile[containingFiles.size()]) ) {
      protected void run(final Result result) throws Throwable {
        final PsiElementFactory factory = method.getManager().getElementFactory();
        if (!createLocal) {
          for(PsiReference ref: parameterRefs) {
            InlineUtil.inlineVariable(parameter, initializerInMethod1, (PsiJavaCodeReferenceElement) ref.getElement());
          }
        }
        PsiDeclarationStatement localDeclaration = factory.createVariableDeclarationStatement(parameter.getName(),
                                                                                              parameter.getType(),
                                                                                              initializerInMethod1);
        SameParameterValueInspection.InlineParameterValueFix.removeParameter(method, parameter);
        if (createLocal) {
          final PsiCodeBlock body = method.getBody();
          if (body != null) {
            body.addAfter(localDeclaration, body.getLBrace());
          }
        }

        for(PsiLocalVariable var: localReplacements.keySet()) {
          if (ReferencesSearch.search(var).findFirst() == null) {
            var.delete();
          }
        }
      }

      protected UndoConfirmationPolicy getUndoConfirmationPolicy() {
        return UndoConfirmationPolicy.DEFAULT;
      }
    }.execute();
  }

  private static boolean replaceLocals(final Map<PsiLocalVariable, PsiElement> localReplacements, final PsiExpression initializerInMethod,
                                       final Map<PsiElement, PsiElement> elementsToReplace) {
    final Ref<Boolean> refCannotEvaluate = new Ref<Boolean>();
    initializerInMethod.accept(new PsiRecursiveElementVisitor() {
      public void visitReferenceExpression(final PsiReferenceExpression expression) {
        final PsiElement element = expression.resolve();
        if (element instanceof PsiLocalVariable) {
          final PsiLocalVariable localVariable = (PsiLocalVariable)element;
          final PsiElement localReplacement = localReplacements.get(localVariable);
          if (localReplacement != null) {
            elementsToReplace.put(expression, localReplacement);
          }
          else {
            refCannotEvaluate.set(Boolean.TRUE);
          }
        }
        else {
          refCannotEvaluate.set(Boolean.TRUE);
        }
      }
    });
    return refCannotEvaluate.isNull();
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

  @Nullable
  private static String getCannotInlineMessage(final PsiParameter psiParameter) {
    if (psiParameter.isVarArgs()) {
      return "Inline for varargs parameters is not supported";
    }
    return null;
  }
}
