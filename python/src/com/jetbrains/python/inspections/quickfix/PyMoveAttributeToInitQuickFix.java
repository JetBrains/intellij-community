package com.jetbrains.python.inspections.quickfix;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Function;
import com.intellij.util.FunctionUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * User: ktisha
 */
public class PyMoveAttributeToInitQuickFix implements LocalQuickFix {

  public PyMoveAttributeToInitQuickFix() {
  }

  @NotNull
  public String getName() {
    return PyBundle.message("QFIX.move.attribute");
  }

  @NonNls
  @NotNull
  public String getFamilyName() {
    return getName();
  }

  public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
    final PsiElement element = descriptor.getPsiElement();
    if (!(element instanceof PyTargetExpression)) return;
    final PyTargetExpression targetExpression = (PyTargetExpression)element;

    final PyClass containingClass = targetExpression.getContainingClass();
    final PyAssignmentStatement assignment = PsiTreeUtil.getParentOfType(element, PyAssignmentStatement.class);
    if (containingClass == null || assignment == null) return;

    final Function<String, PyStatement> callback = FunctionUtil.<String, PyStatement>constant(assignment);
    AddFieldQuickFix.addFieldToInit(project, containingClass, ((PyTargetExpression)element).getName(), callback);
    removeDefinition(assignment);
  }

  private static boolean removeDefinition(PyAssignmentStatement assignment) {
    final PyStatementList statementList = PsiTreeUtil.getParentOfType(assignment, PyStatementList.class);
    if (statementList == null) return false;

    if (statementList.getStatements().length == 1) {
      final PyPassStatement passStatement = PyElementGenerator.getInstance(assignment.getProject()).createPassStatement();
      statementList.addBefore(passStatement, assignment);
    }
    assignment.delete();
    return true;
  }
}
