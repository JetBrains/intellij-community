// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.xml;

import com.intellij.lang.CompositeLanguage;
import com.intellij.lang.Language;
import org.jetbrains.annotations.NonNls;

public class XMLLanguage extends CompositeLanguage {

  public final static XMLLanguage INSTANCE = new XMLLanguage();

  private XMLLanguage() {
    super("XML", "application/xml", "text/xml");
  }

  /**
   * @deprecated use {@link #XMLLanguage(Language, String, String...)}
   */
  @Deprecated
  protected XMLLanguage(@NonNls String name, @NonNls String... mime) {
    super(name, mime);
  }

  protected XMLLanguage(Language baseLanguage, @NonNls String name, @NonNls String... mime) {
    super(baseLanguage, name, mime);
  }
}
