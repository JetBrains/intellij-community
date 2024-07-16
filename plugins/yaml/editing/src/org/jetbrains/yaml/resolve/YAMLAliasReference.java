// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.resolve;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.psi.YAMLAnchor;
import org.jetbrains.yaml.psi.impl.YAMLAliasImpl;

import java.util.Collection;
import java.util.Objects;

public class YAMLAliasReference extends PsiReferenceBase<YAMLAliasImpl> {
  public YAMLAliasReference(YAMLAliasImpl alias) {
    super(alias);
  }

  @Override
  public @Nullable YAMLAnchor resolve() {
    return YAMLLocalResolveUtil.getResolveAliasMap(myElement.getContainingFile()).get(myElement);
  }

  @Override
  public PsiElement handleElementRename(@NotNull String newElementName) throws IncorrectOperationException {
    getIdentifier().replaceWithText(newElementName);
    return myElement;
  }

  @Override
  public @NotNull TextRange getRangeInElement() {
    return TextRange.from(getIdentifier().getStartOffsetInParent(), getIdentifier().getTextLength());
  }

  @Contract(pure = true)
  private @NotNull LeafPsiElement getIdentifier() {
    return Objects.requireNonNull(myElement.getIdentifierPsi(), "Reference should not be created for aliases without name");
  }

  @Override
  public Object @NotNull [] getVariants() {
    Collection<YAMLAnchor> defs = YAMLLocalResolveUtil.getFirstAnchorDefs(myElement.getContainingFile().getOriginalFile());
    return defs.toArray();
  }
}
