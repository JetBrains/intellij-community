package com.jetbrains.python.inspections;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.actions.ConvertDocstringQuickFix;
import com.jetbrains.python.psi.PyDocStringOwner;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * User: catherine
 *
 * Inspection to detect docstrings not using triple double-quoted string
 */
public class PySingleQuotedDocstringInspection extends PyInspection {

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.single.quoted.docstring");
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
    public void visitPyStringLiteralExpression(final PyStringLiteralExpression string) {
      String stringText = string.getText();
      final PyDocStringOwner docStringOwner = PsiTreeUtil.getParentOfType(string, PyDocStringOwner.class);
      if (docStringOwner != null) {
        if (docStringOwner.getDocStringExpression() == string)  {
          if (!stringText.startsWith("\"\"\"") && !stringText.endsWith("\"\"\""))
            registerProblem(string, "Triple double-quoted strings should be used for docstrings.", new ConvertDocstringQuickFix());
        }
      }
    }
  }
}
