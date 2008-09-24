package com.intellij.refactoring.introduceField;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.util.IncorrectOperationException;

/**
 * @author dsl
 */
public class ElementToWorkOn {
  public static final Key<PsiElement> PARENT = Key.create("PARENT");
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
      PsiElement element = TargetElementUtilBase.findTargetElement(editor, TargetElementUtilBase
        .ELEMENT_NAME_ACCEPTED | TargetElementUtilBase
        .REFERENCED_ELEMENT_ACCEPTED | TargetElementUtilBase
        .LOOKUP_ITEM_ACCEPTED);
      if (element instanceof PsiLocalVariable) {
        localVar = (PsiLocalVariable) element;
        final PsiElement elementAt = file.findElementAt(editor.getCaretModel().getOffset());
        if (elementAt instanceof PsiIdentifier && elementAt.getParent() instanceof PsiReferenceExpression) {
          expr = (PsiExpression) elementAt.getParent();
        }
      } else {
        final PsiElement elementAt = file.findElementAt(editor.getCaretModel().getOffset());
        final PsiLocalVariable variable = PsiTreeUtil.getParentOfType(elementAt, PsiLocalVariable.class);
        if (variable != null) {
          localVar = variable;
        } else {
          PsiExpression expression = PsiTreeUtil.getParentOfType(elementAt, PsiExpression.class);
          if (expression != null) {
            expr = expression;
          } else {
            editor.getSelectionModel().selectLineAtCaret();
          }
        }
      }
    }


    int startOffset = 0;
    int endOffset = 0;
    if (localVar == null && expr == null) {
      startOffset = editor.getSelectionModel().getSelectionStart();
      endOffset = editor.getSelectionModel().getSelectionEnd();
      expr = CodeInsightUtil.findExpressionInRange(file, startOffset, endOffset);
      if (expr == null) {
        PsiIdentifier ident = CodeInsightUtil.findElementInRange(file, startOffset, endOffset, PsiIdentifier.class);
        if (ident != null) {
          localVar = PsiTreeUtil.getParentOfType(ident, PsiLocalVariable.class);
        }
      }
    }

    if (expr == null && localVar == null) {
      PsiElement[] statements = CodeInsightUtil.findStatementsInRange(file, startOffset, endOffset);
      if (statements.length == 1 && statements[0] instanceof PsiExpressionStatement) {
        expr = ((PsiExpressionStatement)statements[0]).getExpression();
      }
      else if (statements.length == 1 && statements[0] instanceof PsiDeclarationStatement) {
        PsiDeclarationStatement decl = (PsiDeclarationStatement)statements[0];
        PsiElement[] declaredElements = decl.getDeclaredElements();
        if (declaredElements.length == 1 && declaredElements[0] instanceof PsiLocalVariable) {
          localVar = (PsiLocalVariable)declaredElements[0];
        }
      }
    }
    if (localVar == null && expr == null) {
      try {
        expr = JavaPsiFacade.getInstance(project).getElementFactory()
          .createExpressionFromText(file.getText().subSequence(startOffset, endOffset).toString(), file);
        expr.putUserData(PARENT, file.findElementAt(startOffset));
      }
      catch (IncorrectOperationException e) {
        expr = null;
      }
    }

    if (localVar == null && expr == null) {
      String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("error.wrong.caret.position.local.name"));
      CommonRefactoringUtil.showErrorHint(project, editor, message, refactoringName, helpId);
      return null;
    }
    return new ElementToWorkOn(localVar, expr);
  }
}
