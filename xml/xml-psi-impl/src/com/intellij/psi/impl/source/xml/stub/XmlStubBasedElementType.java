// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.xml.stub;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.tree.ICompositeElementType;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

public abstract class XmlStubBasedElementType<StubT extends StubElement<?>, PsiT extends PsiElement>
  extends IStubElementType<StubT, PsiT> implements ICompositeElementType {

  private String externalId = null;

  public XmlStubBasedElementType(@NotNull String debugName,
                                 @NotNull Language language) {
    super(debugName, language);
  }

  @Override
  public @NotNull String getExternalId() {
    if (externalId == null) {
      externalId = (getLanguage() == XMLLanguage.INSTANCE ? "" : getLanguage().getID().toUpperCase(Locale.ENGLISH) + ":") + getDebugName();
    }
    return externalId;
  }

  @Override
  public String toString() {
    return getExternalId();
  }

  @Override
  public void indexStub(@NotNull StubT stub, @NotNull IndexSink sink) {
  }

  public abstract @NotNull PsiT createPsi(@NotNull ASTNode node);

  @Override
  public @NotNull ASTNode createCompositeNode() {
    return new CompositeElement(this);
  }
}
