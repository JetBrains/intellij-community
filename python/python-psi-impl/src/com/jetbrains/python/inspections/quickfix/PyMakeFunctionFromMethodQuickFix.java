// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.codeInsight.PyPsiIndexUtil;
import com.jetbrains.python.codeInsight.imports.AddImportHelper;
import com.jetbrains.python.inspections.unresolvedReference.PyUnresolvedReferencesVisitor;
import com.jetbrains.python.inspections.unresolvedReference.SimplePyUnresolvedReferencesInspection;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class PyMakeFunctionFromMethodQuickFix implements LocalQuickFix {
  public PyMakeFunctionFromMethodQuickFix() {
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return PyPsiBundle.message("QFIX.NAME.make.function");
  }

  @Override
  public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
    final PsiElement element = descriptor.getPsiElement();
    final PyFunction problemFunction = PsiTreeUtil.getParentOfType(element, PyFunction.class);
    if (problemFunction == null) return;
    final PyClass containingClass = problemFunction.getContainingClass();
    if (containingClass == null) return;

    final List<UsageInfo> usages = PyPsiIndexUtil.findUsages(problemFunction, false);
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
    if (qualifier.getText().equals(PyNames.CANONICAL_SELF)) {
      PyUtil.removeQualifier(element);
      return;
    }
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
      final PyUnresolvedReferencesVisitor visitor = new SimplePyUnresolvedReferencesInspection.Visitor(null,
                                                                                                       session);
      session.putUserData(PyUnresolvedReferencesVisitor.INSPECTION, new SimplePyUnresolvedReferencesInspection());
      usageFile.accept(new PyRecursiveElementVisitor() {
        @Override
        public void visitPyElement(@NotNull PyElement node) {
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
