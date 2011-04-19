package com.jetbrains.python.inspections;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.console.PydevConsoleRunner;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Alexey.Ivanov
 */
public class PyDocstringInspection extends PyInspection {
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.docstring");
  }

  @Override
  public boolean isEnabledByDefault() {
    return false;
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
      final String name = node.getName();
      if (name != null && !name.startsWith("_")) checkDocString(node);
    }

    @Override
    public void visitPyClass(PyClass node) {
      final String name = node.getName();
      if (name != null && !name.startsWith("_")) checkDocString(node);
    }

    private void checkDocString(PyDocStringOwner node) {
      if (PydevConsoleRunner.isInPydevConsole(node)) {
        return;
      }
      final PyStringLiteralExpression docStringExpression = node.getDocStringExpression();
      if (docStringExpression == null) {
        PsiElement marker = null;
        if (node instanceof PyClass) {
          final ASTNode n = ((PyClass)node).getNameNode();
          if (n != null) marker = n.getPsi();
        }
        else if (node instanceof PyFunction) {
          final ASTNode n = ((PyFunction)node).getNameNode();
          if (n != null) marker = n.getPsi();
        }
        else if (node instanceof PyFile) {
          marker = node.findElementAt(0);
        }
        if (marker == null) marker = node;
        registerProblem(marker, PyBundle.message("INSP.no.docstring"));
      }
      else if (StringUtil.isEmptyOrSpaces(docStringExpression.getStringValue())) {
        registerProblem(docStringExpression, PyBundle.message("INSP.empty.docstring"));
      }
    }
  }
}
