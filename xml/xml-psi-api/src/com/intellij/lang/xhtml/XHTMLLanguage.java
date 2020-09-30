// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.xhtml;

import com.intellij.lang.xml.XMLLanguage;

public final class XHTMLLanguage extends XMLLanguage {

  public static final XHTMLLanguage INSTANCE = new XHTMLLanguage();

  private XHTMLLanguage() {
    super(XMLLanguage.INSTANCE, "XHTML", "text/xhtml", "application/xhtml+xml");
  }
}
