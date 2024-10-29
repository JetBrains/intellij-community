package org.jetbrains.plugins.textmate.spellchecker;

import com.intellij.openapi.project.DumbAware;
import com.intellij.psi.PsiElement;
import com.intellij.spellchecker.tokenizer.SpellcheckingStrategy;
import com.intellij.spellchecker.tokenizer.Tokenizer;
import org.jetbrains.annotations.NotNull;

public class TextMateSpellingStrategy extends SpellcheckingStrategy implements DumbAware {
  @NotNull
  @Override
  public Tokenizer getTokenizer(PsiElement element) {
    return TEXT_TOKENIZER;
  }
}
