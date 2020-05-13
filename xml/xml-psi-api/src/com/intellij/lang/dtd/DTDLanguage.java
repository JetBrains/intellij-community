// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.dtd;

import com.intellij.lang.xml.XMLLanguage;

public class DTDLanguage extends XMLLanguage {

  public static final DTDLanguage INSTANCE = new DTDLanguage();

  protected DTDLanguage() {
    super(XMLLanguage.INSTANCE, "DTD", "application/xml-dtd", "text/dtd", "text/x-dtd");
  }
}
