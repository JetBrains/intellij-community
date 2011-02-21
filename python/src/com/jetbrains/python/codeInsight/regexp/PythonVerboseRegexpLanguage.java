package com.jetbrains.python.codeInsight.regexp;

import com.intellij.lang.Language;
import org.intellij.lang.regexp.RegExpLanguage;

/**
 * @author yole
 */
public class PythonVerboseRegexpLanguage extends Language {
  public static final PythonVerboseRegexpLanguage INSTANCE = new PythonVerboseRegexpLanguage();

  public PythonVerboseRegexpLanguage() {
    super(RegExpLanguage.INSTANCE, "PythonVerboseRegExp");
  }
}
