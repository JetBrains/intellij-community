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
  PyElement[] myElements;
  public ReplaceFunctionWithSetLiteralQuickFix(PyElement[] elements) {
    myElements = elements;
  }

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
    for (int i = 0; i != myElements.length; ++i) {
      PyElement e = myElements[i];
      str.append(e.getText());
      if (i != myElements.length-1)
        str.append(", ");
    }
    str.append("}");
    functionCall.replace(elementGenerator.createFromText(LanguageLevel.forElement(functionCall), PyExpressionStatement.class,
                                                             str.toString()).getExpression());
  }
}
