package com.intellij.tokenindex;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public interface Tokenizer {
  @NotNull
  List<Token> tokenize(Collection<? extends PsiElement> roots);
}
