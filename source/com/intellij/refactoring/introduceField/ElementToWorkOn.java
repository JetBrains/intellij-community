package com.intellij.refactoring.introduceField;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.refactoring.util.RefactoringMessageUtil;

/**
 * @author dsl
 */
public class ElementToWorkOn {
  private final PsiExpression myExpression;
  private final PsiLocalVariable myLocalVariable;

  private ElementToWorkOn(PsiLocalVariable localVariable, PsiExpression expr) {
    myLocalVariable = localVariable;
    myExpression = expr;
  }

  public PsiExpression getExpression() {
    return myExpression;
  }

  public PsiLocalVariable getLocalVariable() {
    return myLocalVariable;
  }

  public boolean isInvokedOnDeclaration() {
    return myExpression == null;
  }

  public static ElementToWorkOn getElementToWorkOn(Editor editor, PsiFile file, String refactoringName, String helpId, Project project) {
    PsiLocalVariable localVar = null;
    PsiExpression expr = null;

    if (!editor.getSelectionModel().hasSelection()) {
      PsiElement element =
              TargetElementUtil.findTargetElement(editor,
                      TargetElementUtil.ELEMENT_NAME_ACCEPTED
              | TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED
              | TargetElementUtil.LOOKUP_ITEM_ACCEPTED);
      if (element instanceof PsiLocalVariable) {
        localVar = (PsiLocalVariable) element;
        final PsiElement elementAt = file.findElementAt(editor.getCaretModel().getOffset());
        if (elementAt instanceof PsiIdentifier && elementAt.getParent() instanceof PsiReferenceExpression) {
          expr = (PsiExpression) elementAt.getParent();
        }
      } else {
        editor.getSelectionModel().selectLineAtCaret();
      }
    }


    int startOffset = 0;
    int endOffset = 0;
    if (localVar == null) {
      startOffset = editor.getSelectionModel().getSelectionStart();
      endOffset = editor.getSelectionModel().getSelectionEnd();
      expr = CodeInsightUtil.findExpressionInRange(file, startOffset, endOffset);
    }

    if (expr == null && localVar == null) {
      PsiElement[] statements = CodeInsightUtil.findStatementsInRange(file, startOffset, endOffset);
      if (statements != null && statements.length == 1 && statements[0] instanceof PsiExpressionStatement) {
        expr = ((PsiExpressionStatement) statements[0]).getExpression();
      } else if (statements != null && statements.length == 1 && statements[0] instanceof PsiDeclarationStatement) {
        PsiDeclarationStatement decl = (PsiDeclarationStatement) statements[0];
        PsiElement[] declaredElements = decl.getDeclaredElements();
        if (declaredElements.length == 1 && declaredElements[0] instanceof PsiLocalVariable) {
          localVar = (PsiLocalVariable) declaredElements[0];
        }
      }
    }

    if (localVar == null && expr == null) {
      String message =
              "Cannot perform the refactoring.\n" +
              "Select expression or position the caret on a name of local variable.";
      RefactoringMessageUtil.showErrorMessage(refactoringName, message, helpId, project);
      return null;
    }
    return new ElementToWorkOn(localVar, expr);
  }
}
