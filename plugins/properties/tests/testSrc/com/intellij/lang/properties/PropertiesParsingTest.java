// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.properties;

import com.intellij.lang.LanguageASTFactory;
import com.intellij.lang.properties.parsing.PropertiesParserDefinition;
import com.intellij.lang.properties.psi.impl.PropertiesASTFactory;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.testFramework.ParsingTestCase;

public class PropertiesParsingTest extends ParsingTestCase {

  public PropertiesParsingTest() {
    super("", "properties", new PropertiesParserDefinition());
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    addExplicitExtension(LanguageASTFactory.INSTANCE, PropertiesLanguage.INSTANCE, new PropertiesASTFactory());
  }

  @Override
  protected String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("properties") + "/tests/testData/propertiesFile/psi";
  }

  public void testProp1() { doTest(true); }
  public void testComments() { doTest(true); }
  public void testHeader() { doTest(true); }
}
