package com.jetbrains.python.actions;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

/**
 * User: catherine
 *
 * QuickFix to convert docstrings to the common form according to PEP-257
 * For consistency, always use """triple double quotes""" around docstrings.
 */
public class ConvertDocstringQuickFix implements LocalQuickFix {
  String myModificator = "";
  public ConvertDocstringQuickFix(String modificator) {
    myModificator = modificator;
  }

  @NotNull
  public String getName() {
    return PyBundle.message("QFIX.convert.single.quoted.docstring");
  }

  @NotNull
  public String getFamilyName() {
    return PyBundle.message("INSP.GROUP.python");
  }

  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PsiElement expression = descriptor.getPsiElement();
    if (expression instanceof PyStringLiteralExpression) {
      PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);

      String content = expression.getText().substring(myModificator.length());
      if (content.startsWith("'''") ) {
        content = content.substring(3, content.length()-3);
      } else {
        content = content.length() == 1 ? "" : content.substring(1, content.length()-1);
      }

      PyExpression newString = elementGenerator.createFromText(LanguageLevel.forElement(expression),
                                PyExpressionStatement.class, myModificator+"\"\"\"" + content + "\"\"\"").getExpression();
      expression.replace(newString);
    }
  }

}
