// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.properties.psi.impl;

import com.intellij.lang.ASTFactory;
import com.intellij.lang.properties.parsing.PropertiesTokenTypes;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IFileElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PropertiesASTFactory extends ASTFactory {
  @Override
  public @Nullable CompositeElement createComposite(final @NotNull IElementType type) {
    if (type instanceof IFileElementType) {
      return new FileElement(type, null);
    }
    return new CompositeElement(type);
  }

  @Override
  public @Nullable LeafElement createLeaf(final @NotNull IElementType type, @NotNull CharSequence text) {
    if (type == PropertiesTokenTypes.KEY_CHARACTERS) {
      return new PropertyKeyImpl(type, text);
    }

    if (type == PropertiesTokenTypes.VALUE_CHARACTERS) {
      return new PropertyValueImpl(type, text);
    }

    if (type == PropertiesTokenTypes.END_OF_LINE_COMMENT) {
      return new PsiCommentImpl(type, text);
    }

    return new LeafPsiElement(type, text);
  }
}
