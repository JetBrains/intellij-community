// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections.quickfix;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
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
import com.jetbrains.python.psi.*;
import com.jetbrains.python.refactoring.classes.PyClassRefactoringUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class PyMakeFunctionFromMethodQuickFix implements LocalQuickFix {
  public PyMakeFunctionFromMethodQuickFix() {
  }

  @Override
  public @NotNull String getFamilyName() {
    return PyPsiBundle.message("QFIX.NAME.make.function");
  }

  @Override
  public void applyFix(final @NotNull Project project, final @NotNull ProblemDescriptor descriptor) {
    final PsiElement element = descriptor.getPsiElement();
    final PyFunction problemFunction = PsiTreeUtil.getParentOfType(element, PyFunction.class);
    if (problemFunction == null) return;
    final PyClass containingClass = problemFunction.getContainingClass();
    if (containingClass == null) return;

    final List<PyReferenceExpression> usages = StreamEx.of(PyPsiIndexUtil.findUsages(problemFunction, false))
      .map(UsageInfo::getElement)
      .select(PyReferenceExpression.class)
      .toList();

    ApplicationManagerEx.getApplicationEx().runWriteActionWithCancellableProgressInDispatchThread(
      PyPsiBundle.message("refactoring.progress.title.updating.existing.usages"), problemFunction.getProject(), null, (indicator -> {
        PyFunction function = transformDefinition(problemFunction);
        for (int i = 0; i < usages.size(); i++) {
          indicator.checkCanceled();
          indicator.setFraction((i + 1.0) / usages.size());
          PyReferenceExpression usage = usages.get(i);
          PsiFile usageFile = usage.getContainingFile();
          updateUsage(function, usage, usageFile, !usageFile.equals(containingClass.getContainingFile()));
        }
      })
    );
  }

  private static @NotNull PyFunction transformDefinition(@NotNull PyFunction method) {
    PyParameter[] parameters = method.getParameterList().getParameters();
    if (parameters.length > 0) {
      parameters[0].delete();
    }
    PyClass topmostClass = PsiTreeUtil.getTopmostParentOfType(method, PyClass.class);
    assert topmostClass != null;
    PsiElement copy = method.copy();
    method.delete();
    return (PyFunction)topmostClass.getParent().addBefore(copy, topmostClass);
  }

  private static void updateUsage(final @NotNull PsiElement finalElement, final @NotNull PyReferenceExpression element,
                                  final @NotNull PsiFile usageFile, boolean addImport) {
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

  private static void removeFormerImport(final @NotNull PsiFile usageFile, boolean addImport) {
    if (usageFile instanceof PyFile && addImport) {
      PyClassRefactoringUtil.optimizeImports(usageFile);
    }
  }

  private static void updateAssignment(PyReferenceExpression element, final @NotNull PsiElement resolved) {
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

  private static void updateArgumentList(final @NotNull PyReferenceExpression element) {
    final PyCallExpression callExpression = PsiTreeUtil.getParentOfType(element, PyCallExpression.class);
    if (callExpression == null) return;
    final PyArgumentList argumentList = callExpression.getArgumentList();
    if (argumentList == null) return;
    final PyExpression[] arguments = argumentList.getArguments();
    if (arguments.length > 0) {
      arguments[0].delete();
    }
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  public @Nullable PsiElement getElementToMakeWritable(@NotNull PsiFile currentFile) {
    return currentFile;
  }
}
