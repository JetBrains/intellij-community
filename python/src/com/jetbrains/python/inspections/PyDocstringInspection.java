package com.jetbrains.python.inspections;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.console.PydevConsoleRunner;
import com.jetbrains.python.documentation.PyDocumentationSettings;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

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
      else {
        checkParameters(node, docStringExpression);
      }
    }

    private void checkParameters(PyDocStringOwner pyDocStringOwner, PyStringLiteralExpression node) {
      String str = node.getStringValue();
      PyDocumentationSettings documentationSettings = PyDocumentationSettings.getInstance(node.getProject());
      String prefix;
      if (documentationSettings.isEpydocFormat())
        prefix = "@param";
      else if (documentationSettings.isReSTFormat())
        prefix = ":param";
      else
        return;

      Set<String> params = new HashSet<String>();
      String[] strs = str.split("[\n ]");

      int i = 0;
      while (i != strs.length) {
        if (strs[i].equals(prefix) && strs[i+1].endsWith(":")) {
          ++i;
          params.add(strs[i].substring(0, strs[i].length()-1));
        }
        ++i;
      }
      if (pyDocStringOwner instanceof PyFunction) {
        PyParameter[] realParams = ((PyFunction)pyDocStringOwner).getParameterList().getParameters();
        StringBuilder missingParams = new StringBuilder("Missing parameters ");
        boolean hasProblem = false;
        for (PyParameter p : realParams) {
          if (!params.contains(p.getText())) {
            hasProblem = true;
            missingParams.append(p.getText()).append(", ");
          }
        }
        missingParams.delete(missingParams.length()-2, missingParams.length());
        missingParams.append(" in docstring.");
        if (realParams.length >= params.size()) {
          if (hasProblem)
            registerProblem(node, missingParams.toString());
        }
        else {
          registerProblem(node, "Unexpected parameters in docstring");
        }
      }
    }
  }
}
