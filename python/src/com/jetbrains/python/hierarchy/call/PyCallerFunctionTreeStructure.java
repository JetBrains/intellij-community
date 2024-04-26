// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.hierarchy.call;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.PyElement;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Map;

/**
 * @author novokrest
 */
public class PyCallerFunctionTreeStructure extends PyCallHierarchyTreeStructureBase {
  public PyCallerFunctionTreeStructure(Project project, PsiElement element, String currentScopeType) {
    super(project, element, currentScopeType);
  }

  @Override
  protected @NotNull Map<PsiElement, Collection<PsiElement>> getChildren(@NotNull PyElement element) {
    // TODO: Add callers from the dynamic call data manager
    return PyStaticCallHierarchyUtil.getCallers(element);
  }
}
