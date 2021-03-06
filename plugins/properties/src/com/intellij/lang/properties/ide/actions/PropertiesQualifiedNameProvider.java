// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.properties.ide.actions;

import com.intellij.ide.actions.QualifiedNameProvider;
import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

final class PropertiesQualifiedNameProvider implements QualifiedNameProvider {
  @Nullable
  @Override
  public PsiElement adjustElementToCopy(PsiElement element) {
    return null;
  }

  @Nullable
  @Override
  public String getQualifiedName(PsiElement element) {
    return element instanceof Property ? ((Property)element).getKey() : null;
  }

  @Override
  public PsiElement qualifiedNameToElement(String fqn, Project project) {
    return null;
  }
}
