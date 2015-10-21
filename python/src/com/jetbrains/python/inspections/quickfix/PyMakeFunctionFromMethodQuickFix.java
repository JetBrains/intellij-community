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
package com.jetbrains.python.inspections.quickfix;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.usageView.UsageInfo;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.imports.AddImportHelper;
import com.jetbrains.python.inspections.unresolvedReference.PyUnresolvedReferencesInspection;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.refactoring.PyRefactoringUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * User: ktisha
 */
public class PyMakeFunctionFromMethodQuickFix implements LocalQuickFix {
  public PyMakeFunctionFromMethodQuickFix() {
  }

  @NotNull
  public String getName() {
    return PyBundle.message("QFIX.NAME.make.function");
  }

  @NonNls
  @NotNull
  public String getFamilyName() {
    return getName();
  }

  public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
    final PsiElement element = descriptor.getPsiElement();
    final PyFunction problemFunction = PsiTreeUtil.getParentOfType(element, PyFunction.class);
    if (problemFunction == null) return;
    final PyClass containingClass = problemFunction.getContainingClass();
    if (containingClass == null) return;

    final List<UsageInfo> usages = PyRefactoringUtil.findUsages(problemFunction, false);
    final PyParameter[] parameters = problemFunction.getParameterList().getParameters();
    if (parameters.length > 0) {
      parameters[0].delete();
    }

    PsiElement copy = problemFunction.copy();
    problemFunction.delete();
    final PsiElement parent = containingClass.getParent();
    PyClass aClass = PsiTreeUtil.getTopmostParentOfType(containingClass, PyClass.class);
    if (aClass == null)
      aClass = containingClass;
    copy = parent.addBefore(copy, aClass);

    for (UsageInfo usage : usages) {
      final PsiElement usageElement = usage.getElement();
      if (usageElement instanceof PyReferenceExpression) {
        final PsiFile usageFile = usageElement.getContainingFile();
        updateUsage(copy, (PyReferenceExpression)usageElement, usageFile, !usageFile.equals(parent));
      }
    }
  }

  private static void updateUsage(@NotNull final PsiElement finalElement, @NotNull final PyReferenceExpression element,
                                  @NotNull final PsiFile usageFile, boolean addImport) {
    final PyExpression qualifier = element.getQualifier();
    if (qualifier == null) return;
    if (qualifier.getText().equals(PyNames.CANONICAL_SELF)) PyUtil.removeQualifier(element);
    if (qualifier instanceof PyCallExpression) {              // remove qualifier A().m()
      if (addImport)
        AddImportHelper.addImport((PsiNamedElement)finalElement, usageFile, element);

      PyUtil.removeQualifier(element);
      removeFormerImport(usageFile, addImport);
    }
    else {
      final PsiReference reference = qualifier.getReference();
      if (reference == null) return;

      final PsiElement resolved = reference.resolve();
      if (resolved instanceof PyTargetExpression) {  // qualifier came from assignment  a = A(); a.m()
        updateAssignment(element, resolved);
      }
      else if (resolved instanceof PyClass) {     //call with first instance argument A.m(A())
        PyUtil.removeQualifier(element);
        updateArgumentList(element);
      }
    }
  }

  private static void removeFormerImport(@NotNull final PsiFile usageFile, boolean addImport) {
    if (usageFile instanceof PyFile && addImport) {
      final LocalInspectionToolSession session = new LocalInspectionToolSession(usageFile, 0, usageFile.getTextLength());
      final PyUnresolvedReferencesInspection.Visitor visitor = new PyUnresolvedReferencesInspection.Visitor(null,
                                                                                                            session,
                                                                                                            Collections.<String>emptyList());
      usageFile.accept(new PyRecursiveElementVisitor() {
        @Override
        public void visitPyElement(PyElement node) {
          super.visitPyElement(node);
          node.accept(visitor);
        }
      });

      visitor.optimizeImports();
    }
  }

  private static void updateAssignment(PyReferenceExpression element, @NotNull final PsiElement resolved) {
    final PsiElement parent = resolved.getParent();
    if (parent instanceof PyAssignmentStatement) {
      final PyExpression value = ((PyAssignmentStatement)parent).getAssignedValue();
      if (value instanceof PyCallExpression) {
        final PyExpression callee = ((PyCallExpression)value).getCallee();
        if (callee instanceof PyReferenceExpression) {
          final PyExpression calleeQualifier = ((PyReferenceExpression)callee).getQualifier();
          if (calleeQualifier != null) {
            value.replace(calleeQualifier);
          }
          else {
            PyUtil.removeQualifier(element);
          }
        }
      }
    }
  }

  private static void updateArgumentList(@NotNull final PyReferenceExpression element) {
    final PyCallExpression callExpression = PsiTreeUtil.getParentOfType(element, PyCallExpression.class);
    if (callExpression == null) return;
    final PyArgumentList argumentList = callExpression.getArgumentList();
    if (argumentList == null) return;
    final PyExpression[] arguments = argumentList.getArguments();
    if (arguments.length > 0) {
      arguments[0].delete();
    }
  }
}
