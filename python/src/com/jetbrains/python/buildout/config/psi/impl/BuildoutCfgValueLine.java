// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.buildout.config.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiReference;
import com.jetbrains.python.buildout.config.psi.BuildoutPsiUtil;
import com.jetbrains.python.buildout.config.ref.BuildoutPartReference;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class BuildoutCfgValueLine extends BuildoutCfgPsiElement {
  @NonNls private static final String PARTS_OPTION = "parts";

  public BuildoutCfgValueLine(@NotNull final ASTNode node) {
    super(node);
  }

  @Override
  public String toString() {
    return "BuildoutCfgValue:" + getNode().getElementType().toString();
  }

  @Override
  public PsiReference @NotNull [] getReferences() {
    if (BuildoutPsiUtil.isInBuildoutSection(this) && BuildoutPsiUtil.isAssignedTo(this, PARTS_OPTION)) {
      List<BuildoutPartReference> refs = new ArrayList<>();
      List<Pair<String, Integer>> names = StringUtil.getWordsWithOffset(getText());
      for (Pair<String, Integer> name : names) {
        refs.add(new BuildoutPartReference(this, name.getFirst(), name.getSecond()));
      }
      return  refs.toArray(new BuildoutPartReference[0]);
    }

    return PsiReference.EMPTY_ARRAY;
  }
}
