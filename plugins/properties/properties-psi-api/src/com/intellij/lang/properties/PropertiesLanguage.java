// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
