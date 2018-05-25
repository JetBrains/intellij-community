// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  @Nullable
  @Override
  public YAMLAnchor resolve() {
    return YAMLLocalResolveUtil.getResolveAliasMap(myElement.getContainingFile()).get(myElement);
  }

  @Override
  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    getIdentifier().replaceWithText(newElementName);
    return myElement;
  }

  @NotNull
  @Override
  public TextRange getRangeInElement() {
    return TextRange.from(getIdentifier().getStartOffsetInParent(), getIdentifier().getTextLength());
  }

  @Contract(pure = true)
  @NotNull
  private LeafPsiElement getIdentifier() {
    return Objects.requireNonNull(myElement.getIdentifierPsi(), "Reference should not be created for aliases without name");
  }

  @NotNull
  @Override
  public Object[] getVariants() {
    Collection<YAMLAnchor> defs = YAMLLocalResolveUtil.getFirstAnchorDefs(myElement.getContainingFile().getOriginalFile());
    return defs.toArray();
  }
}
