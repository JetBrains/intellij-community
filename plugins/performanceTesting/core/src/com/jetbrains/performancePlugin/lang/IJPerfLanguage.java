package com.jetbrains.performancePlugin.lang;

import com.intellij.lang.Language;

public final class IJPerfLanguage extends Language {

  public static final IJPerfLanguage INSTANCE = new IJPerfLanguage();

  private IJPerfLanguage() {
    super("IntegrationPerformanceTest");
  }
}
