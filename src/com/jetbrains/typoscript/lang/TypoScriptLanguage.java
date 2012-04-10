package com.jetbrains.typoscript.lang;

import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.psi.templateLanguages.TemplateLanguage;

/**
 * @author lene
 *         Date: 03.04.12
 */
public class TypoScriptLanguage extends Language /*implements TemplateLanguage */ {//todo[lene]
  public static TypoScriptLanguage INSTANCE = new TypoScriptLanguage();

  private TypoScriptLanguage() {
    super("TypoScript", "");
  }

  @Override
  public LanguageFileType getAssociatedFileType() {
    return TypoScriptFileType.INSTANCE;
  }
}
