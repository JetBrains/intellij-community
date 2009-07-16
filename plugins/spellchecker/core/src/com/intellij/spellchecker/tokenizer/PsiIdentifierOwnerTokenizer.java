package com.intellij.spellchecker.tokenizer;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNameIdentifierOwner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Created by IntelliJ IDEA.
 *
 * @author shkate@jetbrains.com
 */
public class PsiIdentifierOwnerTokenizer extends Tokenizer<PsiNameIdentifierOwner> {

  @Nullable
  @Override
  public Token[] tokenize(@NotNull PsiNameIdentifierOwner element) {
    PsiElement identifier = element.getNameIdentifier();
    if (identifier == null) {
      return null;
    }
    int offset = identifier.getStartOffsetInParent();

    return new Token[]{new Token<PsiElement>(element, identifier.getText(), true, offset)};
  }


}