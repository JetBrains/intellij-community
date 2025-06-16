// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performanceScripts.lang;

import com.intellij.lang.Language;

public final class IJPerfLanguage extends Language {

  public static final IJPerfLanguage INSTANCE = new IJPerfLanguage();

  private IJPerfLanguage() {
    super("IntegrationPerformanceTest");
  }
}
