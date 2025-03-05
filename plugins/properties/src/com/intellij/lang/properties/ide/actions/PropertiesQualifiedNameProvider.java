// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.properties.ide.actions;

import com.intellij.ide.actions.QualifiedNameProvider;
import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class PropertiesQualifiedNameProvider implements QualifiedNameProvider {
  @Override
  public @Nullable PsiElement adjustElementToCopy(@NotNull PsiElement element) {
    return null;
  }

  @Override
  public @Nullable String getQualifiedName(@NotNull PsiElement element) {
    return element instanceof Property ? ((Property)element).getKey() : null;
  }

  @Override
  public PsiElement qualifiedNameToElement(@NotNull String fqn, @NotNull Project project) {
    return null;
  }
}
