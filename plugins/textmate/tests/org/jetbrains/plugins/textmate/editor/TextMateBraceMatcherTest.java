package org.jetbrains.plugins.textmate.editor;

import com.intellij.codeInsight.highlighting.BraceMatchingUtil;
import com.intellij.openapi.application.ReadAction;
import org.jetbrains.plugins.textmate.TextMateAcceptanceTestCase;

public class TextMateBraceMatcherTest extends TextMateAcceptanceTestCase {
  public void testBracesInLatex() {
    myFixture.configureByText("text.tex", "\\setlength<caret>{\\parskip}");
    assertEquals(19, getMatchedOffset());
  }

  public void testMultiCharBracesInJs() {
    myFixture.configureByText("text.js_hack", "<caret>/** hello */");
    assertEquals(10, getMatchedOffset());
  }

  public void testMatchingInInjectedCode() {
    myFixture.configureByText("text.md_hack", "<html><caret><p>Paragraph</p></html>");
    assertEquals(8, getMatchedOffset());
  }

  public void testJsx() {
    myFixture.configureByText("text.jsx_hack", "foo() {<caret> return <p>Paragraph</p>;}");
    assertEquals(32, getMatchedOffset());
  }

  public void testJsxTags() {
    myFixture.configureByText("text.jsx_hack", "foo() { return <caret><p>Paragraph</p>;}");
    assertEquals(17, getMatchedOffset());
  }

  public void testBrackets() {
    myFixture.configureByText("text.md_hack", "<caret>[link](url)");
    assertEquals(5, getMatchedOffset());
  }

  private int getMatchedOffset() {
    return ReadAction
      .compute(() -> BraceMatchingUtil.getMatchedBraceOffset(myFixture.getEditor(), true, myFixture.getFile()));
  }
}
