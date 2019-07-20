package org.intellij.plugins.markdown.html;

import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.intellij.plugins.markdown.MarkdownTestingUtil;
import org.intellij.plugins.markdown.ui.preview.MarkdownUtil;
import org.jetbrains.annotations.NotNull;

public class MarkdownHtmlGenerationTest extends BasePlatformTestCase {
  @NotNull
  @Override
  protected String getTestDataPath() {
    return MarkdownTestingUtil.TEST_DATA_PATH + "/html";
  }

  private void doTest(@NotNull String htmlText) {
    PsiFile mdFile = myFixture.configureByFile(getTestName(true) + ".md");

    assertTrue(MarkdownUtil.INSTANCE.generateMarkdownHtml(mdFile.getVirtualFile(), mdFile.getText(), getProject()).contains(htmlText));
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
