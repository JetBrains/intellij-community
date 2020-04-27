// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.buildout.config.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class BuildoutCfgSection extends BuildoutCfgPsiElement {
  public BuildoutCfgSection(@NotNull final ASTNode node) {
    super(node);
  }

  @Nullable
  public String getHeaderName() {
    BuildoutCfgSectionHeader header = PsiTreeUtil.findChildOfType(this, BuildoutCfgSectionHeader.class);
    return header != null ? header.getName() : null;
  }


  public List<BuildoutCfgOption> getOptions() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, BuildoutCfgOption.class);
  }

  @Nullable
  public BuildoutCfgOption findOptionByName(String name) {
    for (BuildoutCfgOption option : getOptions()) {
      if (name.equals(option.getKey())) {
        return option;
      }
    }
    return null;
  }

  @Nullable
  public String getOptionValue(String name) {
    final BuildoutCfgOption option = findOptionByName(name);
    if (option != null) {
      return StringUtil.join(option.getValues(), " ");
    }
    return null;
  }

  @Override
  public String toString() {
    return "BuildoutCfgSection:" + getNode().getElementType().toString();
  }
}
