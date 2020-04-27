// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.psi.impl;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.psi.YAMLPsiElement;

public class YAMLPsiElementImpl extends ASTWrapperPsiElement implements YAMLPsiElement {
  public YAMLPsiElementImpl(@NotNull final ASTNode node) {
    super(node);
  }

  @Override
  public String toString() {
    return "YAML element";
  }
}
