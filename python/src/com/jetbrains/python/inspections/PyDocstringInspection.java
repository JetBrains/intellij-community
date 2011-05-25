package com.jetbrains.python.inspections;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.actions.DocstringQuickFix;
import com.jetbrains.python.console.PydevConsoleRunner;
import com.jetbrains.python.documentation.EpydocString;
import com.jetbrains.python.documentation.PyDocumentationSettings;
import com.jetbrains.python.documentation.SphinxDocString;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
      else {
        boolean registered = checkParameters(node, docStringExpression);
        if (!registered && StringUtil.isEmptyOrSpaces(docStringExpression.getStringValue())) {
          registerProblem(docStringExpression, PyBundle.message("INSP.empty.docstring"));
        }
      }
    }

    private boolean checkParameters(PyDocStringOwner pyDocStringOwner, PyStringLiteralExpression node) {
      PyDocumentationSettings documentationSettings = PyDocumentationSettings.getInstance(node.getProject());
      List<String> docstringParams;
      if (documentationSettings.isEpydocFormat(node.getContainingFile()))
        docstringParams = new EpydocString(node.getText()).getParameters();
      else if (documentationSettings.isReSTFormat(node.getContainingFile()))
        docstringParams = new SphinxDocString(node.getText()).getParameters();
      else
        return false;

      if (pyDocStringOwner instanceof PyFunction) {
        PyDecoratorList decoratorList = ((PyFunction)pyDocStringOwner).getDecoratorList();
        boolean isClassMethod = false;
        if (decoratorList != null)
          isClassMethod = decoratorList.findDecorator(PyNames.CLASSMETHOD) != null;
        PyParameter[] tmp = ((PyFunction)pyDocStringOwner).getParameterList().getParameters();
        List<String> realParams = new ArrayList<String>();
        for (PyParameter p : tmp) {
          if ((!isClassMethod && !p.getText().equals(PyNames.CANONICAL_SELF)) ||
              (isClassMethod && !p.getText().equals("cls")))
            realParams.add(p.getText());
        }

        List<String> missingParams = getMissingParams(realParams, docstringParams);
        String missingString = getMissingText("Missing", missingParams);
        List<String> unexpectedParams = getMissingParams(docstringParams, realParams);
        String unexpectedString = getMissingText("Unexpected", unexpectedParams);

        String problem = missingString + " " + unexpectedString;
        if (!problem.equals(" ")) {
          registerProblem(node, problem, new DocstringQuickFix(missingParams, unexpectedParams));
          return true;
        }
      }
      return false;
    }
    private List<String> getMissingParams(List<String> realParams, List<String> docstringParams) {
       List<String> missing = new ArrayList<String>();
      boolean hasMissing = false;
      for (String p : realParams) {
        if (!docstringParams.contains(p)) {
          hasMissing = true;
          missing.add(p);
        }
      }
      return hasMissing? missing : Collections.<String>emptyList();
    }

    private String getMissingText(String prefix, List<String> missing) {
      if (missing.isEmpty())
        return "";
      StringBuilder missingString = new StringBuilder(prefix);
      missingString.append(" parameters ");
      for (String param : missing) {
        missingString.append(param).append(", ");
      }
      missingString.delete(missingString.length()-2, missingString.length());
      missingString.append(" in docstring.");
      return missingString.toString();
    }
  }
}
