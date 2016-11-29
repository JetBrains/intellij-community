/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.lang.properties.psi.codeStyle.PropertiesCodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.formatter.FormatterTestCase;


/**
 * @author Dmitry Batkovich
 */
public class PropertiesFormatterTest extends FormatterTestCase {
  private CommonCodeStyleSettings mySettings;
  private PropertiesCodeStyleSettings myCustomSettings;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    final CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(getProject());
    mySettings = settings.getCommonSettings(PropertiesLanguage.INSTANCE);
    myCustomSettings = settings.getCustomSettings(PropertiesCodeStyleSettings.class);
    mySettings.ALIGN_GROUP_FIELD_DECLARATIONS = false;
    myCustomSettings.SPACES_AROUND_KEY_VALUE_DELIMITER = false;
    myCustomSettings.KEEP_BLANK_LINES = false;
  }

  @Override
  public void tearDown() throws Exception {
    super.tearDown();
  }

  public void testSimple1() {
    doTextTest("\n\n" +
               "qwe=asd\n" +
               "#comment\n" +
               "  key1  =   value1",

               "qwe=asd\n" +
               "#comment\n" +
               "key1=value1");
  }

  public void testSimple2() {
    mySettings.ALIGN_GROUP_FIELD_DECLARATIONS = true;
    doTextTest("    qwe_very_big_property   =  asd\n" +
               "#comment\n" +
               "  key1  =   value1",

               "qwe_very_big_property=asd\n" +
               "#comment\n" +
               "key1                 =value1");
  }

  public void testSimple3() {
    mySettings.ALIGN_GROUP_FIELD_DECLARATIONS = true;
    myCustomSettings.SPACES_AROUND_KEY_VALUE_DELIMITER = true;
    doTextTest("    qwe_very_big_property   =  asd\n" +
               "#comment\n" +
               "  key1  =   value1",

               "qwe_very_big_property = asd\n" +
               "#comment\n" +
               "key1                  = value1");
  }

  public void testWhitespaceDelimiter() {
    doTextTest("k    v", "k v");
  }

  public void testKeepBlankLines() {
    myCustomSettings.KEEP_BLANK_LINES = true;
    String propertiesWithBlankLines =
      "#comment\n" +
      "\n" +
      "key1=value1\n" +
      "\n" +
      "\n" +
      "key2=value2\n";

    doTextTest(propertiesWithBlankLines, propertiesWithBlankLines);
  }

  public void testDoNotKeepBlankLines() {
    myCustomSettings.KEEP_BLANK_LINES = false;
    doTextTest("#comment\n" +
               "\n" +
               "key1=value1\n" +
               "\n" +
               "\n" +
               "key2=value2\n",

               "#comment\n" +
               "key1=value1\n" +
               "key2=value2\n");
  }

  protected void doTextTest(String text) {
    doTextTest(text, text);
  }

  @Override
  protected String getBasePath() {
    return "";
  }

  @Override
  protected String getFileExtension() {
    return PropertiesFileType.DEFAULT_EXTENSION;
  }
}
