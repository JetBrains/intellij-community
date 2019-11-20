// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections.unresolvedReference;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface PyUnresolvedReferencesQuickFixBuilder {
  PyUnresolvedReferencesQuickFixBuilder EMPTY = new PyUnresolvedReferencesQuickFixBuilder() {
    @Override
    public void addAddInstallPackageQuickFix(@NotNull PyElement node, @NotNull PsiReference reference, String refName) {

    }

    @Override
    public void addAddIgnoredIdentifierQuickFix(List<QualifiedName> qualifiedNames) {

    }

    @Override
    public void addImportStatementQuickFix(PsiElement element) {

    }

    @Override
    public void addRenameUnresolvedRefQuickFix() {

    }

    @Override
    public void addAddParameterQuickFix(String refName, PyReferenceExpression expr) {

    }

    @Override
    public void addCreateFunctionQuickFix(PyReferenceExpression expr) {

    }

    @Override
    public void addTrueFalseQuickFix(PyReferenceExpression expr, String refText) {

    }

    @Override
    public void addInstallPackageAction(String packageName, Module module, Sdk sdk) {

    }

    @Override
    public void addCreateMemberFromUsageFixes(TypeEvalContext typeEvalContext, PyType type, PsiReference reference, String refText) {

    }

    @Override
    public void addAddSelfFix(TypeEvalContext typeEvalContext, PyElement node, PyReferenceExpression expr) {

    }

    @Override
    public void addAutoImportFix(PyElement node, PsiReference reference, PsiElement element) {

    }

    @Override
    public void addCreateClassFix(TypeEvalContext typeEvalContext, String refText, PsiElement element) {

    }

    @Override
    public void addPluginQuickFixes(PsiReference reference) {

    }

    @Override
    public void addOptimizeImportsQuickFix() {

    }

    @Override
    public LocalQuickFix[] build() {
      return LocalQuickFix.EMPTY_ARRAY;
    }
  };

  void addAddInstallPackageQuickFix(@NotNull PyElement node,
                                    @NotNull PsiReference reference,
                                    String refName);

  void addAddIgnoredIdentifierQuickFix(List<QualifiedName> qualifiedNames);

  void addImportStatementQuickFix(PsiElement element);

  void addRenameUnresolvedRefQuickFix();

  void addAddParameterQuickFix(String refName, PyReferenceExpression expr);

  void addCreateFunctionQuickFix(PyReferenceExpression expr);

  void addTrueFalseQuickFix(PyReferenceExpression expr, String refText);

  void addInstallPackageAction(String packageName, Module module, Sdk sdk);

  void addCreateMemberFromUsageFixes(
    TypeEvalContext typeEvalContext, PyType type, PsiReference reference, String refText
  );

  void addAddSelfFix(TypeEvalContext typeEvalContext, PyElement node, PyReferenceExpression expr);

  void addAutoImportFix(PyElement node, PsiReference reference, PsiElement element);

  void addCreateClassFix(TypeEvalContext typeEvalContext, @NonNls String refText, PsiElement element);

  void addPluginQuickFixes(PsiReference reference);

  void addOptimizeImportsQuickFix();

  LocalQuickFix[] build();
}
