package com.intellij.spellchecker.tokenizer;

import com.intellij.lang.Language;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlText;
import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 *
 * @author shkate@jetbrains.com
 */
public class HtmlSpellcheckingStrategy extends SpellcheckingStrategy {
  @NotNull
  @Override
    public Tokenizer getTokenizer(PsiElement element) {
      if (element instanceof PsiComment) return new CommentTokenizer();
      if (element instanceof XmlAttributeValue) return new XmlAttributeTokenizer();
      if (element instanceof XmlText) return new XmlTextTokenizer();
      return new Tokenizer();
    }

  @NotNull
  @Override
  public Language getLanguage() {
    return Language.findLanguageByID("HTML");
  }
}