package com.intellij.lang.properties.psi.impl;

import com.intellij.lang.ASTFactory;
import com.intellij.lang.properties.parsing.PropertiesTokenTypes;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.util.CharTable;
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
  public LeafElement createLeaf(final IElementType type, final CharSequence fileText, final int start, final int end, final CharTable table) {
    if (type == PropertiesTokenTypes.VALUE_CHARACTERS) {
      return new PropertyValueImpl(type, fileText, start, end, table);
    }
    return new LeafPsiElement(type, fileText, start, end, table);
  }
}
