package com.intellij.spellchecker.tokenizer;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Created by IntelliJ IDEA.
 *
 * @author shkate@jetbrains.com
 */
public class Tokenizer<T extends PsiElement> {

  @Nullable
  public Token[] tokenize(@NotNull T element) {
    return null;
  }
}
