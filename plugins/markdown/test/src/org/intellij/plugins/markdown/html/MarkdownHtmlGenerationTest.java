// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.html;

import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.idea.TestFor;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.intellij.plugins.markdown.ui.preview.html.MarkdownUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
@TestDataPath("$PROJECT_ROOT/community/plugins/markdown/test/data/html")
public class MarkdownHtmlGenerationTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);
  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = codeInsightModuleFixture(projectFixture);

  private EditorColorsScheme myOriginalScheme;

  private String testName;

  @BeforeEach
  void setUp(TestInfo testInfo) {
    testName = PlatformTestUtil.getTestName(testInfo.getTestMethod().orElseThrow().getName(), true);
    EditorColorsManager manager = EditorColorsManager.getInstance();
    myOriginalScheme = manager.getGlobalScheme();
    EditorColorsScheme emptyScheme = manager.getScheme("Empty");
    if (emptyScheme != null) {
      manager.setGlobalScheme(emptyScheme);
    }
  }

  @AfterEach
  void tearDown() {
    if (myOriginalScheme != null) {
      EditorColorsManager.getInstance().setGlobalScheme(myOriginalScheme);
    }
  }

  private void doTest(@NotNull String htmlText) {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiFile mdFile = myFixture.configureByFile(testName + ".md");

    assertEquals(htmlText.trim(), generateHtml(mdFile).trim());
  }

  private @NotNull String generateHtml(@NotNull PsiFile mdFile) {
    return ReadAction.computeBlocking(
      () -> MarkdownUtil.INSTANCE.generateMarkdownHtml(mdFile.getVirtualFile(), mdFile.getText(), codeInsightFixture.get().getProject()));
  }

  @Test
  public void testImageDestinationInAngleBrackets() {
    doTestByHtmlFile();
  }

  @TestFor(issues = "IJPL-92231")
  @Test
  public void testLinkFromProjectRoot() {
    doTestByHtmlFile();
  }

  @Test
  public void testCodeFenceWithLang() {
    doTestByHtmlFile();
  }

  @Test
  public void testCodeFenceWithoutLang() {
    doTestByHtmlFile();
  }

  @Test
  public void testHighlightIsRenderedAsMark() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    String content = "Some ==marked== text, =one= and ===three===";
    PsiFile mdFile = myFixture.configureByText("test.md", content);
    String html = generateHtml(mdFile);

    assertTrue(html.contains(">marked</mark>"), html);
    assertEquals(1, html.split("<mark", -1).length - 1, html);
    assertEquals("Some marked text, =one= and ===three===", html.replaceAll("<[^>]+>", ""));
  }

  @Test
  public void testIndentedCodeFenceWithLongIndent() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    String content = "            ```\n            line\n            ```";
    PsiFile mdFile = myFixture.configureByText("test.md", content);
    String html = generateHtml(mdFile);

    assertFalse(html.contains(">  line"));
  }

  @Test
  public void testTabIndentedCodeFenceTrimsIndent() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    String content = "\t```\n\tline\n\t```";
    PsiFile mdFile = myFixture.configureByText("test.md", content);
    String html = generateHtml(mdFile);

    assertFalse(html.contains("\tline"));
    assertTrue(html.contains("line"));
  }

  @Test
  public void testMixedSpaceAndTabIndentedCodeFenceTrimsIndent() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    String content = "  \t```\n  \tline\n  \t```";
    PsiFile mdFile = myFixture.configureByText("test.md", content);
    String html = generateHtml(mdFile);

    assertTrue(html.contains(">line\n"));
    assertFalse(html.contains("> line\n"));
  }

  @Test
  public void testEmphasisAroundCodeSpan() {
    doTestByHtmlFile();
  }

  @Test
  public void testIndentedCodeFenceCopyContentMatchesPreview() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    String content = "    ```\n    line\n        nested\n    ```";
    PsiFile mdFile = myFixture.configureByText("test.md", content);
    String html = generateHtml(mdFile);

    String expectedContent = java.util.Base64.getEncoder().encodeToString("line\n    nested".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    assertTrue(html.contains("data-fence-content=\"" + expectedContent + "\""));
  }

  @Test
  public void testFootnoteContinuationUsesMinimumIndent() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    String content = "See[^note]\n\n[^note]: First paragraph.\n\n        outer\n    inner\n";
    PsiFile mdFile = myFixture.configureByText("test.md", content);
    String html = generateHtml(mdFile);

    assertTrue(html.contains("<pre><code>outer\n</code></pre><p>inner"));
  }

  @Test
  public void testFootnoteFenceUsesOpeningIndent() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    String content = "See[^note]\n\n[^note]: First paragraph.\n\n        ```java\n    fence code\n        ```\n";
    PsiFile mdFile = myFixture.configureByText("test.md", content);
    String html = generateHtml(mdFile);

    assertTrue(html.contains("<pre><code class=\"language-java\">fence code\n</code></pre>"));
  }

  @Test
  public void testNestedIndentedCodeFenceUsesOpeningIndent() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    String content = "- item\n\n      ```\n      line\n      ```";
    PsiFile mdFile = myFixture.configureByText("test.md", content);
    String html = generateHtml(mdFile);

    String expectedContent = java.util.Base64.getEncoder().encodeToString("line".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    assertTrue(html.contains("data-fence-content=\"" + expectedContent + "\""));
  }

  @Test
  public void testXmlTags() {
    doTestByHtmlFile();
  }

  @Test
  public void testHtmlTags() {
    doTestByHtmlFile();
  }

  @Test
  public void testComment() {
    doTestByHtmlFile();
  }

  @TestFor(issues = "IDEA-259656")
  @Test
  public void testBackslashesInText() {
    doTestByHtmlFile();
  }

  @Test
  public void testAtPath() {
    doTestByHtmlFile();
  }

  @Test
  public void testFootnoteBasic() {
    doTestByHtmlFile();
  }

  @Test
  public void testFootnoteWithoutReference() {
    doTestByHtmlFile();
  }

  @Test
  public void testFootnoteRegularReferenceLinksUnaffected() {
    doTestByHtmlFile();
  }

  @Test
  public void testFootnoteFullReferenceLinksUnaffected() {
    doTestByHtmlFile();
  }

  @Test
  public void testFootnoteMultiline() {
    doTestByHtmlFile();
  }

  @Test
  public void testFootnoteIndentedCodeBlockUnaffected() {
    doTestByHtmlFile();
  }

  @Test
  public void testFootnoteMultilineWithCode() {
    doTestByHtmlFile();
  }

  @Test
  public void testFootnoteMultilineWithCodeLines() {
    doTestByHtmlFile();
  }

  @Test
  public void testFootnoteInlineMarkdown() {
    doTestByHtmlFile();
  }

  @Test
  public void testFootnoteBlockquote() {
    doTestByHtmlFile();
  }

  @Test
  public void testFootnoteCodeFence() {
    doTestByHtmlFile();
  }

  @Test
  public void testFootnoteConsecutiveReferences() {
    doTestByHtmlFile();
  }

  @Test
  public void testFootnoteMultilineCodeBlockWithBlankLine() {
    doTestByHtmlFile();
  }

  @Test
  public void testFootnoteNested() {
    doTestByHtmlFile();
  }

  @Test
  public void testFootnoteLabelWithSpaceUnaffected() {
    doTestByHtmlFile();
  }

  @Test
  public void testFootnoteTabIndentedContinuation() {
    doTestByHtmlFile();
  }

  @Test
  public void testFootnoteBlockquoteEqualIndentFenceOutside() {
    doTestByHtmlFile();
  }

  @Test
  public void testFootnoteBlockquoteIndentedFenceContinuation() {
    doTestByHtmlFile();
  }

  @Test
  public void testFootnoteBodyWithDefinedReference() {
    doTestByHtmlFile();
  }

  @TestFor(issues = "IJPL-94598")
  @Test
  public void testFrontMatterYaml() {
    doTestByHtmlFile();
  }

  @TestFor(issues = "IJPL-94598")
  @Test
  public void testFrontMatterYamlNestedMapping() {
    doTestByHtmlFile();
  }

  @TestFor(issues = "IJPL-94598")
  @Test
  public void testFrontMatterYamlInvalid() {
    doTestByHtmlFile();
  }

  @TestFor(issues = "IJPL-94598")
  @Test
  public void testFrontMatterYamlAliasExpansion() {
    doTestByHtmlFile();
  }

  void doTestByHtmlFile() {
    PsiFile htmlFile = codeInsightFixture.get().configureByFile(testName + ".html");
    doTest(ReadAction.computeBlocking(() -> htmlFile.getText()));
  }
}
