// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.actions;

import com.intellij.ide.actions.QualifiedNameProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.psi.resolve.PyQualifiedNameResolveContext;
import com.jetbrains.python.psi.resolve.PyResolveImportUtil;
import com.jetbrains.python.psi.stubs.PyClassNameIndex;
import com.jetbrains.python.psi.stubs.PyFunctionNameIndex;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;


@ApiStatus.Internal
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
    return qualifiedNameToModule(fqn, project);
  }

  /**
   * Resolves a module/package qualified name (e.g. {@code collections}) to its file, turning a package
   * directory into its {@code __init__} file so navigation lands on the module rather than the directory.
   */
  private static @Nullable PsiElement qualifiedNameToModule(@NotNull String fqn, @NotNull Project project) {
    QualifiedName qualifiedName = QualifiedName.fromDottedString(fqn);
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      PyQualifiedNameResolveContext context = PyResolveImportUtil.fromModule(module);
      for (PsiElement resolved : PyResolveImportUtil.resolveQualifiedName(qualifiedName, context)) {
        if (PyUtil.turnDirIntoInit(resolved) instanceof PyFile file) {
          return file;
        }
      }
    }
    return null;
  }
}
