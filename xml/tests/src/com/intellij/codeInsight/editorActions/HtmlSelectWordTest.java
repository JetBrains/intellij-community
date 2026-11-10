/*
 * Copyright 2000-2018 JetBrains s.r.o.
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
package com.intellij.codeInsight.editorActions;

import com.intellij.application.options.editor.WebEditorOptions;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.CodeInsightTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestDataPath("$CONTENT_ROOT/testData/selectWord")
@TestApplication
public class HtmlSelectWordTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  private boolean oldSelectWholeCssSelectorOptionValue;

  @BeforeEach
  void setUp() {
    codeInsightFixture.get().setTestDataPath(PlatformTestUtil.getCommunityPath().replace(File.separatorChar, '/') + "/xml/tests/testData/selectWord");
    oldSelectWholeCssSelectorOptionValue = WebEditorOptions.getInstance().isSelectWholeCssIdentifierOnDoubleClick();
  }

  @AfterEach
  void tearDown() {
    WebEditorOptions.getInstance().setSelectWholeCssIdentifierOnDoubleClick(oldSelectWholeCssSelectorOptionValue);
  }
  
  @Test
  public void testSelectClassNames() {
    WebEditorOptions.getInstance().setSelectWholeCssIdentifierOnDoubleClick(true);
    doTest();
  }

  @Test
  public void testSelectClassNamesWithDisabledSelectSelectorOption() {
    WebEditorOptions.getInstance().setSelectWholeCssIdentifierOnDoubleClick(false);
    doTest();
  }

  @Test
  public void testSelectTag() {
    doTest();
  }

  private void doTest() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    CodeInsightTestUtil.doWordSelectionTestOnDirectory(myFixture, myFixture.getTestName(true), "html");
  }
}
