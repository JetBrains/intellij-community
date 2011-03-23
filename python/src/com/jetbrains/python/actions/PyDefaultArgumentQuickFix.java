package com.jetbrains.python.actions;

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
 * QuickFix to replace mutable default argument. For instance,
 * def foo(args=[]):
     pass
 * replace with:
 * def foo(args=None):
     if not args: args = []
     pass
 */
public class PyDefaultArgumentQuickFix implements LocalQuickFix {

  @Override
  @NotNull
  public String getName() {
    return PyBundle.message("QFIX.default.argument");
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return PyBundle.message("INSP.GROUP.python");
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PsiElement defaultValue = descriptor.getPsiElement();
    PsiElement param = PsiTreeUtil.getParentOfType(defaultValue, PyNamedParameter.class);
    PyFunction function = PsiTreeUtil.getParentOfType(defaultValue, PyFunction.class);
    String defName = PsiTreeUtil.getParentOfType(defaultValue, PyNamedParameter.class).getName();
    if (function != null) {
      PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
      PyStatementList list = function.getStatementList();
      if (list != null) {
        PyParameterList paramList = function.getParameterList();

        StringBuilder str = new StringBuilder("def foo(");
        int size = paramList.getParameters().length;
        for (int i = 0; i != size; ++i) {
          PyParameter p = paramList.getParameters()[i];
          if (p == param)
            str.append(defName).append("=None");
          else
            str.append(p.getText());
          if (i != size-1)
            str.append(", ");
        }
        str.append("):\n\tpass");
        PyIfStatement ifStatement = elementGenerator.createFromText(LanguageLevel.forElement(function), PyIfStatement.class,
                                                  "if not " + defName + ": " + defName + " = " + defaultValue.getText());

        PyStatement firstStatement = list.getStatements()[0];
        PyStringLiteralExpression docString = function.getDocStringExpression();
        if (docString != null)
          list.addAfter(ifStatement, firstStatement);
        else
          list.addBefore(ifStatement, firstStatement);
        paramList.replace(elementGenerator.createFromText(LanguageLevel.forElement(defaultValue),
                                                                   PyFunction.class, str.toString()).getParameterList());
      }
    }
  }
}
