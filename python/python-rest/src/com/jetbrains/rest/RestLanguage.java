// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.rest;

import com.intellij.lang.Language;

/**
 * @deprecated moved to another package
 */
@Deprecated
public final class RestLanguage {
  private RestLanguage() {
  }

  /**
   * @deprecated moved to another package
   */
  @Deprecated
  public static final Language INSTANCE =
    com.intellij.python.reStructuredText.RestLanguage.INSTANCE;
}
