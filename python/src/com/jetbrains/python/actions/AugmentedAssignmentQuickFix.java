package com.jetbrains.python.actions;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyAugAssignmentStatementImpl;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * User: catherine
 *
 * QuickFix to replace assignment that can be replaced with augmented assignment.
 * for instance, i = i + 1   --> i +=1
 */
public class AugmentedAssignmentQuickFix implements LocalQuickFix {

  public AugmentedAssignmentQuickFix() {
  }

  @NotNull
  public String getName() {
    return PyBundle.message("QFIX.augment.assignment");
  }

  @NotNull
  public String getFamilyName() {
    return PyBundle.message("INSP.GROUP.python");
  }

  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PsiElement element = descriptor.getPsiElement();

    if (element instanceof PyAssignmentStatement && element.isWritable()) {
      final PyAssignmentStatement statement = (PyAssignmentStatement)element;

      final PyExpression target = statement.getLeftHandSideExpression();
      final PyBinaryExpression expression = (PyBinaryExpression)statement.getAssignedValue();
      if (expression == null) return;
      PyExpression leftExpression = expression.getLeftExpression();
      PyExpression rightExpression = expression.getRightExpression();
      if (rightExpression instanceof PyParenthesizedExpression)
        rightExpression = ((PyParenthesizedExpression)rightExpression).getContainedExpression();
      if (target != null && rightExpression != null) {
        final String targetText = target.getText();
        final String rightText = rightExpression.getText();
        if (rightText.equals(targetText)) {
          final PyExpression tmp = rightExpression;
          rightExpression = leftExpression;
          leftExpression = tmp;
        }
        final List<PsiComment> comments = PsiTreeUtil.getChildrenOfTypeAsList(statement, PsiComment.class);

        if ((leftExpression instanceof PyReferenceExpression || leftExpression instanceof PySubscriptionExpression)) {
          if (leftExpression.getText().equals(targetText)) {
            if (rightExpression instanceof PyNumericLiteralExpression || rightExpression instanceof PyStringLiteralExpression
              || rightExpression instanceof PyReferenceExpression || isPercentage(rightExpression) || isCompound(rightExpression)
              || isMathOperation(rightExpression, expression.getOperator())) {

              final PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
              final StringBuilder stringBuilder = new StringBuilder();
              final PsiElement psiOperator = expression.getPsiOperator();
              if (psiOperator == null) return;
              stringBuilder.append(targetText).append(" ").
                  append(psiOperator.getText()).append("= ").append(rightText);
              final PyAugAssignmentStatementImpl augAssignment = elementGenerator.createFromText(LanguageLevel.forElement(element),
                                                            PyAugAssignmentStatementImpl.class, stringBuilder.toString());
              for (PsiComment comment : comments)
                augAssignment.add(comment);
              statement.replace(augAssignment);
            }
          }
        }
      }
    }
  }

  private static boolean isCompound(final PyExpression rightExpression) {
    if (rightExpression instanceof PyCallExpression) {
      final PyType type = rightExpression.getType(TypeEvalContext.fast());
      if (type != null && type.isBuiltin(TypeEvalContext.fast()) &&
          ("int".equals(type.getName()) || "str".equals(type.getName()))) return true;
    }
    return false;
  }

  private static boolean isPercentage(final PyExpression rightExpression) {
    return (rightExpression instanceof PyBinaryExpression &&
              ((PyBinaryExpression)rightExpression).getLeftExpression() instanceof PyStringLiteralExpression &&
              ((PyBinaryExpression)rightExpression).getOperator() == PyTokenTypes.PERC);
  }

  private static boolean isMathOperation(final PyExpression rightExpression, final PyElementType mainOperator) {
    final TokenSet first = TokenSet.create(PyTokenTypes.EXP, PyTokenTypes.FLOORDIV);
    final TokenSet second = TokenSet.create(PyTokenTypes.MULT, PyTokenTypes.DIV, PyTokenTypes.PERC);
    final TokenSet third = TokenSet.create(PyTokenTypes.PLUS, PyTokenTypes.MINUS);
    if (rightExpression instanceof PyBinaryExpression){
      final PyElementType operator = ((PyBinaryExpression)rightExpression).getOperator();
      if (third.contains(mainOperator))
        return true;
      else if (second.contains(mainOperator) && (second.contains(operator) || first.contains(operator)))
        return true;
    }
    return false;
  }
}
