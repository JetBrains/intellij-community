// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.properties;

import com.intellij.lang.Language;

public class PropertiesLanguage extends Language {

  public static final PropertiesLanguage INSTANCE = new PropertiesLanguage();

  private static final String ID = "Properties";

  private PropertiesLanguage() {
    super(ID, "text/properties");
  }

  protected PropertiesLanguage(String id) {
    super(id, "text/properties");
  }
}
