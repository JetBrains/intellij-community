package com.jetbrains.python;

import com.intellij.lang.Language;

/**
 * @author yole
 */
public class PythonLanguage extends Language {
  public static PythonLanguage getInstance() {
    return (PythonLanguage)PythonFileType.INSTANCE.getLanguage();
  }

  @Override
  public boolean isCaseSensitive() {
    return true; // http://jetbrains-feed.appspot.com/message/372001
  }

  protected PythonLanguage() {
    super("Python");
  }
}
