// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.html;

import com.intellij.idea.TestFor;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.intellij.plugins.markdown.MarkdownTestingUtil;
import org.intellij.plugins.markdown.ui.preview.html.MarkdownUtil;
import org.jetbrains.annotations.NotNull;

public class MarkdownHtmlGenerationTest extends BasePlatformTestCase {
  private EditorColorsScheme myOriginalScheme;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    EditorColorsManager manager = EditorColorsManager.getInstance();
    myOriginalScheme = manager.getGlobalScheme();
    EditorColorsScheme emptyScheme = manager.getScheme("Empty");
    if (emptyScheme != null) {
      manager.setGlobalScheme(emptyScheme);
    }
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      if (myOriginalScheme != null) {
        EditorColorsManager.getInstance().setGlobalScheme(myOriginalScheme);
      }
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return MarkdownTestingUtil.TEST_DATA_PATH + "/html";
  }

  private void doTest(@NotNull String htmlText) {
    PsiFile mdFile = myFixture.configureByFile(getTestName(true) + ".md");

    assertEquals(htmlText.trim(), MarkdownUtil.INSTANCE.generateMarkdownHtml(mdFile.getVirtualFile(), mdFile.getText(), getProject()).trim());
  }

  public void testImageDestinationInAngleBrackets() {
    doTestByHtmlFile();
  }

  public void testCodeFenceWithLang() {
    doTestByHtmlFile();
  }

  public void testCodeFenceWithoutLang() {
    doTestByHtmlFile();
  }

  public void testIndentedCodeFenceWithLongIndent() {
    String content = "            ```\n            line\n            ```";
    PsiFile mdFile = myFixture.configureByText("test.md", content);
    String html = MarkdownUtil.INSTANCE.generateMarkdownHtml(mdFile.getVirtualFile(), mdFile.getText(), getProject());

    assertFalse(html.contains(">  line"));
  }

  public void testTabIndentedCodeFenceTrimsIndent() {
    String content = "\t```\n\tline\n\t```";
    PsiFile mdFile = myFixture.configureByText("test.md", content);
    String html = MarkdownUtil.INSTANCE.generateMarkdownHtml(mdFile.getVirtualFile(), mdFile.getText(), getProject());

    assertFalse(html.contains("\tline"));
    assertTrue(html.contains("line"));
  }

  public void testMixedSpaceAndTabIndentedCodeFenceTrimsIndent() {
    String content = "  \t```\n  \tline\n  \t```";
    PsiFile mdFile = myFixture.configureByText("test.md", content);
    String html = MarkdownUtil.INSTANCE.generateMarkdownHtml(mdFile.getVirtualFile(), mdFile.getText(), getProject());

    assertTrue(html.contains(">line\n"));
    assertFalse(html.contains("> line\n"));
  }

  public void testIndentedCodeFenceCopyContentMatchesPreview() {
    String content = "    ```\n    line\n        nested\n    ```";
    PsiFile mdFile = myFixture.configureByText("test.md", content);
    String html = MarkdownUtil.INSTANCE.generateMarkdownHtml(mdFile.getVirtualFile(), mdFile.getText(), getProject());

    String expectedContent = java.util.Base64.getEncoder().encodeToString("line\n    nested".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    assertTrue(html.contains("data-fence-content=\"" + expectedContent + "\""));
  }

  public void testFootnoteContinuationUsesMinimumIndent() {
    String content = "See[^note]\n\n[^note]: First paragraph.\n\n        outer\n    inner\n";
    PsiFile mdFile = myFixture.configureByText("test.md", content);
    String html = MarkdownUtil.INSTANCE.generateMarkdownHtml(mdFile.getVirtualFile(), mdFile.getText(), getProject());

    assertTrue(html.contains("<pre><code>outer\n</code></pre><p>inner"));
  }

  public void testFootnoteFenceUsesOpeningIndent() {
    String content = "See[^note]\n\n[^note]: First paragraph.\n\n        ```java\n    fence code\n        ```\n";
    PsiFile mdFile = myFixture.configureByText("test.md", content);
    String html = MarkdownUtil.INSTANCE.generateMarkdownHtml(mdFile.getVirtualFile(), mdFile.getText(), getProject());

    assertTrue(html.contains("<pre><code class=\"language-java\">fence code\n</code></pre>"));
  }

  public void testNestedIndentedCodeFenceUsesOpeningIndent() {
    String content = "- item\n\n      ```\n      line\n      ```";
    PsiFile mdFile = myFixture.configureByText("test.md", content);
    String html = MarkdownUtil.INSTANCE.generateMarkdownHtml(mdFile.getVirtualFile(), mdFile.getText(), getProject());

    String expectedContent = java.util.Base64.getEncoder().encodeToString("line".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    assertTrue(html.contains("data-fence-content=\"" + expectedContent + "\""));
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

  @TestFor(issues = "IDEA-259656")
  public void testBackslashesInText() {
    doTestByHtmlFile();
  }

  public void testAtPath() {
    doTestByHtmlFile();
  }

  public void testFootnoteBasic() {
    doTestByHtmlFile();
  }

  public void testFootnoteWithoutReference() {
    doTestByHtmlFile();
  }

  public void testFootnoteRegularReferenceLinksUnaffected() {
    doTestByHtmlFile();
  }

  public void testFootnoteFullReferenceLinksUnaffected() {
    doTestByHtmlFile();
  }

  public void testFootnoteMultiline() {
    doTestByHtmlFile();
  }

  public void testFootnoteIndentedCodeBlockUnaffected() {
    doTestByHtmlFile();
  }

  public void testFootnoteMultilineWithCode() {
    doTestByHtmlFile();
  }

  public void testFootnoteMultilineWithCodeLines() {
    doTestByHtmlFile();
  }

  public void testFootnoteInlineMarkdown() {
    doTestByHtmlFile();
  }

  public void testFootnoteBlockquote() {
    doTestByHtmlFile();
  }

  public void testFootnoteCodeFence() {
    doTestByHtmlFile();
  }

  public void testFootnoteConsecutiveReferences() {
    doTestByHtmlFile();
  }

  public void testFootnoteMultilineCodeBlockWithBlankLine() {
    doTestByHtmlFile();
  }

  public void testFootnoteNested() {
    doTestByHtmlFile();
  }

  public void testFootnoteLabelWithSpaceUnaffected() {
    doTestByHtmlFile();
  }

  public void testFootnoteTabIndentedContinuation() {
    doTestByHtmlFile();
  }

  public void testFootnoteBlockquoteEqualIndentFenceOutside() {
    doTestByHtmlFile();
  }

  public void testFootnoteBlockquoteIndentedFenceContinuation() {
    doTestByHtmlFile();
  }

  public void testFootnoteBodyWithDefinedReference() {
    doTestByHtmlFile();
  }

  void doTestByHtmlFile() {
    doTest(myFixture.configureByFile(getTestName(true) + ".html").getText());
  }
}
