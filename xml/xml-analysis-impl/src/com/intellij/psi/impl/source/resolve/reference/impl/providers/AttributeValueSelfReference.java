// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

public class AttributeValueSelfReference extends BasicAttributeValueReference {
  public AttributeValueSelfReference(final PsiElement element) {
    super(element);
  }

  public AttributeValueSelfReference(final PsiElement element, int offset) {
    super(element, offset);
  }

  public AttributeValueSelfReference(final PsiElement element, TextRange range) {
    super(element, range);
  }

  @Override
  public @Nullable PsiElement resolve() {
    return myElement;
  }

  @Override
  public boolean isSoft() {
    return true;
  }
}
