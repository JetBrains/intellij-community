package com.intellij.spellchecker.xml;

import com.intellij.codeInspection.SuppressIntentionAction;
import com.intellij.psi.PsiElement;
import com.intellij.spellchecker.tokenizer.SuppressibleSpellcheckingStrategy;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author Sergey Evdokimov
 */
public class XmlSpellcheckingStrategy extends SuppressibleSpellcheckingStrategy {

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
  public SuppressIntentionAction[] getSuppressActions(@NotNull PsiElement element, @NotNull String name) {
    return SuppressIntentionAction.EMPTY_ARRAY;
  }
}
