package com.intellij.refactoring.inline;

import com.intellij.psi.*;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.controlFlow.DefUseUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.InlineUtil;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.HelpID;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.codeInspection.sameParameterValue.SameParameterValueInspection;

import java.util.Map;
import java.util.HashMap;
import java.util.Collection;
import java.util.HashSet;

/**
 * @author yole
 */
public class InlineParameterExpressionProcessor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.inline.InlineParameterExpressionProcessor");

  private final PsiCallExpression myMethodCall;
  private final PsiMethod myMethod;
  private final PsiParameter myParameter;
  private final PsiExpression myInitializer;
  private final boolean mySameClass;

  public InlineParameterExpressionProcessor(final PsiCallExpression methodCall,
                                            final PsiMethod method,
                                            final PsiParameter parameter,
                                            final PsiExpression initializer) {
    myMethodCall = methodCall;
    myMethod = method;
    myParameter = parameter;
    myInitializer = initializer;

    PsiClass callingClass = PsiTreeUtil.getParentOfType(methodCall, PsiClass.class);
    mySameClass = (callingClass == myMethod.getContainingClass());
  }

  void run() throws IncorrectOperationException {
    final PsiMethod callingMethod = PsiTreeUtil.getParentOfType(myMethodCall, PsiMethod.class);
    int parameterIndex = myMethod.getParameterList().getParameterIndex(myParameter);
    final Map<PsiLocalVariable, PsiElement> localReplacements = new HashMap<PsiLocalVariable, PsiElement>();
    final PsiExpression[] arguments = myMethodCall.getArgumentList().getExpressions();
    for(int i=0; i<arguments.length; i++) {
      if (i != parameterIndex && arguments [i] instanceof PsiReferenceExpression) {
        final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)arguments[i];
        final PsiElement element = referenceExpression.resolve();
        if (element instanceof PsiLocalVariable) {
          final PsiParameter param = myMethod.getParameterList().getParameters()[i];
          final PsiExpression paramRef = myMethod.getManager().getElementFactory().createExpressionFromText(param.getName(), myMethod);
          localReplacements.put((PsiLocalVariable) element, paramRef);
        }
      }
    }

    myInitializer.accept(new PsiRecursiveElementVisitor() {
      public void visitReferenceExpression(final PsiReferenceExpression expression) {
        super.visitReferenceExpression(expression);
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
              else {
                final Map<PsiElement, PsiElement> elementsToReplace = new HashMap<PsiElement, PsiElement>();
                PsiExpression replacedInitializer = (PsiExpression)localInitializer.copy();
                if (replaceLocals(localReplacements, replacedInitializer, elementsToReplace)) {
                  try {
                    replacedInitializer = (PsiExpression) RefactoringUtil.replaceElementsWithMap(replacedInitializer, elementsToReplace);
                  }
                  catch (IncorrectOperationException e) {
                    LOG.error(e);
                  }
                  localReplacements.put(localVariable, replacedInitializer);
                }
              }
            }
          }
        }
      }
    });

    PsiExpression initializerInMethod = (PsiExpression) myInitializer.copy();
    final Map<PsiElement, PsiElement> elementsToReplace = new HashMap<PsiElement, PsiElement>();
    final boolean canEvaluate = replaceLocals(localReplacements, initializerInMethod, elementsToReplace);
    if (!canEvaluate) {
      CommonRefactoringUtil.showErrorMessage(RefactoringBundle.message("inline.parameter.refactoring"),
                                             "Parameter initializer depends on values which are not available inside the method and cannot be inlined",
                                             null, myMethod.getProject());
      return;
    }

    final Collection<PsiReference> parameterRefs = ReferencesSearch.search(myParameter).findAll();

    String question = RefactoringBundle.message("inline.parameter.confirmation", myParameter.getName(),
                                                myInitializer.getText());
    InlineParameterDialog dlg = new InlineParameterDialog(InlineParameterHandler.REFACTORING_NAME, question, HelpID.INLINE_VARIABLE,
                                                          "OptionPane.questionIcon", true, myMethod.getProject());
    if (!dlg.showDialog()) {
      return;
    }
    final boolean createLocal = dlg.isCreateLocal();

    initializerInMethod = (PsiExpression) RefactoringUtil.replaceElementsWithMap(initializerInMethod, elementsToReplace);

    final Collection<PsiFile> containingFiles = new HashSet<PsiFile>();
    containingFiles.add(myMethod.getContainingFile());
    containingFiles.add(myMethodCall.getContainingFile());
    final Project project = myMethod.getProject();
    final PsiExpression initializerInMethod1 = initializerInMethod;
    new WriteCommandAction(project,
                           RefactoringBundle.message("inline.parameter.command.name", myParameter.getName()),
                           containingFiles.toArray(new PsiFile[containingFiles.size()]) ) {
      protected void run(final Result result) throws Throwable {
        final PsiElementFactory factory = myMethod.getManager().getElementFactory();
        if (!createLocal) {
          for(PsiReference ref: parameterRefs) {
            InlineUtil.inlineVariable(myParameter, initializerInMethod1, (PsiJavaCodeReferenceElement) ref.getElement());
          }
        }
        PsiDeclarationStatement localDeclaration = factory.createVariableDeclarationStatement(myParameter.getName(),
                                                                                              myParameter.getType(),
                                                                                              initializerInMethod1);
        SameParameterValueInspection.InlineParameterValueFix.removeParameter(myMethod, myParameter);
        if (createLocal) {
          final PsiCodeBlock body = myMethod.getBody();
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

  private boolean replaceLocals(final Map<PsiLocalVariable, PsiElement> localReplacements,
                                final PsiExpression initializerInMethod,
                                final Map<PsiElement, PsiElement> elementsToReplace) {
    final Ref<Boolean> refCannotEvaluate = new Ref<Boolean>();
    initializerInMethod.accept(new PsiRecursiveElementVisitor() {
      public void visitReferenceExpression(final PsiReferenceExpression expression) {
        super.visitReferenceExpression(expression);
        final PsiElement element = expression.resolve();
        if (!canEvaluate(expression, element, localReplacements, elementsToReplace)) {
          refCannotEvaluate.set(Boolean.TRUE);
        }
      }
    });
    return refCannotEvaluate.isNull();
  }

  private boolean canEvaluate(final PsiReferenceExpression expression,
                              final PsiElement element,
                              final Map<PsiLocalVariable, PsiElement> localReplacements,
                              final Map<PsiElement, PsiElement> elementsToReplace) {
    if (element instanceof PsiLocalVariable) {
      final PsiLocalVariable localVariable = (PsiLocalVariable)element;
      final PsiElement localReplacement = localReplacements.get(localVariable);
      if (localReplacement != null) {
        elementsToReplace.put(expression, localReplacement);
        return true;
      }
    }
    else if (element instanceof PsiMethod || element instanceof PsiField) {
      return mySameClass;
    }
    return false;
  }
}