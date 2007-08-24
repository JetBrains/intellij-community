
package com.intellij.refactoring.inline;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.util.Processor;

import java.util.ArrayList;
import java.util.List;

class InlineMethodHandler {
  private static final String REFACTORING_NAME = RefactoringBundle.message("inline.method.title");

  private InlineMethodHandler() {
  }

  public static void invoke(final Project project, Editor editor, PsiMethod method) {
    method = (PsiMethod)method.getNavigationElement();
    if (method.getBody() == null){
      String message;
      if (method.hasModifierProperty(PsiModifier.ABSTRACT)) {
        message = RefactoringBundle.message("refactoring.cannot.be.applied.to.abstract.methods", REFACTORING_NAME);
      }
      else {
        message = RefactoringBundle.message("refactoring.cannot.be.applied.no.sources.attached", REFACTORING_NAME);
      }
      CommonRefactoringUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.INLINE_METHOD, project);
      return;
    }

    PsiReference reference = editor != null ? TargetElementUtil.findReference(editor, editor.getCaretModel().getOffset()) : null;
    boolean allowInlineThisOnly = false;
    if (InlineMethodProcessor.checkBadReturns(method) && !allUsagesAreTailCalls(method)) {
      if (reference != null && isTailCall(reference)) {
        allowInlineThisOnly = true;
      }
      else {
        String message = RefactoringBundle.message("refactoring.is.not.supported.when.return.statement.interrupts.the.execution.flow", REFACTORING_NAME);
        CommonRefactoringUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.INLINE_METHOD, project);
        return;
      }
    }

    if (reference == null && checkRecursive(method)) {
      String message = RefactoringBundle.message("refactoring.is.not.supported.for.recursive.methods", REFACTORING_NAME);
      CommonRefactoringUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.INLINE_METHOD, project);
      return;
    }

    if (method.isConstructor()) {
      if (method.isVarArgs()) {
        String message = RefactoringBundle.message("refactoring.cannot.be.applied.to.vararg.constructors", REFACTORING_NAME);
        CommonRefactoringUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.INLINE_CONSTRUCTOR, project);
        return;
      }
      if (!isChainingConstructor(method)) {
        String message = RefactoringBundle.message("refactoring.cannot.be.applied.to.inline.non.chaining.constructors", REFACTORING_NAME);
        CommonRefactoringUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.INLINE_CONSTRUCTOR, project);
        return;
      }
      if (reference != null) {
        PsiCall constructorCall = RefactoringUtil.getEnclosingConstructorCall((PsiJavaCodeReferenceElement)reference.getElement());
        if (constructorCall == null || !method.equals(constructorCall.resolveMethod())) reference = null;
      }
    }
    else {
      if (reference != null && !method.getManager().areElementsEquivalent(method, reference.resolve())) {
        reference = null;
      }
    }

    final boolean invokedOnReference = reference != null;
    if (!invokedOnReference) {
      final VirtualFile vFile = method.getContainingFile().getVirtualFile();
      ReadonlyStatusHandler.getInstance(project).ensureFilesWritable(vFile);
    }
    PsiJavaCodeReferenceElement refElement = reference != null ? (PsiJavaCodeReferenceElement)reference.getElement() : null;
    InlineMethodDialog dialog = new InlineMethodDialog(project, method, refElement, editor, allowInlineThisOnly);
    dialog.show();
  }

  private static boolean allUsagesAreTailCalls(final PsiMethod method) {
    final List<PsiReference> nonTailCallUsages = new ArrayList<PsiReference>();
    boolean result = ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
      public void run() {
        ReferencesSearch.search(method).forEach(new Processor<PsiReference>() {
          public boolean process(final PsiReference psiReference) {
            ProgressManager.getInstance().checkCanceled();
            if (!isTailCall(psiReference)) {
              nonTailCallUsages.add(psiReference);
              return false;
            }
            return true;
          }
        });
      }
    }, RefactoringBundle.message("inline.method.checking.tail.calls.progress"), true, method.getProject());
    return result && nonTailCallUsages.isEmpty();
  }

  private static boolean isTailCall(final PsiReference psiReference) {
    PsiElement element = psiReference.getElement();
    PsiExpression methodCall = PsiTreeUtil.getParentOfType(element, PsiMethodCallExpression.class);
    if (methodCall == null) return false;
    if (methodCall.getParent() instanceof PsiReturnStatement) return true;
    if (methodCall.getParent() instanceof PsiExpressionStatement) {
      PsiStatement callStatement = (PsiStatement) methodCall.getParent();
      PsiMethod callerMethod = PsiTreeUtil.getParentOfType(callStatement, PsiMethod.class);
      if (callerMethod != null) {
        final PsiStatement[] psiStatements = callerMethod.getBody().getStatements();
        return psiStatements.length > 0 && callStatement == psiStatements [psiStatements.length-1];
      }
    }
    return false;
  }

  public static boolean isChainingConstructor(PsiMethod constructor) {
    PsiCodeBlock body = constructor.getBody();
    if (body != null) {
      PsiStatement[] statements = body.getStatements();
      if (statements.length == 1 && statements[0] instanceof PsiExpressionStatement) {
        PsiExpression expression = ((PsiExpressionStatement)statements[0]).getExpression();
        if (expression instanceof PsiMethodCallExpression) {
          PsiReferenceExpression methodExpr = ((PsiMethodCallExpression)expression).getMethodExpression();
            if ("this".equals(methodExpr.getReferenceName())) {
              PsiElement resolved = methodExpr.resolve();
              return resolved instanceof PsiMethod && ((PsiMethod)resolved).isConstructor(); //delegated via "this" call
            }
        }
      }
    }
    return false;
  }

  public static boolean checkRecursive(PsiMethod method) {
    return checkCalls(method.getBody(), method);
  }

  private static boolean checkCalls(PsiElement scope, PsiMethod method) {
    if (scope instanceof PsiMethodCallExpression){
      PsiMethod refMethod = (PsiMethod)((PsiMethodCallExpression)scope).getMethodExpression().resolve();
      if (method.equals(refMethod)) return true;
    }

    for(PsiElement child = scope.getFirstChild(); child != null; child = child.getNextSibling()){
      if (checkCalls(child, method)) return true;
    }

    return false;
  }
}