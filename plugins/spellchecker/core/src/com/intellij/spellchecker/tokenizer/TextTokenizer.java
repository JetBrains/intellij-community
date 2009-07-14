package com.intellij.spellchecker.tokenizer;

import com.intellij.psi.PsiPlainText;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Created by IntelliJ IDEA.
 *
 * @author shkate@jetbrains.com
 */
public class TextTokenizer extends Tokenizer<PsiPlainText> {


  @Nullable
  @Override
  public Token[] tokenize(@NotNull PsiPlainText element) {
    return new Token[]{new Token<PsiPlainText>(element, element.getText(),false)};
  }


}