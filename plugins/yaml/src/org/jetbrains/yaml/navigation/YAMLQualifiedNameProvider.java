// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.navigation;

import com.intellij.ide.actions.QualifiedNameProvider;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.YAMLUtil;
import org.jetbrains.yaml.psi.YAMLPsiElement;

public class YAMLQualifiedNameProvider implements QualifiedNameProvider {
  @Nullable
  @Override
  public PsiElement adjustElementToCopy(PsiElement element) {
    return null;
  }

  @Nullable
  @Override
  public String getQualifiedName(PsiElement element) {
    return element instanceof YAMLPsiElement ? YAMLUtil.getConfigFullName(element) : null;
  }

  @Nullable
  @Override
  public PsiElement qualifiedNameToElement(String fqn, Project project) {
    return null;
  }
}
