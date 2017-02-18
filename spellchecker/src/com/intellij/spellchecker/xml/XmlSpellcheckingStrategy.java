package com.intellij.spellchecker.xml;

import com.intellij.codeInspection.SuppressQuickFix;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.templateLanguages.TemplateLanguage;
import com.intellij.psi.xml.XmlToken;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.spellchecker.tokenizer.SuppressibleSpellcheckingStrategy;
import com.intellij.spellchecker.tokenizer.Tokenizer;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author Sergey Evdokimov
 */
public class XmlSpellcheckingStrategy extends SuppressibleSpellcheckingStrategy {

  @NotNull
  @Override
  public Tokenizer getTokenizer(PsiElement element) {
    if (element instanceof XmlToken && ((XmlToken)element).getTokenType() == XmlTokenType.XML_DATA_CHARACTERS) {
      PsiFile file = element.getContainingFile();
      if (file == null || file.getLanguage() instanceof TemplateLanguage)
        return EMPTY_TOKENIZER;
    }
    return super.getTokenizer(element);
  }

  @Override
  public boolean isSuppressedFor(@NotNull PsiElement element, @NotNull String name) {
    DomElement domElement = DomUtil.getDomElement(element);
    if (domElement != null) {
      if (domElement.getAnnotation(NoSpellchecking.class) != null) {
        return true;
      }
    }

    return false;
  }

  @Override
  public SuppressQuickFix[] getSuppressActions(@NotNull PsiElement element, @NotNull String name) {
    return SuppressQuickFix.EMPTY_ARRAY;
  }
}
