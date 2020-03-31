// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.psi.*;

import java.util.Collection;
import java.util.Collections;

public class YAMLSequenceItemImpl extends YAMLPsiElementImpl implements YAMLSequenceItem {
  public YAMLSequenceItemImpl(@NotNull final ASTNode node) {
    super(node);
  }

  @Nullable
  @Override
  public YAMLValue getValue() {
    return PsiTreeUtil.findChildOfType(this, YAMLValue.class);
  }

  @Override
  @NotNull
  public Collection<YAMLKeyValue> getKeysValues() {
    final YAMLMapping mapping = PsiTreeUtil.findChildOfType(this, YAMLMapping.class);
    if (mapping == null) {
      return Collections.emptyList();
    }
    else {
      return mapping.getKeyValues();
    }
  }

  @Override
  public String toString() {
    return "YAML sequence item";
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof YamlPsiElementVisitor) {
      ((YamlPsiElementVisitor)visitor).visitSequenceItem(this);
    }
    else {
      super.accept(visitor);
    }
  }

  @Override
  public int getItemIndex() {
    PsiElement parent = getParent();
    if (parent instanceof YAMLSequence) {
      return ((YAMLSequence)parent).getItems().indexOf(this);
    }
    return 0;
  }
}