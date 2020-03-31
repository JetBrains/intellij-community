// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon;

import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.xml.util.CheckXmlFileWithXercesValidatorInspection;

public class XmlSimpleHighlightingTest extends BasePlatformTestCase {

  public void testCannotFindDeclaration() {
    checkValidation("<idea-plugin xmlns:xi=\"http://www.w3.org/2001/XInclude\">\n" +
                    "</idea-plugin>");
  }

  public void testUnsupportedIncludeScheme() {
    checkValidation("<idea-plugin xmlns:xi=\"http://www.w3.org/2001/XInclude\">\n" +
                    "  <xi:include href=\"extensionPoints.xml\" xpointer=\"xpointer(/extensionPoints/*)\"/>\n" +
                    "</idea-plugin>");
  }

  public void testSupportedIncludeScheme() {
    checkValidation("<idea-plugin xmlns:xi=\"http://www.w3.org/2001/XInclude\">\n" +
                    "  <<error descr=\"An 'include' failed, and no 'fallback' element was found.\">xi:include</error> href=\"extensionPoints.xml\" xpointer=\"element(/1111)\"/>\n" +
                    "</idea-plugin>");
  }

  private void checkValidation(String text) {
    myFixture.enableInspections(CheckXmlFileWithXercesValidatorInspection.class);
    myFixture.configureByText(XmlFileType.INSTANCE, text);
    myFixture.testHighlighting();
  }
}
