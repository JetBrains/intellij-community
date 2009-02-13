package com.intellij.lang.properties.psi.impl;

import com.intellij.lang.ASTFactory;
import com.intellij.lang.properties.parsing.PropertiesTokenTypes;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IFileElementType;
import org.jetbrains.annotations.Nullable;

/**
 * @author cdr
 */
public class PropertiesASTFactory extends ASTFactory {
  @Nullable
  public CompositeElement createComposite(final IElementType type) {
    if (type instanceof IFileElementType) {
      return new FileElement(type);
    }
    return new CompositeElement(type);
  }

  @Nullable
  public LeafElement createLeaf(final IElementType type, CharSequence text) {
    if (type == PropertiesTokenTypes.VALUE_CHARACTERS) {
      return new PropertyValueImpl(type, text);
    }

    if (type == PropertiesTokenTypes.END_OF_LINE_COMMENT) {
      return new PsiCommentImpl(type, text);
    }

    return new LeafPsiElement(type, text);
  }
}
