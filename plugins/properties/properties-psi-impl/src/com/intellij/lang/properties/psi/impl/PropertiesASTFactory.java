/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.lang.properties.psi.impl;

import com.intellij.lang.ASTFactory;
import com.intellij.lang.properties.parsing.PropertiesTokenTypes;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IFileElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author cdr
 */
public class PropertiesASTFactory extends ASTFactory {
  @Override
  @Nullable
  public CompositeElement createComposite(@NotNull final IElementType type) {
    if (type instanceof IFileElementType) {
      return new FileElement(type, null);
    }
    return new CompositeElement(type);
  }

  @Override
  @Nullable
  public LeafElement createLeaf(@NotNull final IElementType type, @NotNull CharSequence text) {
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
