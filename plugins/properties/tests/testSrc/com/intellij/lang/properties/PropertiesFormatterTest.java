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

import com.intellij.application.options.CodeStyle;
import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.lang.properties.psi.codeStyle.PropertiesCodeStyleSettings;
import com.intellij.openapi.project.Project;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.formatter.FormatterTestUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;

/**
 * @author Dmitry Batkovich
 */
@TestApplication
public class PropertiesFormatterTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = codeInsightModuleFixture(projectFixture);

  private Project project;
  private CommonCodeStyleSettings mySettings;
  private PropertiesCodeStyleSettings myCustomSettings;

  @BeforeEach
  void setUp() {
    project = codeInsightFixture.get().getProject();
    FormatterTestUtil.assertNoProjectCodeStyle(project);
    final CodeStyleSettings settings = CodeStyle.getSettings(project);
    mySettings = settings.getCommonSettings(PropertiesLanguage.INSTANCE);
    myCustomSettings = settings.getCustomSettings(PropertiesCodeStyleSettings.class);
    mySettings.ALIGN_GROUP_FIELD_DECLARATIONS = false;
    myCustomSettings.SPACES_AROUND_KEY_VALUE_DELIMITER = false;
    myCustomSettings.KEEP_BLANK_LINES = false;
  }

  @Test
  public void testSimple1() {
    doTextTest("""


                 qwe=asd
                 #comment
                   key1  =   value1""",

               """
                 qwe=asd
                 #comment
                 key1=value1""");
  }

  @Test
  public void testSimple2() {
    mySettings.ALIGN_GROUP_FIELD_DECLARATIONS = true;
    doTextTest("""
                     qwe_very_big_property   =  asd
                 #comment
                   key1  =   value1""",

               """
                 qwe_very_big_property=asd
                 #comment
                 key1                 =value1""");
  }

  @Test
  public void testSimple3() {
    mySettings.ALIGN_GROUP_FIELD_DECLARATIONS = true;
    myCustomSettings.SPACES_AROUND_KEY_VALUE_DELIMITER = true;
    doTextTest("""
                     qwe_very_big_property   =  asd
                 #comment
                   key1  =   value1""",

               """
                 qwe_very_big_property = asd
                 #comment
                 key1                  = value1""");
  }

  @Test
  public void testWhitespaceDelimiter() {
    doTextTest("k    v", "k v");
  }

  @Test
  public void testKeepBlankLines() {
    myCustomSettings.KEEP_BLANK_LINES = true;
    String propertiesWithBlankLines =
      """
        #comment

        key1=value1


        key2=value2
        """;

    doTextTest(propertiesWithBlankLines, propertiesWithBlankLines);
  }

  @Test
  public void testDoNotKeepBlankLines() {
    myCustomSettings.KEEP_BLANK_LINES = false;
    doTextTest("""
                 #comment

                 key1=value1


                 key2=value2
                 """,

               """
                 #comment
                 key1=value1
                 key2=value2
                 """);
  }

  private void doTextTest(String text) {
    doTextTest(text, text);
  }

  private void doTextTest(String text, String textAfter) {
    FormatterTestUtil.doTextTest(project, PropertiesFileType.DEFAULT_EXTENSION, text, textAfter);
  }
}
