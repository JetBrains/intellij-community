package com.intellij.refactoring.introduceField;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pass;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.introduceVariable.IntroduceVariableBase;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author dsl
 */
public class ElementToWorkOn {
  public static final Key<PsiElement> PARENT = Key.create("PARENT");
  private final PsiExpression myExpression;
  private final PsiLocalVariable myLocalVariable;
  public static final Key<PsiElement> PREFIX = Key.create("prefix");
  public static final Key<PsiElement> SUFFIX = Key.create("suffix");

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

  public static void processElementToWorkOn(final Editor editor, final PsiFile file, final String refactoringName, final String helpId, final Project project, final Pass<ElementToWorkOn> processor) {
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
          final List<PsiExpression> expressions = new ArrayList<PsiExpression>();
          PsiExpression expression = PsiTreeUtil.getParentOfType(elementAt, PsiExpression.class);
          while (expression != null) {
            if (!(expression instanceof PsiReferenceExpression && expression.getParent() instanceof PsiMethodCallExpression)) {
              expressions.add(expression);
            }
            expression = PsiTreeUtil.getParentOfType(expression, PsiExpression.class);
          }
          if (expressions.isEmpty()) {
            editor.getSelectionModel().selectLineAtCaret();
          } else if (expressions.size() == 1) {
            expr = expressions.get(0);
          } else {
            final ElementToWorkOn[] el = new ElementToWorkOn[1];
            JBPopupFactory.getInstance().createListPopup(new BaseListPopupStep<PsiExpression>("Expressions", expressions) {
              @Override
              public PopupStep onChosen(final PsiExpression selectedValue, final boolean finalChoice) {
                processor.pass(getElementToWorkOn(editor, file, refactoringName, helpId, project, null, selectedValue));
                return FINAL_CHOICE;
              }

              @NotNull
              @Override
              public String getTextFor(final PsiExpression value) {
                return value.getText();
              }
            }).showInBestPositionFor(editor);

          }
        }
      }
    }


    processor.pass(getElementToWorkOn(editor, file, refactoringName, helpId, project, localVar, expr));
  }

  private static ElementToWorkOn getElementToWorkOn(final Editor editor, final PsiFile file,
                                                    final String refactoringName,
                                                    final String helpId,
                                                    final Project project, PsiLocalVariable localVar, PsiExpression expr) {
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
      expr = IntroduceVariableBase.getSelectedExpression(project, file, startOffset, endOffset);
    }

    if (localVar == null && expr == null) {
      String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("error.wrong.caret.position.local.name"));
      CommonRefactoringUtil.showErrorHint(project, editor, message, refactoringName, helpId);
      return null;
    }
    return new ElementToWorkOn(localVar, expr);
  }
}
