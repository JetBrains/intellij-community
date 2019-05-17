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
package org.intellij.plugins.markdown.parser;

import com.intellij.lang.LanguageASTFactory;
import com.intellij.testFramework.ParsingTestCase;
import org.intellij.plugins.markdown.MarkdownTestingUtil;
import org.intellij.plugins.markdown.highlighting.MarkdownColorSettingsPage;
import org.intellij.plugins.markdown.lang.MarkdownLanguage;
import org.intellij.plugins.markdown.lang.parser.MarkdownParserDefinition;
import org.intellij.plugins.markdown.lang.psi.MarkdownASTFactory;

import java.io.IOException;

public class MarkdownParserTest extends ParsingTestCase {

  public MarkdownParserTest() {
    super("parser", "md", true, new MarkdownParserDefinition());
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    addExplicitExtension(LanguageASTFactory.INSTANCE, MarkdownLanguage.INSTANCE, new MarkdownASTFactory());
  }

  @Override
  protected String getTestDataPath() {
    return MarkdownTestingUtil.TEST_DATA_PATH;
  }

  public void testColorsAndFontsSample() throws IOException {
    final MarkdownColorSettingsPage colorSettingsPage = new MarkdownColorSettingsPage();
    String demoText = colorSettingsPage.getDemoText();
    for (String tag : colorSettingsPage.getAdditionalHighlightingTagToDescriptorMap().keySet()) {
      demoText = demoText.replaceAll("<" + tag + ">", "");
      demoText = demoText.replaceAll("</" + tag + ">", "");
    }
    doCodeTest(demoText);
  }

  public void testCodeBlock() {
    doTest(true);
  }

  public void testComment() {
    doTest(true);
  }
}
