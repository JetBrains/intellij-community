package com.intellij.spellchecker.tokenizer;

import com.intellij.lang.Language;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.psi.*;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlText;
import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 *
 * @author shkate@jetbrains.com
 */
public class SpellcheckingStrategy {


  public static ExtensionPointName<SpellcheckingStrategy> EP_NAME = ExtensionPointName.create("com.intellij.spellchecker.support");

  @NotNull
  public Tokenizer getTokenizer(PsiElement element) {
    if (element instanceof PsiNameIdentifierOwner) return new PsiIdentifierOwnerTokenizer();
    if (element instanceof PsiComment) return new CommentTokenizer();
    if (element instanceof XmlAttributeValue) return new XmlAttributeTokenizer();
    if (element instanceof XmlText) return new XmlTextTokenizer();
    if (element instanceof PsiPlainText) return new TextTokenizer();

    return new Tokenizer();
  }

  @NotNull
  public Language getLanguage(){
    return PlainTextLanguage.INSTANCE;
  }
}
