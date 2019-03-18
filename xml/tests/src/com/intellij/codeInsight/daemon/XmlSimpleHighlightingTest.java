// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon;

import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;
import com.intellij.xml.util.CheckXmlFileWithXercesValidatorInspection;

public class XmlSimpleHighlightingTest extends LightPlatformCodeInsightFixtureTestCase {

  public void testCannotFindDeclaration() {
    myFixture.enableInspections(CheckXmlFileWithXercesValidatorInspection.class);
    myFixture.configureByText(XmlFileType.INSTANCE, "<idea-plugin xmlns:xi=\"http://www.w3.org/2001/XInclude\">\n" +
                                                    "</idea-plugin>");
    myFixture.testHighlighting();
  }
}
