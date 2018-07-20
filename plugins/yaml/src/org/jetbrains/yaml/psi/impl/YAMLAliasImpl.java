// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.psi.YAMLAlias;
import org.jetbrains.yaml.psi.YamlPsiElementVisitor;
import org.jetbrains.yaml.resolve.YAMLAliasReference;

/** Current implementation consists of 2 nodes: star symbol and name identifier */
public class YAMLAliasImpl extends YAMLValueImpl implements YAMLAlias {
  public YAMLAliasImpl(@NotNull ASTNode node) {
    super(node);
  }

  @NotNull
  @Override
  public String getAliasName() {
    LeafPsiElement identifier = getIdentifierPsi();
    return identifier == null ? "" : identifier.getText();
  }

  @Override
  public YAMLAliasReference getReference() {
    return getIdentifierPsi() == null ? null : new YAMLAliasReference(this);
  }

  @Override
  public String toString() {
    return "YAML alias";
  }

  /** For now it could not return null but better do not rely on it */
  @Contract(pure = true)
  @Nullable
  public LeafPsiElement getIdentifierPsi() {
    return (LeafPsiElement)getLastChild();
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof YamlPsiElementVisitor) {
      ((YamlPsiElementVisitor)visitor).visitAlias(this);
    }
    else {
      super.accept(visitor);
    }
  }
}
