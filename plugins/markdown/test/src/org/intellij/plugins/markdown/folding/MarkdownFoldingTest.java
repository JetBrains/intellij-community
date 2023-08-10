package org.intellij.plugins.markdown.folding;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.intellij.plugins.markdown.MarkdownTestingUtil;

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

  public void testTableOfContents() {
    doTest();
  }

  public void testFrontMatter() {
    doTest();
  }

  private void doTest() {
    myFixture.testFolding(getTestDataPath() + "/" + getTestName(true) + ".md");
  }

  @Override
  protected String getTestDataPath() {
    return MarkdownTestingUtil.TEST_DATA_PATH + "/folding";
  }
}
