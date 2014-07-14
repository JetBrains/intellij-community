/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;

import java.io.File;

/**
 * User: zolotov
 * Date: 10/9/13
 */
@TestDataPath("$CONTENT_ROOT/testData/selectWord")
public class HtmlSelectWordTest extends LightCodeInsightFixtureTestCase {

  private boolean oldSelectWholeCssSelectorOptionValue;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    oldSelectWholeCssSelectorOptionValue = WebEditorOptions.getInstance().isSelectWholeCssIdentifierOnDoubleClick();
  }

  @Override
  public void tearDown() throws Exception {
    WebEditorOptions.getInstance().setSelectWholeCssIdentifierOnDoubleClick(oldSelectWholeCssSelectorOptionValue);
    super.tearDown();
  }
  
  public void testSelectClassNames() {
    WebEditorOptions.getInstance().setSelectWholeCssIdentifierOnDoubleClick(true);
    doTest();
  }

  public void testSelectClassNamesWithDisabledSelectSelectorOption() {
    WebEditorOptions.getInstance().setSelectWholeCssIdentifierOnDoubleClick(false);
    doTest();
  }

  public void testSelectTag() {
    doTest();
  }

  private void doTest() {
    CodeInsightTestUtil.doWordSelectionTestOnDirectory(myFixture, getTestName(true), "html");
  }

  @Override
  protected String getTestDataPath() {
    return PlatformTestUtil.getCommunityPath().replace(File.separatorChar, '/') + "/xml/tests/testData/selectWord";
  }
}
