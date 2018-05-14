// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.psi.YAMLAnchor;
import org.jetbrains.yaml.psi.YAMLValue;
import org.jetbrains.yaml.psi.YamlPsiElementVisitor;

/** Current implementation  consists of 2 nodes: ampersand symbol and name identifier */
public class YAMLAnchorImpl extends YAMLPsiElementImpl implements YAMLAnchor {
  public YAMLAnchorImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Nullable
  @Override
  public YAMLValue getMarkedValue() {
    PsiElement parent = getParent();
    if (parent instanceof YAMLValue) {
      return (YAMLValue)parent;
    }
    return null;
  }

  @Override
  public String toString() {
    return "YAML anchor";
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof YamlPsiElementVisitor) {
      ((YamlPsiElementVisitor)visitor).visitAnchor(this);
    }
    else {
      super.accept(visitor);
    }
  }
}
