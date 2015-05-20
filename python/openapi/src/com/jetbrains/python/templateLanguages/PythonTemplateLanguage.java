package com.jetbrains.python.templateLanguages;

import com.intellij.lang.Language;
import com.intellij.openapi.module.Module;
import com.intellij.psi.templateLanguages.TemplateLanguage;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Python template language
 * @author Ilya.Kazakevich
 */
public abstract class PythonTemplateLanguage extends Language implements TemplateLanguage {


  protected PythonTemplateLanguage(@NotNull @NonNls final String ID,
                                @NotNull @NonNls final String... mimeTypes) {
    super(null, ID, mimeTypes);
  }

  /**
   * @return template language readable name
   */
  @NotNull
  public abstract String getTemplateLanguageName();

  /**
   * Checks if text contains some chars that make us think this text uses appropriate template language and we should set
   * this language as our project language
   *
   * @param text text with chars to check
   * @return text contains some chars that make us think this text uses appropriate template language and we should set this language as our project language
   */
  public abstract boolean isFileLeadsToLanguageSelection(@NotNull String text);
}
