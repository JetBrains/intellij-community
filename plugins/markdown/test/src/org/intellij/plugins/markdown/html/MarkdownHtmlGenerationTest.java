// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.html;

import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.intellij.plugins.markdown.MarkdownTestingUtil;
import org.intellij.plugins.markdown.ui.preview.html.MarkdownUtil;
import org.jetbrains.annotations.NotNull;

public class MarkdownHtmlGenerationTest extends BasePlatformTestCase {
  @NotNull
  @Override
  protected String getTestDataPath() {
    return MarkdownTestingUtil.TEST_DATA_PATH + "/html";
  }

  private void doTest(@NotNull String htmlText) {
    PsiFile mdFile = myFixture.configureByFile(getTestName(true) + ".md");

    assertEquals(htmlText.trim(), MarkdownUtil.INSTANCE.generateMarkdownHtml(mdFile.getVirtualFile(), mdFile.getText(), getProject()).trim());
  }

  public void testCodeFenceWithLang() {
    doTestByHtmlFile();
  }

  public void testCodeFenceWithoutLang() {
    doTestByHtmlFile();
  }

  public void testXmlTags() {
    doTestByHtmlFile();
  }

  public void testHtmlTags() {
    doTestByHtmlFile();
  }

  public void testComment() {
    doTestByHtmlFile();
  }

  void doTestByHtmlFile() {
    doTest(myFixture.configureByFile(getTestName(true) + ".html").getText());
  }
}
