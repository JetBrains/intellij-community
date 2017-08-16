/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.lang.properties;

import com.intellij.lang.LanguageASTFactory;
import com.intellij.lang.properties.parsing.PropertiesParserDefinition;
import com.intellij.lang.properties.psi.impl.PropertiesASTFactory;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.testFramework.ParsingTestCase;

/**
 * @author max
 */
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
    return PluginPathManager.getPluginHomePath("properties") + "/testData/propertiesFile/psi";
  }

  public void testProp1() { doTest(true); }
  public void testComments() { doTest(true); }
}
