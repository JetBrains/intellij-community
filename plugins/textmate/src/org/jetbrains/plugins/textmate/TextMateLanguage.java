package org.jetbrains.plugins.textmate;

import com.intellij.lang.Language;

/**
 * User: zolotov
 * <p/>
 * Any language that supported via TextMate bundle.
 * Instance should be created once and must be used for all languages.
 */
public final class TextMateLanguage extends Language {
  public static final TextMateLanguage LANGUAGE = new TextMateLanguage();

  private TextMateLanguage() {
    super("textmate");
  }
}

