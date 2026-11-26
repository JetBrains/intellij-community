package org.intellij.plugins.markdown.folding;

import com.intellij.codeInsight.folding.CodeFoldingManager;
import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.lang.folding.LanguageFolding;
import com.intellij.lang.html.HTMLLanguage;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.impl.FoldingKeys;
import com.intellij.openapi.fileEditor.impl.text.CodeFoldingState;
import com.intellij.openapi.project.Project;
import com.intellij.psi.LanguageFileViewProviders;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.testFramework.CoroutineKt;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.TestDisposable;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.intellij.plugins.markdown.lang.MarkdownFileViewProviderFactory;
import org.intellij.plugins.markdown.lang.MarkdownLanguage;
import org.jdom.Element;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.time.Duration;
import java.util.Arrays;
import java.util.Objects;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
@TestDataPath("$PROJECT_ROOT/community/plugins/markdown/test/data/folding")
public class MarkdownFoldingTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);
  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = codeInsightModuleFixture(projectFixture);

  private String testName;

  @BeforeEach
  void setUp(TestInfo testInfo) {
    testName = PlatformTestUtil.getTestName(testInfo.getTestMethod().orElseThrow().getName(), true);
  }

  @Test
  public void testOrderedList() {
    doTest();
  }

  @Test
  public void testUnorderedList() {
    doTest();
  }

  @Test
  public void testComplexOrderedList() {
    doTest();
  }

  @Test
  public void testComplexUnorderedList() {
    doTest();
  }

  @Test
  public void testSingleLineOrderedList() {
    doTest();
  }

  @Test
  public void testSingleLineUnorderedList() {
    doTest();
  }

  @Test
  public void testUnorderedSublist() {
    doTest();
  }

  @Test
  public void testTable() {
    doTest();
  }

  @Test
  public void testCodeFence() {
    doTest();
  }

  @Test
  public void testHtmlCommentFoldingStateIsRestored() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    String text = """
      <!--
      multiline
      comment
      -->
      """;
    myFixture.configureByText("test.md", text);
    Editor editor = myFixture.getEditor();
    int commentStart = text.indexOf("<!--") + "<!--".length();
    CodeFoldingManager manager = CodeFoldingManager.getInstance(myFixture.getProject());
    EdtTestUtil.runInEdtAndWait(() -> {
      EditorTestUtil.buildInitialFoldingsInBackground(editor);
      FoldRegion commentRegion = Arrays.stream(editor.getFoldingModel().getAllFoldRegions())
        .filter(region -> region.getStartOffset() == commentStart)
        .findFirst()
        .orElseThrow();

      editor.getFoldingModel().runBatchFoldingOperation(() -> commentRegion.setExpanded(false));
      CodeFoldingState state = manager.saveFoldingState(editor);
      Element serializedState = new Element("folding");
      manager.writeFoldingState(state, serializedState);
      CodeFoldingState pendingState = manager.readFoldingState(serializedState, editor.getDocument());

      editor.getFoldingModel().runBatchFoldingOperation(() -> commentRegion.setExpanded(true));
      manager.restoreFoldingState(editor, pendingState);
      NonBlockingReadActionImpl.waitForAsyncTaskCompletion();

      assertFalse(commentRegion.isExpanded());
    });
  }

  @Test
  public void testBlockQuote() {
    doTest();
  }

  @Test
  public void testSingleHeader() {
    doTest();
  }

  @Test
  public void testLongHeader() {
    doTest();
  }

  @Test
  public void testMultipleHeaders() {
    doTest();
  }

  @Test
  public void testHeaderPlaceholderUsesHeaderTextOnly() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    String text = """
      ## Lorem ipsum dolor sed emit
      Lorem
      ## Lorem ipsum dolor sed emit est
      Ipsum
      """;
    myFixture.configureByText("test.md", text);

    String foldingDescription = ((CodeInsightTestFixtureImpl)myFixture).getFoldingDescription(false, false);
    assertTrue(foldingDescription.contains("text='h2: ## Lorem ipsum dolor sed emit'"), foldingDescription);
    assertTrue(foldingDescription.contains("text='h2: ## Lorem ipsum dolor sed emit est'"), foldingDescription);
  }

  @Test
  public void testSetextHeaderPlaceholderUsesContentLine() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("test.md", """
      Setext title
      ============

      Setext body
      """);

    String foldingDescription = ((CodeInsightTestFixtureImpl)myFixture).getFoldingDescription(false, false);
    assertTrue(foldingDescription.contains("text='Setext title'"), foldingDescription);
  }

  @Test
  public void testLongHeaderPlaceholderIsTruncatedAt120Characters() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    String longHeader = "## " + "x".repeat(130);
    myFixture.configureByText("test.md", longHeader + "\nBody");

    String foldingDescription = ((CodeInsightTestFixtureImpl)myFixture).getFoldingDescription(false, false);
    String expectedPlaceholder = "text='h2: ## " + "x".repeat(109) + "..." + "x".repeat(5) + "'";
    assertTrue(foldingDescription.contains(expectedPlaceholder), foldingDescription);
  }

  @Test
  public void testListMultiLineItem() {
    doTest();
  }

  @Test
  public void testSingleLineParagraph() {
    doTest();
  }

  @Test
  public void testSimpleHeadersStructure() {
    doTest();
  }

  @Test
  public void testHeadersTree() {
    doTest();
  }

  @Test
  public void testIrregularHeadersStructure() {
    doTest();
  }

  @Test
  public void testMultiLineParagraph() {
    doTest();
  }

  @Test
  public void testMultiLineQuoteParagraph() {
    doTest();
  }

  @Test
  public void testMultiParagraphListItem() {
    doTest();
  }

  @Test
  public void testTwoMultiLineParagraphs() {
    doTest();
  }

  @Test
  public void testTwoMultiLineQuoteParagraphs() {
    doTest();
  }

  @Test
  public void testLinkDestinations() {
    doTest();
  }

  /** IJPL-162519: visual table alignment supports folded link destinations. */
  @Test
  public void testLinkDestinationsInsideTableAreFolded() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("table.md", """
      [Regular link](https://jetbrains.com/path)

      | Server | Address                                                   |
      |--------|-----------------------------------------------------------|
      | Client | [http://localhost](http://localhost/variable-length-link) |
      | Server | [http://localhost](http://localhost/variable)             |
      """);

    String foldingDescription = ((CodeInsightTestFixtureImpl)myFixture).getFoldingDescription(false, false);
    assertTrue(foldingDescription.contains("[Regular link](<fold text='...'>https://jetbrains.com/path</fold>)"), foldingDescription);
    assertTrue(foldingDescription.contains("[http://localhost](<fold text='...'>http://localhost/variable-length-link</fold>)"), foldingDescription);
    assertTrue(foldingDescription.contains("[http://localhost](<fold text='...'>http://localhost/variable</fold>)"), foldingDescription);
  }

  @Test
  public void testTableOfContents() {
    doTest();
  }

  @Test
  public void testFrontMatter() {
    doTest();
  }

  @Test
  public void testFrontMatterIsNotFoldedWhenAPolicyForbidsIt(@TestDisposable Disposable disposable) {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    MarkdownFrontMatterFoldingPolicy.EP_NAME.getPoint().registerExtension(_ -> false, disposable);
    myFixture.configureByText("notes.md", "---\nSome front matter\n---\n\nText");

    String foldingDescription = ((CodeInsightTestFixtureImpl)myFixture).getFoldingDescription(false, false);
    assertFalse(foldingDescription.contains("<fold"), foldingDescription);
  }

  @Test
  public void testFrontMatterIsNotFoldedWhenOneOfSeveralPoliciesForbidsIt(@TestDisposable Disposable disposable) {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    var point = MarkdownFrontMatterFoldingPolicy.EP_NAME.getPoint();
    point.registerExtension(_ -> true, disposable);
    point.registerExtension(_ -> false, disposable);
    point.registerExtension(_ -> true, disposable);
    myFixture.configureByText("notes.md", "---\nSome front matter\n---\n\nText");

    String foldingDescription = ((CodeInsightTestFixtureImpl)myFixture).getFoldingDescription(false, false);
    assertFalse(foldingDescription.contains("<fold"), foldingDescription);
  }

  @Test
  public void testFrontMatterIsFoldedWhenEveryPolicyAllowsIt(@TestDisposable Disposable disposable) {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    var point = MarkdownFrontMatterFoldingPolicy.EP_NAME.getPoint();
    point.registerExtension(_ -> true, disposable);
    point.registerExtension(_ -> true, disposable);
    myFixture.configureByText("notes.md", "---\nSome front matter\n---\n\nText");

    String foldingDescription = ((CodeInsightTestFixtureImpl)myFixture).getFoldingDescription(false, false);
    assertTrue(foldingDescription.contains("<fold"), foldingDescription);
  }

  @Test
  public void testDetails() {
    doTest();
  }

  @Test
  @SuppressWarnings("deprecation")
  public void testDetailsHaveNoDuplicateGenericFoldsAfterCacheRestore() {
    CoroutineKt.runTestInCoroutineScope(() -> {
      var myFixture = codeInsightFixture.get();
      ApplicationManager.getApplication().invokeAndWait(() -> {
        var file = myFixture.configureByText("details.md", """
          <details>
          <summary>The summary</summary>

          Hidden content.

          </details>
          """);
        var editor = myFixture.getEditor();
        var html = Objects.requireNonNull(file.getViewProvider().getPsi(HTMLLanguage.INSTANCE));
        var builder = LanguageFolding.INSTANCE.forLanguage(HTMLLanguage.INSTANCE);
        var range = LanguageFolding.buildFoldingDescriptors(builder, html, editor.getDocument(), true)[0].getRange();
        editor.getFoldingModel().runBatchFoldingOperation(() -> {
          var region = Objects.requireNonNull(
            editor.getFoldingModel().addFoldRegion(range.getStartOffset(), range.getEndOffset(), "..."));
          region.putUserData(FoldingKeys.ZOMBIE_REGION_KEY, true);
        });

        EditorTestUtil.buildInitialFoldingsInBackground(editor);

        var regions = editor.getFoldingModel().getAllFoldRegions();
        assertEquals(1, regions.length);
        assertEquals(range, regions[0].getTextRange());
        assertEquals("▶ The summary", regions[0].getPlaceholderText());
        var text = editor.getDocument().getText();
        var collapsedText = text.substring(0, regions[0].getStartOffset()) + regions[0].getPlaceholderText() +
                            text.substring(regions[0].getEndOffset());
        assertEquals("<details▶ The summary>", collapsedText.stripTrailing());
      });
    }, Duration.ofSeconds(10));
  }

  @Test
  public void testDetailsWithoutHtmlRoot(@TestDisposable Disposable disposable) {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    LanguageFileViewProviders.INSTANCE.addExplicitExtension(
      MarkdownLanguage.INSTANCE, new MarkdownFileViewProviderFactory(), disposable);
    var block = "<details>\n<summary>The summary</summary>\n\n## Heading\n\nHeading content.\n\n</details>";
    myFixture.configureByText("details.md", block + "\n\nText\n");
    assertEquals(1, myFixture.getFile().getViewProvider().getLanguages().size());

    var foldingDescription = ((CodeInsightTestFixtureImpl)myFixture).getFoldingDescription(false, false);
    assertFalse(foldingDescription.contains("▶"), foldingDescription);
    assertTrue(foldingDescription.contains("<fold text='h2: ## Heading'>"), foldingDescription);
  }

  @Test
  public void testDetailsFoldAppearsWhenClosingTagIsTyped() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    var open = "<details>\n<summary>The summary</summary>\n\nContent.\n\n";
    myFixture.configureByText("details.md", open + "\nText\n");
    var before = ((CodeInsightTestFixtureImpl)myFixture).getFoldingDescription(false, false);
    assertFalse(before.contains("▶"), before);

    Project project = myFixture.getProject();
    var after = EdtTestUtil.runInEdtAndGet(() -> {
      var editor = myFixture.getEditor();
      var initialRegions = editor.getFoldingModel().getAllFoldRegions();
      assertEquals(1, initialRegions.length);
      assertEquals("...", initialRegions[0].getPlaceholderText());
      assertTrue(initialRegions[0].isExpanded());
      editor.getCaretModel().moveToOffset(editor.getDocument().getTextLength());
      editor.getFoldingModel().runBatchFoldingOperation(() -> {
        for (var region : editor.getFoldingModel().getAllFoldRegions()) {
          region.setExpanded(false);
        }
      });
      WriteCommandAction.runWriteCommandAction(project, () -> editor.getDocument().insertString(open.length(), "</details>\n"));
      PsiDocumentManager.getInstance(project).commitAllDocuments();
      CodeFoldingManager.getInstance(project).updateFoldRegions(editor);
      return CodeInsightTestFixtureImpl.getFoldingData(editor, false, false);
    });
    assertTrue(after.contains("<details<fold text='▶ The summary'>"), after);
    assertFalse(after.contains("<fold text='...'>"), after);
  }

  private void doTest() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.testFolding(myFixture.getTestDataPath() + "/" + testName + ".md");
  }
}
