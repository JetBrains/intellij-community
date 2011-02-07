package com.jetbrains.python.actions;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyReferenceExpression;
import org.jetbrains.annotations.NotNull;

/**
 * User: catherine
 *
 * QuickFix to replace statement that has no effect with function call
 */
public class StatementEffectFunctionCallQuickFix implements LocalQuickFix {

  public StatementEffectFunctionCallQuickFix() {
  }

  @NotNull
  public String getName() {
    return PyBundle.message("QFIX.statement.effect");
  }

  @NotNull
  public String getFamilyName() {
    return PyBundle.message("INSP.GROUP.python");
  }

  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PsiElement expression = descriptor.getPsiElement();
    if (expression != null && expression.isWritable() && expression instanceof PyReferenceExpression) {
      PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
      if ("print".equals(expression.getText()))
        replacePrint(expression, elementGenerator);
      else
        expression.replace(elementGenerator.createCallExpression(LanguageLevel.forElement(expression), expression.getText()));
    }
  }

  private static void replacePrint(PsiElement expression, PyElementGenerator elementGenerator) {
    StringBuilder stringBuilder = new StringBuilder("print (");

    PsiElement whiteSpace = expression.getParent().getNextSibling();
    PsiElement next = null;
    if (whiteSpace instanceof PsiWhiteSpace) {
      if (!whiteSpace.getText().contains("\n"))
        next = whiteSpace.getNextSibling();
    }
    else
      next = whiteSpace;

    if (next != null) {
      stringBuilder.append(next.getText());
      next.delete();
    }
    stringBuilder.append(")");
    expression.replace(elementGenerator.createFromText(LanguageLevel.forElement(expression), PyExpression.class,
                                                       stringBuilder.toString()));
  }
}
