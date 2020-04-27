// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.buildout.config.psi.impl;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;

public class BuildoutCfgKey extends BuildoutCfgPsiElement {
  public BuildoutCfgKey(@NotNull final ASTNode node) {
    super(node);
  }

  @Override
  public String toString() {
    return "BuildoutCfgKey:" + getNode().getElementType().toString();
  }
}
