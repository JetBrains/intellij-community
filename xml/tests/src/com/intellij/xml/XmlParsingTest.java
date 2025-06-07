// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml;

import com.intellij.lang.xml.XMLParserDefinition;
import com.intellij.xml.testFramework.XmlParsingTestBase;

public class XmlParsingTest extends XmlParsingTestBase {
  public XmlParsingTest() {
    super("psi/xml", "xml", new XMLParserDefinition());
  }
}
