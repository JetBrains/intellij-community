package org.intellij.plugins.markdown.folding;

import com.intellij.codeInsight.folding.CodeFoldingManager;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.fileEditor.impl.text.CodeFoldingState;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import org.intellij.plugins.markdown.MarkdownTestingUtil;
import org.jdom.Element;

import java.util.Arrays;

public class MarkdownFoldingTest extends BasePlatformTestCase {
  public void testOrderedList() {
    doTest();
  }

  public void testUnorderedList() {
    doTest();
  }

  public void testComplexOrderedList() {
    doTest();
  }

  public void testComplexUnorderedList() {
    doTest();
  }

  public void testSingleLineOrderedList() {
    doTest();
  }

  public void testSingleLineUnorderedList() {
    doTest();
  }

  public void testUnorderedSublist() {
    doTest();
  }

  public void testTable() {
    doTest();
  }

  public void testCodeFence() {
    doTest();
  }

  public void testHtmlCommentFoldingStateIsRestored() {
    String text = """
      <!--
      multiline
      comment
      -->
      """;
    myFixture.configureByText("test.md", text);
    Editor editor = myFixture.getEditor();
    EditorTestUtil.buildInitialFoldingsInBackground(editor);

    int commentStart = text.indexOf("<!--") + "<!--".length();
    FoldRegion commentRegion = Arrays.stream(editor.getFoldingModel().getAllFoldRegions())
      .filter(region -> region.getStartOffset() == commentStart)
      .findFirst()
      .orElseThrow();

    CodeFoldingManager manager = CodeFoldingManager.getInstance(getProject());
    CodeFoldingState pendingState = EdtTestUtil.runInEdtAndGet(() -> {
      editor.getFoldingModel().runBatchFoldingOperation(() -> commentRegion.setExpanded(false));
      CodeFoldingState state = manager.saveFoldingState(editor);
      Element serializedState = new Element("folding");
      manager.writeFoldingState(state, serializedState);
      return manager.readFoldingState(serializedState, editor.getDocument());
    });

    EdtTestUtil.runInEdtAndWait(() -> {
      editor.getFoldingModel().runBatchFoldingOperation(() -> commentRegion.setExpanded(true));
      manager.restoreFoldingState(editor, pendingState);
      NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
    });

    assertFalse(commentRegion.isExpanded());
  }

  public void testBlockQuote() {
    doTest();
  }

  public void testSingleHeader() {
    doTest();
  }

  public void testLongHeader() {
    doTest();
  }

  public void testMultipleHeaders() {
    doTest();
  }

  public void testListMultiLineItem() {
    doTest();
  }

  public void testSingleLineParagraph() {
    doTest();
  }

  public void testSimpleHeadersStructure() {
    doTest();
  }

  public void testHeadersTree() {
    doTest();
  }

  public void testIrregularHeadersStructure() {
    doTest();
  }

  public void testMultiLineParagraph() {
    doTest();
  }

  public void testMultiLineQuoteParagraph() {
    doTest();
  }

  public void testMultiParagraphListItem() {
    doTest();
  }

  public void testTwoMultiLineParagraphs() {
    doTest();
  }

  public void testTwoMultiLineQuoteParagraphs() {
    doTest();
  }

  public void testLinkDestinations() {
    doTest();
  }

  /** IJPL-162519: visual table alignment supports folded link destinations. */
  public void testLinkDestinationsInsideTableAreFolded() {
    myFixture.configureByText("table.md", """
      [Regular link](https://jetbrains.com/path)

      | Server | Address                                                   |
      |--------|-----------------------------------------------------------|
      | Client | [http://localhost](http://localhost/variable-length-link) |
      | Server | [http://localhost](http://localhost/variable)             |
      """);

    String foldingDescription = ((CodeInsightTestFixtureImpl)myFixture).getFoldingDescription(false, false);
    assertTrue(foldingDescription, foldingDescription.contains("[Regular link](<fold text='...'>https://jetbrains.com/path</fold>)"));
    assertTrue(foldingDescription, foldingDescription.contains("[http://localhost](<fold text='...'>http://localhost/variable-length-link</fold>)"));
    assertTrue(foldingDescription, foldingDescription.contains("[http://localhost](<fold text='...'>http://localhost/variable</fold>)"));
  }

  public void testTableOfContents() {
    doTest();
  }

  public void testFrontMatter() {
    doTest();
  }

  public void testFrontMatterIsNotFoldedWhenAPolicyForbidsIt() {
    MarkdownFrontMatterFoldingPolicy.EP_NAME.getPoint().registerExtension(_ -> false, getTestRootDisposable());
    myFixture.configureByText("notes.md", "---\nSome front matter\n---\n\nText");

    String foldingDescription = ((CodeInsightTestFixtureImpl)myFixture).getFoldingDescription(false, false);
    assertFalse(foldingDescription, foldingDescription.contains("<fold"));
  }

  public void testFrontMatterIsNotFoldedWhenOneOfSeveralPoliciesForbidsIt() {
    var point = MarkdownFrontMatterFoldingPolicy.EP_NAME.getPoint();
    point.registerExtension(_ -> true, getTestRootDisposable());
    point.registerExtension(_ -> false, getTestRootDisposable());
    point.registerExtension(_ -> true, getTestRootDisposable());
    myFixture.configureByText("notes.md", "---\nSome front matter\n---\n\nText");

    String foldingDescription = ((CodeInsightTestFixtureImpl)myFixture).getFoldingDescription(false, false);
    assertFalse(foldingDescription, foldingDescription.contains("<fold"));
  }

  public void testFrontMatterIsFoldedWhenEveryPolicyAllowsIt() {
    var point = MarkdownFrontMatterFoldingPolicy.EP_NAME.getPoint();
    point.registerExtension(_ -> true, getTestRootDisposable());
    point.registerExtension(_ -> true, getTestRootDisposable());
    myFixture.configureByText("notes.md", "---\nSome front matter\n---\n\nText");

    String foldingDescription = ((CodeInsightTestFixtureImpl)myFixture).getFoldingDescription(false, false);
    assertTrue(foldingDescription, foldingDescription.contains("<fold"));
  }

  private void doTest() {
    myFixture.testFolding(getTestDataPath() + "/" + getTestName(true) + ".md");
  }

  @Override
  protected String getTestDataPath() {
    return MarkdownTestingUtil.TEST_DATA_PATH + "/folding";
  }
}
