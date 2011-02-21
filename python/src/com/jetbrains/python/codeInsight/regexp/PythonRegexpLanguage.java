package com.jetbrains.python.codeInsight.regexp;

import com.intellij.lang.Language;
import org.intellij.lang.regexp.RegExpLanguage;

/**
 * @author yole
 */
public class PythonRegexpLanguage extends Language {
  public static final PythonRegexpLanguage INSTANCE = new PythonRegexpLanguage();

  public PythonRegexpLanguage() {
    super(RegExpLanguage.INSTANCE, "PythonRegExp");
  }
}
