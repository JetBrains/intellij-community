package org.jetbrains.plugins.textmate.editor;

import com.intellij.codeInsight.CodeInsightSettings;
import org.jetbrains.plugins.textmate.TextMateAcceptanceTestCase;

public class TextMateTypedHandlerTest extends TextMateAcceptanceTestCase {
  public void testQuotes_1() {
    doTest("<caret>", "md_hack", "\"", "\"<caret>\"");
  }

  public void testQuotes_2() {
    doTest("\"<caret>\"", "md_hack", "\"", "\"\"<caret>");
  }

  public void testQuotesAfterSpecSymbol_1() {
    doTest("\"asd<caret>", "md_hack", "\"", "\"asd\"<caret>");
  }

  public void testQuotesAfterSpecSymbol_2() {
    doTest("\"asd <caret>", "md_hack", "\"", "\"asd \"<caret>\"");
  }

  public void testQuotesAfterSpecSymbol_3() {
    doTest("\"asd(<caret>", "md_hack", "\"", "\"asd(\"<caret>\"");
  }

  public void testQuotesBeforeChar() {
    doTest("\"asd <caret>asd", "md_hack", "\"", "\"asd \"<caret>asd");
  }

  public void testTagsInHtml() {
    doTest("<html><caret></html>", "md_hack", "<", "<html><<caret>></html>");
  }

  public void testTagsInJsx() {
    doTest("var a = <div><caret></div>", "jsx_hack", "<", "var a = <div><<caret>></div>");
  }

  public void testBracePairInLatex_1() {
    doTest("<caret>", "tex", "`", "`<caret>'");
  }

  public void testBracePairInLatex_2() {
    doTest("`<caret>'", "tex", "'", "`'<caret>");
  }

  public void testMultiCharBraces() {
    doTest("/*<caret>", "js_hack", "*", "/**<caret> */");
  }

  public void testDisabledSmartBraces() {
    doTest("\\<caret>", "text", "`", "\\`<caret>");
  }

  public void testDisableQuotes() {
    CodeInsightSettings settings = CodeInsightSettings.getInstance();
    boolean oldValue = settings.AUTOINSERT_PAIR_QUOTE;
    try {
      settings.AUTOINSERT_PAIR_QUOTE = false;
      doTest("<caret>", "md_hack", "\"", "\"<caret>");
    }
    finally {
      settings.AUTOINSERT_PAIR_QUOTE = oldValue;
    }
  }

  public void testDisableBraces() {
    CodeInsightSettings settings = CodeInsightSettings.getInstance();
    boolean oldValue = settings.AUTOINSERT_PAIR_BRACKET;
    try {
      settings.AUTOINSERT_PAIR_BRACKET = false;
      doTest("<html><caret></html>", "md_hack", "<", "<html><<caret></html>");
    }
    finally {
      settings.AUTOINSERT_PAIR_BRACKET = oldValue;
    }
  }

  private void doTest(String source, String extension, String toType, String expected) {
    myFixture.configureByText(getTestName(true) + "." + extension, source);
    myFixture.type(toType);
    myFixture.checkResult(expected);
  }
}
