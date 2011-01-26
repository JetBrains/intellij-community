package com.jetbrains.python.inspections;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.actions.ListCreationQuickFix;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * User :catherine
 */
public class PyListCreationInspection extends PyInspection {
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.list.creation");
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new Visitor(holder);
  }

  private static class Visitor extends PyInspectionVisitor {

    public Visitor(final ProblemsHolder holder) {
      super(holder);
    }

    @Override
    public void visitPyAssignmentStatement(PyAssignmentStatement node) {
      if (node.getAssignedValue() instanceof PyListLiteralExpression) {
        if (node.getTargets().length != 1) {
          return;
        }
        final PyExpression target = node.getTargets()[0];
        String name = target.getName();
        if (name == null) {
          return;
        }
        PyExpression statement = null;
        PyExpressionStatement expressionStatement = PsiTreeUtil.getNextSiblingOfType(node, PyExpressionStatement.class);
        if (expressionStatement != null)
          statement = expressionStatement.getExpression();
        ListCreationQuickFix quickFix = null;
        boolean availableFix = false;
        while (statement instanceof PyCallExpression) {
          PyCallExpression callExpression = (PyCallExpression)statement;
          PyExpression callee = callExpression.getCallee();
          if (callee instanceof PyQualifiedExpression) {
            PyExpression qualifier = ((PyQualifiedExpression)callee).getQualifier();
            String funcName = ((PyQualifiedExpression)callee).getReferencedName();
            if (qualifier != null && qualifier.getText().equals(name) && "append".equals(funcName)) {
              PyArgumentList argList = callExpression.getArgumentList();
              if (argList != null) {
                for (PyExpression argument : argList.getArguments()) {
                  if (!availableFix) {
                    quickFix = new ListCreationQuickFix(node);
                    availableFix = true;
                  }
                }
                if(availableFix)
                  quickFix.addStatement(expressionStatement);
              }
            }
          }
          if (quickFix == null) {
            return;
          }
          expressionStatement = PsiTreeUtil.getNextSiblingOfType(expressionStatement, PyExpressionStatement.class);
          if (expressionStatement != null)
            statement = expressionStatement.getExpression();
          else
            statement = null;
        }
        
        if (availableFix) {
          registerProblem(node, "This list creation could be rewritten as a list literal", quickFix);
        }
      }
    }
  }
}
