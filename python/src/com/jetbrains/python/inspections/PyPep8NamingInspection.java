/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.codeInsight.dataflow.scope.Scope;
import com.jetbrains.python.inspections.quickfix.PyRenameElementQuickFix;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.search.PySuperMethodsSearch;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.regex.Pattern;

/**
 * User : ktisha
 */
public class PyPep8NamingInspection extends PyInspection {
  private static final Pattern LOWERCASE_REGEX = Pattern.compile("[_\\p{javaLowerCase}][_\\p{javaLowerCase}0-9]*");
  private static final Pattern UPPERCASE_REGEX = Pattern.compile("[_\\p{javaUpperCase}][_\\p{javaUpperCase}0-9]*");
  private static final Pattern MIXEDCASE_REGEX = Pattern.compile("_?[\\p{javaUpperCase}][\\p{javaLowerCase}\\p{javaUpperCase}0-9]*");

  public boolean ignoreOverriddenFunctions = true;
  public boolean ignoreDescendantsOfStandardClasses = false;

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                        boolean isOnTheFly,
                                        @NotNull LocalInspectionToolSession session) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      ignoreDescendantsOfStandardClasses = true;
    }
    return new Visitor(holder, session);
  }

  public class Visitor extends PyInspectionVisitor {
    public Visitor(@NotNull final ProblemsHolder holder, LocalInspectionToolSession session) {
      super(holder, session);
    }

    @Override
    public void visitPyAssignmentStatement(PyAssignmentStatement node) {
      final PyFunction function = PsiTreeUtil.getParentOfType(node, PyFunction.class, true, PyClass.class);
      if (function == null) return;
      final Scope scope = ControlFlowCache.getScope(function);
      for (PyExpression expression : node.getTargets()) {
        final String name = expression.getName();
        if (name == null || scope.isGlobal(name)) continue;
        if (expression instanceof PyTargetExpression) {
          final PyExpression qualifier = ((PyTargetExpression)expression).getQualifier();
          if (qualifier != null) {
            return;
          }
        }
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
    public void visitPyFunction(PyFunction function) {
      final PyClass containingClass = function.getContainingClass();
      if (ignoreOverriddenFunctions && isOverriddenMethod(function)) return;
      final String name = function.getName();
      if (name == null) return;
      if (containingClass != null && PyUtil.isSpecialName(name)) {
        return;
      }
      if (containingClass != null && ignoreDescendantsOfStandardClasses && isStandardClassDescendant(containingClass)) {
        return;
      }
      if (!LOWERCASE_REGEX.matcher(name).matches()) {
        final ASTNode nameNode = function.getNameNode();
        if (nameNode != null)
          registerProblem(nameNode.getPsi(), "Function name should be lowercase", new PyRenameElementQuickFix());
      }
    }

    private boolean isOverriddenMethod(@NotNull PyFunction function) {
      return PySuperMethodsSearch.search(function).findFirst() != null;
    }

    private boolean isStandardClassDescendant(@NotNull final PyClass cls) {
      return ContainerUtil.exists(cls.getAncestorClasses(myTypeEvalContext), new Condition<PyClass>() {
        @Override
        public boolean value(PyClass ancestor) {
          final PsiFile ancestorsModule = ancestor.getContainingFile();
          final Sdk sdk = PyBuiltinCache.findSdkForFile(ancestorsModule);
          if (PythonSdkType.isStdLib(ancestorsModule.getVirtualFile(), sdk) && !PyUtil.isObjectClass(cls)) {
            return true;
          }
          return false;
        }
      });
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
      final QualifiedName importedQName = node.getImportedQName();
      if (importedQName == null) return;
      final String name = importedQName.getLastComponent();

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
    panel.addCheckbox("Ignore descendants of standard classes", "ignoreDescendantsOfStandardClasses");
    return panel;
  }
}
