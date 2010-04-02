package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 * Author: Alexey.Ivanov
 * Date:   01.04.2010
 * Time:   17:36:42
 */
public class PyByteLiteralInspection extends LocalInspectionTool {
  @Nls
  @NotNull
  @Override
  public String getGroupDisplayName() {
    return PyBundle.message("INSP.GROUP.python");
  }

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.byte.literal");
  }

  @NotNull
  @Override
  public String getShortName() {
    return "PyByteLiteralInspection";
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
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
    public void visitPyStringLiteralExpression(PyStringLiteralExpression node) {
      if (Character.toLowerCase(node.getText().charAt(0)) == 'b') {
        String value = node.getStringValue();
        int length = value.length();
        for (int i = 0; i < length; ++i) {
          char c = value.charAt(i);
          if (((int) c) > 127) {
            registerProblem(node, "Byte literal contains characters > 127");
          }
        }
      }
    }
  }
}
