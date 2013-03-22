package com.jetbrains.python.inspections.quickfix;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
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

    final PsiElement copy = assignment.copy();
    if (!addDefinition(copy, containingClass)) return;

    removeDefinition(assignment);
  }

  private static boolean addDefinition(PsiElement copy, PyClass containingClass) {
    PyFunction init = containingClass.findMethodByName(PyNames.INIT, true);

    if (init == null) {
      final PyStatementList classStatementList = containingClass.getStatementList();
      final PyStatement[] statements = classStatementList.getStatements();
      init = PyElementGenerator.getInstance(containingClass.getProject()).createFromText(LanguageLevel.forElement(containingClass),
                                                                                         PyFunction.class,
                                                                                         "def __init__(self):\n\t" +
                                                                                         copy.getText());
      if (statements.length > 0) {
        final PyStatement statement = statements[0];
        if (statement instanceof PyExpressionStatement &&
            ((PyExpressionStatement)statement).getExpression() == containingClass.getDocStringExpression())
          classStatementList.addAfter(init, statement);
        else
          classStatementList.addBefore(init, statement);
      }
      else {
        classStatementList.add(init);
      }
      return true;
    }
    final PyStatementList statementList = init.getStatementList();
    if (statementList == null) return false;

    final PyStatement[] statements = statementList.getStatements();
    if (statements.length == 1) {
      final PyStatement firstStatement = statements[0];
      if (firstStatement instanceof PyPassStatement) {
        firstStatement.replace(copy);
      }
      else
        statementList.addAfter(copy, statements[statements.length - 1]);
    }
    else
      statementList.addAfter(copy, statements[statements.length - 1]);
    return true;
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
