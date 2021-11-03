package org.intellij.plugins.markdown.lang.psi;

import com.intellij.lang.ASTFactory;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.tree.IElementType;
import org.intellij.plugins.markdown.lang.MarkdownElementTypes;
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes;
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownCodeFenceContentImpl;
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownCodeFenceImpl;
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownTableSeparatorRow;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MarkdownASTFactory extends ASTFactory {
  @Nullable
  @Override
  public CompositeElement createComposite(@NotNull IElementType type) {
    if (type == MarkdownElementTypes.CODE_FENCE) {
      return new MarkdownCodeFenceImpl(type);
    }

    return super.createComposite(type);
  }

  @Nullable
  @Override
  public LeafElement createLeaf(@NotNull IElementType type, @NotNull CharSequence text) {
    if (type == MarkdownTokenTypes.CODE_FENCE_CONTENT) {
      return new MarkdownCodeFenceContentImpl(type, text);
    }
    if (type == MarkdownTokenTypes.TABLE_SEPARATOR && text.length() > 1) {
      return new MarkdownTableSeparatorRow(text);
    }
    return super.createLeaf(type, text);
  }
}
