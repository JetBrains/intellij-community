// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.actions;

import com.intellij.ide.actions.QualifiedNameProvider;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.stubs.PyClassNameIndex;
import com.jetbrains.python.psi.stubs.PyFunctionNameIndex;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;


public final class PyQualifiedNameProvider implements QualifiedNameProvider {

  @Override
  public PsiElement adjustElementToCopy(@NotNull PsiElement element) {
    return element instanceof PyClass || element instanceof PyFunction ? element : null;
  }

  @Override
  public @Nullable String getQualifiedName(@NotNull PsiElement element) {
    if (element instanceof PyClass) {
      return ((PyClass)element).getQualifiedName();
    }
    if (element instanceof PyFunction) {
      return ((PyFunction)element).getQualifiedName();
    }
    return null;
  }

  @Override
  public @Nullable PsiElement qualifiedNameToElement(@NotNull String fqn, @NotNull Project project) {
    GlobalSearchScope entireProjectScope = GlobalSearchScope.allScope(project);
    List<PyClass> classes = PyClassNameIndex.findByQualifiedName(fqn, project, entireProjectScope);
    if (!classes.isEmpty()) {
      return classes.get(0);
    }
    List<PyFunction> functions = PyFunctionNameIndex.findByQualifiedName(fqn, project, entireProjectScope);
    if (!functions.isEmpty()) {
      return functions.get(0);
    }
    return null;
  }
}
