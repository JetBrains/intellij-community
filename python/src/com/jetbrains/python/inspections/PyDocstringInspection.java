package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.console.PydevConsoleRunner;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 * Author: Alexey.Ivanov
 * Date:   10.03.2010
 * Time:   16:14:45
 */
public class PyDocstringInspection extends LocalInspectionTool {
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
    return PyBundle.message("INSP.NAME.docstring");
  }

  @NotNull
  @Override
  public String getShortName() {
    return "PyDocstringInspection";
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
    public void visitPyFile(PyFile node) {
      checkDocString(node);
    }

    @Override
    public void visitPyFunction(PyFunction node) {
      checkDocString(node);
    }

    @Override
    public void visitPyClass(PyClass node) {
      checkDocString(node);
    }

    private void checkDocString(PyDocStringOwner node) {
      if (PydevConsoleRunner.isInPydevConsole(node)) {
        return;
      }
      final PyStringLiteralExpression docStringExpression = node.getDocStringExpression();
      if (docStringExpression == null) {
        registerProblem(node, "Missing docstring"); // node?
      } else if ("".equals(docStringExpression.getStringValue().trim())) {
        registerProblem(docStringExpression, "Empty docstring");
      }
    }
  }
}
