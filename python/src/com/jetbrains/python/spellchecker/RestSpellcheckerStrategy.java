package com.jetbrains.python.spellchecker;

import com.intellij.lang.Language;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.spellchecker.inspections.SplitterFactory;
import com.intellij.spellchecker.tokenizer.SpellcheckingStrategy;
import com.intellij.spellchecker.tokenizer.Token;
import com.intellij.spellchecker.tokenizer.Tokenizer;
import com.jetbrains.rest.RestLanguage;
import com.jetbrains.rest.RestTokenTypes;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class RestSpellcheckerStrategy extends SpellcheckingStrategy {
  private static final Tokenizer<PsiElement> REST_ELEMENT_TOKENIZER = new Tokenizer<PsiElement>() {
    @Override
    public Token[] tokenize(@NotNull PsiElement element) {
      return new Token[]{new Token<PsiElement>(element, element.getText(), false, SplitterFactory.getInstance().getPlainTextSplitter())};
    }
  };

  @NotNull
  @Override
  public Tokenizer getTokenizer(PsiElement element) {
    IElementType elementType = element.getNode().getElementType();
    if (elementType == RestTokenTypes.FIELD ||
        elementType == RestTokenTypes.CUSTOM_DIRECTIVE ||
        elementType == RestTokenTypes.REST_DJANGO_INJECTION ||
        elementType == RestTokenTypes.REST_INJECTION) {
      return EMPTY_TOKENIZER;
    }
    if (element instanceof LeafPsiElement && element.getLanguage() == RestLanguage.INSTANCE) {
      return REST_ELEMENT_TOKENIZER;
    }
    return EMPTY_TOKENIZER;
  }

  @NotNull
  @Override
  public Language getLanguage() {
    return RestLanguage.INSTANCE;
  }
}
