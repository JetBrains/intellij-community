// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.xml;

import com.intellij.lang.CompositeLanguage;
import com.intellij.lang.Language;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class XMLLanguage extends CompositeLanguage {

  public final static XMLLanguage INSTANCE = new XMLLanguage();

  private XMLLanguage() {
    super("XML", "application/xml", "text/xml");
  }

  protected XMLLanguage(@NotNull Language baseLanguage, @NonNls @NotNull String name, @NonNls @NotNull String @NotNull ... mime) {
    super(baseLanguage, name, mime);
  }
}
