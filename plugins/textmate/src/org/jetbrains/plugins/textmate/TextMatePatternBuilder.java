package org.jetbrains.plugins.textmate;

import com.intellij.lexer.EmptyLexer;
import com.intellij.lexer.Lexer;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.search.IndexPatternBuilder;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.textmate.psi.TextMateFile;

public final class TextMatePatternBuilder implements IndexPatternBuilder {
  @Override
  public @Nullable Lexer getIndexingLexer(@NotNull PsiFile file) {
    return file instanceof TextMateFile ? new EmptyLexer() : null;
  }

  @Override
  public @Nullable TokenSet getCommentTokenSet(@NotNull PsiFile file) {
    return null;
  }

  @Override
  public int getCommentStartDelta(IElementType tokenType) {
    return 0;
  }

  @Override
  public int getCommentEndDelta(IElementType tokenType) {
    return 0;
  }
}
