// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.xml;

import com.intellij.lang.CompositeLanguage;
import com.intellij.lang.Language;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class XMLLanguage extends CompositeLanguage {

  public static final XMLLanguage INSTANCE = new XMLLanguage();

  private XMLLanguage() {
    super("XML", "application/xml", "text/xml");
  }

  protected XMLLanguage(@NotNull Language baseLanguage, @NonNls @NotNull String name, @NonNls @NotNull String @NotNull ... mime) {
    super(baseLanguage, name, mime);
  }
}
