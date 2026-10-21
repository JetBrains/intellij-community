// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.html;

import com.intellij.html.testFramework.HtmlParsingTestBase;
import com.intellij.lang.html.HTMLParserDefinition;
import com.intellij.testFramework.junit5.TestApplication;

@TestApplication
public class HtmlParsingTest extends HtmlParsingTestBase {

  public HtmlParsingTest() {
    super("psi/html", "html", new HTMLParserDefinition());
  }

}
