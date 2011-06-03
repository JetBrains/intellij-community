package com.jetbrains.python.inspections;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.SuppressIntentionAction;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.actions.DocstringQuickFix;
import com.jetbrains.python.actions.PySuppressInspectionFix;
import com.jetbrains.python.console.PydevConsoleRunner;
import com.jetbrains.python.documentation.EpydocString;
import com.jetbrains.python.documentation.PyDocumentationSettings;
import com.jetbrains.python.documentation.SphinxDocString;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
          TextRange tr = new TextRange(0,0);
          ProblemsHolder holder = getHolder();
          if (holder != null)
            holder.registerProblem(node, tr, PyBundle.message("INSP.no.docstring"));
          return;
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
        PyParameter[] realParams = ((PyFunction)pyDocStringOwner).getParameterList().getParameters();

        List<PyParameter> missingParams = getMissingParams(realParams, docstringParams, isClassMethod);
        boolean registered = false;
        if (!missingParams.isEmpty()) {
          for (PyParameter param : missingParams) {
            registerProblem(param, "Missing parameter " + param.getName() + " in docstring",
                            new DocstringQuickFix(param, null));
          }
          registered = true;
        }
        List<String> unexpectedParams = getUnexpectedParams(docstringParams, realParams, node);
        if (!unexpectedParams.isEmpty()) {
          for (String param : unexpectedParams) {
            ProblemsHolder holder = getHolder();
            int index = node.getText().indexOf("param "+param+":") +6;
            if (holder != null)
              holder.registerProblem(node, TextRange.create(index, index+param.length()),
                                   "Unexpected parameter " + param + " in docstring",
                                    new DocstringQuickFix(null, param));
          }
          registered = true;
        }
        return registered;
      }
      return false;
    }

    private List<String> getUnexpectedParams(List<String> docstringParams, PyParameter[] realParams, PyStringLiteralExpression node) {
      for (PyParameter p : realParams) {
        if (docstringParams.contains(p.getName())) {
          docstringParams.remove(p.getName());
        }
      }
      return docstringParams;
    }

    private List<PyParameter> getMissingParams(PyParameter[] realParams, List<String> docstringParams, boolean isClassMethod) {
      List<PyParameter> missing = new ArrayList<PyParameter>();
      boolean hasMissing = false;
      for (PyParameter p : realParams) {
        if ((!isClassMethod && !p.getText().equals(PyNames.CANONICAL_SELF)) ||
              (isClassMethod && !p.getText().equals("cls"))) {
          if (!docstringParams.contains(p.getName())) {
            hasMissing = true;
            missing.add(p);
          }
        }
      }
      return hasMissing? missing : Collections.<PyParameter>emptyList();
    }
  }
  @Override
  public SuppressIntentionAction[] getSuppressActions(@Nullable PsiElement element) {
    List<SuppressIntentionAction> result = new ArrayList<SuppressIntentionAction>();
    if (element != null) {
      if (PsiTreeUtil.getParentOfType(element, PyFunction.class) != null) {
        result.add(new PySuppressInspectionFix(getShortName().replace("Inspection", ""), "Suppress for function", PyFunction.class));
      }
      if (PsiTreeUtil.getParentOfType(element, PyClass.class) != null) {
        result.add(new PySuppressInspectionFix(getShortName().replace("Inspection", ""), "Suppress for class", PyClass.class));
      }
    }
    return result.toArray(new SuppressIntentionAction[result.size()]);
  }
}
