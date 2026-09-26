// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.navigation;

import com.intellij.ide.actions.QualifiedNameProvider;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.YAMLUtil;
import org.jetbrains.yaml.psi.YAMLPsiElement;

public class YAMLQualifiedNameProvider implements QualifiedNameProvider {
  @Override
  public @Nullable PsiElement adjustElementToCopy(@NotNull PsiElement element) {
    return element instanceof LeafPsiElement ? PsiTreeUtil.getParentOfType(element, YAMLPsiElement.class) : null;
  }

  @Override
  public @Nullable String getQualifiedName(@NotNull PsiElement element) {
    if (element instanceof YAMLPsiElement) {
      String configFullName = YAMLUtil.getConfigFullName((YAMLPsiElement)element);
      if (!configFullName.isEmpty()) {
        return configFullName;
      }
    }
    return null;
  }

  @Override
  public @Nullable PsiElement qualifiedNameToElement(@NotNull String fqn, @NotNull Project project) {
    return null;
  }
}
