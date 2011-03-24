package com.jetbrains.python.actions;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

/**
 * User : catherine
 * Quick Fix to replace function call of built-in function "set" with
 * set literal if applicable
 */
public class ReplaceFunctionWithSetLiteralQuickFix implements LocalQuickFix {
  @Override
  @NotNull
  public String getName() {
    return PyBundle.message("QFIX.replace.function.set.with.literal");
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return PyBundle.message("INSP.GROUP.python");
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
    PsiElement functionCall = descriptor.getPsiElement();
    StringBuilder str = new StringBuilder("{");
    if (functionCall instanceof PyCallExpression) {
      PyExpression[] arguments = ((PyCallExpression)functionCall).getArguments();
      if (arguments.length > 0) {
        PyExpression argument= arguments[0];
        PyElement[] elements = {};
        if (argument instanceof PySequenceExpression)
          elements = ((PySequenceExpression)argument).getElements();
        if (argument instanceof PyParenthesizedExpression) {
          PyExpression tuple = ((PyParenthesizedExpression)argument).getContainedExpression();
          if (tuple instanceof PyTupleExpression)
            elements = ((PyTupleExpression)(tuple)).getElements();
        }
        for (int i = 0; i != elements.length; ++i) {
          PyElement e = elements[i];
          str.append(e.getText());
          if (i != elements.length-1)
            str.append(", ");
        }
      }
      str.append("}");
      functionCall.replace(elementGenerator.createFromText(LanguageLevel.forElement(functionCall), PyExpressionStatement.class,
                                                             str.toString()).getExpression());
    }
  }
}
