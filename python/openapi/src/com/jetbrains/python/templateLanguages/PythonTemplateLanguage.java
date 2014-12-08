package com.jetbrains.python.templateLanguages;

import com.intellij.lang.Language;
import com.intellij.psi.templateLanguages.TemplateLanguage;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * TODO: Make this class implemeent {@link com.intellij.lang.html.HtmlCompatibleLanguage} to prevent copy/paste by fixing dependencies
 * Python template language
 *
 * @author Ilya.Kazakevich
 */
public abstract class PythonTemplateLanguage extends Language implements TemplateLanguage {


  protected PythonTemplateLanguage(@Nullable final Language baseLanguage,
                                   @NotNull @NonNls final String ID,
                                   @NotNull @NonNls final String... mimeTypes) {
    super(baseLanguage, ID, mimeTypes);
  }

  protected PythonTemplateLanguage(@NotNull @NonNls final String id) {
    super(id);
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
