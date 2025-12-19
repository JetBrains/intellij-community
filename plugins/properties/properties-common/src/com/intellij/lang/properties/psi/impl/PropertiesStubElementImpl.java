// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * @author max
 */
package com.intellij.lang.properties.psi.impl;

import com.intellij.extapi.psi.StubBasedPsiElementBase;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.properties.PropertiesLanguage;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

public class PropertiesStubElementImpl <T extends StubElement> extends StubBasedPsiElementBase<T> {
  public PropertiesStubElementImpl(final T stub, IElementType nodeType) {
    super(stub, nodeType);
  }

  public PropertiesStubElementImpl(final ASTNode node) {
    super(node);
  }

  @Override
  public final @NotNull IStubElementType getElementType() {
    throw new UnsupportedOperationException("Use getIElementType() instead");
  }

  @Override
  public @NotNull Language getLanguage() {
    return PropertiesLanguage.INSTANCE;
  }
}