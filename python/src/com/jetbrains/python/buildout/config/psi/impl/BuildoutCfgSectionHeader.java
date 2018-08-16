// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.buildout.config.psi.impl;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author traff
 */
public class BuildoutCfgSectionHeader extends BuildoutCfgPsiElement {
  public BuildoutCfgSectionHeader(@NotNull final ASTNode node) {
    super(node);
  }

  @Override
  @Nullable
  public String getName() {
    String name = getText().trim();
    if (name.startsWith("[") && name.endsWith("]")) {
      return name.substring(1, name.length()-1).trim();
    }
    return name;
  }

  @Override
  public String toString() {
    return "BuildoutCfgSectionHeader:" + getNode().getElementType().toString();
  }
}
