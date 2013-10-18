package com.jetbrains.python.inspections.quickfix;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

/**
 * User: catherine
 *
 * QuickFix to move misplaced docstring
 */
public class StatementEffectDocstringQuickFix implements LocalQuickFix {
  @NotNull
  public String getName() {
    return PyBundle.message("QFIX.statement.effect.move.docstring");
  }

  @NotNull
  public String getFamilyName() {
    return getName();
  }

  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PsiElement expression = descriptor.getPsiElement();
    if (expression instanceof PyStringLiteralExpression) {
      PyStatement st = PsiTreeUtil.getParentOfType(expression, PyStatement.class);
      if (st != null) {
        PyDocStringOwner parent = PsiTreeUtil.getParentOfType(expression, PyDocStringOwner.class);

        if (parent instanceof PyClass || parent instanceof PyFunction) {
          PyStatementList statementList = PsiTreeUtil.findChildOfType(parent, PyStatementList.class);
          if (statementList != null) {
            PyStatement[] statements = statementList.getStatements();
            if (statements.length > 0) {
              statementList.addBefore(st, statements[0]);
              st.delete();
            }
          }
        }
      }
    }
  }

}
