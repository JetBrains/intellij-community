package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyQualifiedName;
import com.jetbrains.python.psi.search.PySuperMethodsSearch;
import com.jetbrains.python.testing.pytest.PyTestUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.regex.Pattern;

/**
 *
 * User : ktisha
 */
public class PyPep8NamingInspection extends PyInspection {
  public boolean ignoreOverriddenFunctions = true;
  public boolean ignoreTestFunctions = true;
  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                        boolean isOnTheFly,
                                        @NotNull LocalInspectionToolSession session) {
    return new Visitor(holder, session);
  }

  public class Visitor extends PyInspectionVisitor {
    Pattern LOWERCASE_REGEX = Pattern.compile("[_a-z][_a-z0-9]*");
    Pattern UPPERCASE_REGEX = Pattern.compile("[_A-Z][_A-Z0-9]*");
    Pattern MIXEDCASE_REGEX = Pattern.compile("_?[A-Z][a-zA-Z0-9]*");

    public Visitor(@NotNull final ProblemsHolder holder, LocalInspectionToolSession session) {
      super(holder, session);
    }

    @Override
    public void visitPyAssignmentStatement(PyAssignmentStatement node) {
      final PyFunction function = PsiTreeUtil.getParentOfType(node, PyFunction.class, true, PyClass.class);
      if (function == null) return;
      for (PyExpression expression : node.getTargets()) {
        final String name = expression.getName();
        if (name == null) continue;
        if (!LOWERCASE_REGEX.matcher(name).matches() && !name.startsWith("_")) {
          registerProblem(expression, "Variable in function should be lowercase", new PyRenameElementQuickFix());
        }
      }
    }

    @Override
    public void visitPyParameter(PyParameter node) {
      final String name = node.getName();
      if (name == null) return;
      if (!LOWERCASE_REGEX.matcher(name).matches()) {
        registerProblem(node, "Argument name should be lowercase", new PyRenameElementQuickFix());
      }
    }

    @Override
    public void visitPyFunction(PyFunction node) {
      final PyClass containingClass = node.getContainingClass();
      final PsiElement superMethod = PySuperMethodsSearch.search(node).findFirst();
      if (superMethod != null && ignoreOverriddenFunctions) return;
      if(containingClass != null && PyTestUtil.isPyTestClass(containingClass) && ignoreTestFunctions) return;
      final String name = node.getName();
      if (name == null) return;
      if (containingClass != null && name.startsWith("__") && name.endsWith("__")) {
        return;
      }
      if (!LOWERCASE_REGEX.matcher(name).matches()) {
        final ASTNode nameNode = node.getNameNode();
        if (nameNode != null)
          registerProblem(nameNode.getPsi(), "Function name should be lowercase", new PyRenameElementQuickFix());
      }
    }

    @Override
    public void visitPyClass(PyClass node) {
      final String name = node.getName();
      if (name == null) return;
      if (!MIXEDCASE_REGEX.matcher(name).matches()) {
        final ASTNode nameNode = node.getNameNode();
        if (nameNode != null)
          registerProblem(nameNode.getPsi(), "Class names should use CamelCase convention", new PyRenameElementQuickFix());
      }
    }

    @Override
    public void visitPyImportElement(PyImportElement node) {
      final String asName = node.getAsName();
      final PyQualifiedName importedQName = node.getImportedQName();
      if (importedQName == null) return;
      final String name = importedQName.toString();

      if (asName == null || name == null) return;
      if (UPPERCASE_REGEX.matcher(name).matches()) {
        if (!UPPERCASE_REGEX.matcher(asName).matches()) {
          registerProblem(node.getAsNameElement(), "Constant variable imported as non constant", new PyRenameElementQuickFix());
        }
      }
      else if (LOWERCASE_REGEX.matcher(name).matches()) {
        if (!LOWERCASE_REGEX.matcher(asName).matches()) {
          registerProblem(node.getAsNameElement(), "Lowercase variable imported as non lowercase", new PyRenameElementQuickFix());
        }
      }
      else if (LOWERCASE_REGEX.matcher(asName).matches()) {
        registerProblem(node.getAsNameElement(), "CamelCase variable imported as lowercase", new PyRenameElementQuickFix());
      }
      else if (UPPERCASE_REGEX.matcher(asName).matches()) {
        registerProblem(node.getAsNameElement(), "CamelCase variable imported as constant", new PyRenameElementQuickFix());
      }
    }
  }

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    MultipleCheckboxOptionsPanel panel = new MultipleCheckboxOptionsPanel(this);
    panel.addCheckbox("Ignore overridden functions", "ignoreOverriddenFunctions");
    panel.addCheckbox("Ignore test functions", "ignoreTestFunctions");
    return panel;
  }
}
