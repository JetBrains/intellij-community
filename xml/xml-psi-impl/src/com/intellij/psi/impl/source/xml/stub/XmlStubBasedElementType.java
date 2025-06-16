// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.xml.stub;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.tree.ICompositeElementType;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

public abstract class XmlStubBasedElementType<PsiT extends PsiElement>
  extends IElementType implements ICompositeElementType {

  private String externalId = null;

  public XmlStubBasedElementType(@NotNull String debugName,
                                 @NotNull Language language) {
    super(debugName, language);
  }

  @Override
  public @NotNull String toString() {
    if (externalId == null) {
      externalId = (getLanguage() == XMLLanguage.INSTANCE ? "" : getLanguage().getID().toUpperCase(Locale.ENGLISH) + ":") + getDebugName();
    }
    return externalId;
  }

  public abstract @NotNull PsiT createPsi(@NotNull ASTNode node);

  @Override
  public @NotNull ASTNode createCompositeNode() {
    return new CompositeElement(this);
  }
}
