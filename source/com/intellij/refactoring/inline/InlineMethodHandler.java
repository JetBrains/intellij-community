
package com.intellij.refactoring.inline;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.util.RefactoringMessageUtil;
import com.intellij.refactoring.util.RefactoringUtil;

class InlineMethodHandler {
  private static final String REFACTORING_NAME = "Inline Method";

  public void invoke(final Project project, Editor editor, final PsiMethod method) {
    if (method.getBody() == null){
      String message = REFACTORING_NAME + " refactoring cannot be applied to abstract methods";
      RefactoringMessageUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.INLINE_METHOD, project);
      return;
    }

    if (InlineMethodProcessor.checkBadReturns(method)) {
      String message = REFACTORING_NAME + " refactoring is not supported when return statement interrupts the execution flow";
      RefactoringMessageUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.INLINE_METHOD, project);
      return;
    }

    if (checkRecursive(method)) {
      String message = REFACTORING_NAME + " refactoring is not supported for recursive methods";
      RefactoringMessageUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.INLINE_METHOD, project);
      return;
    }

    PsiReference reference = editor != null ? TargetElementUtil.findReference(editor, editor.getCaretModel().getOffset()) : null;
    if (method.isConstructor()) {
      if (method.isVarArgs()) {
        String message = REFACTORING_NAME + " refactoring cannot be applied to vararg constructors";
        RefactoringMessageUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.INLINE_METHOD, project);
        return;
      }
      if (!checkChainingConstructor(method)) {
        String message = REFACTORING_NAME + " refactoring cannot be applied to inline non-chaining constructors";
        RefactoringMessageUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.INLINE_METHOD, project);
        return;
      }
      if (reference != null) {
        PsiCall constructorCall = RefactoringUtil.getEnclosingConstructorCall((PsiJavaCodeReferenceElement)reference.getElement());
        if (constructorCall == null || !method.equals(constructorCall.resolveMethod())) reference = null;
      }
    }
    else {
      if (reference != null && !method.equals(reference.resolve())) {
        reference = null;
      }
    }

    final boolean invokedOnReference = reference != null;
    if (!invokedOnReference && !method.isWritable()) {
      RefactoringMessageUtil.showReadOnlyElementRefactoringMessage(project, method);
      return;
    }
    PsiJavaCodeReferenceElement element = reference != null ? (PsiJavaCodeReferenceElement)reference.getElement() : null;
    final InlineMethodProcessor processor = new InlineMethodProcessor(project, method, element, editor);
    InlineMethodDialog dialog = new InlineMethodDialog(project, method, invokedOnReference, processor);
    dialog.show();
  }

  private boolean checkChainingConstructor(PsiMethod constructor) {
    PsiCodeBlock body = constructor.getBody();
    if (body != null) {
      PsiStatement[] statements = body.getStatements();
      if (statements.length == 1 && statements[0] instanceof PsiExpressionStatement) {
        PsiExpression expression = ((PsiExpressionStatement)statements[0]).getExpression();
        if (expression instanceof PsiMethodCallExpression) {
          PsiReferenceExpression methodExpr = ((PsiMethodCallExpression)expression).getMethodExpression();
          if (methodExpr != null) {
            PsiElement resolved = methodExpr.resolve();
            return resolved instanceof PsiMethod && ((PsiMethod)resolved).isConstructor();
          }
        }
      }
    }
    return false;
  }

  private boolean checkRecursive(PsiMethod method) {
    return checkCalls(method.getBody(), method);
  }

  private boolean checkCalls(PsiElement scope, PsiMethod method) {
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