package com.intellij.spellchecker.tokenizer;

import com.intellij.psi.PsiComment;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Created by IntelliJ IDEA.
 *
 * @author shkate@jetbrains.com
 */
public class CommentTokenizer extends Tokenizer<PsiComment> {

  @Nullable
  @Override
  public Token[] tokenize(@NotNull PsiComment element) {
    return new Token[]{new Token<PsiComment>(element, element.getText(),false)};
  }


}
