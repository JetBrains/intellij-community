package com.intellij.bash.editing;

import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;

public class BashTypingTest extends LightCodeInsightFixtureTestCase {
  // @formatter:off
  public void testRawString()           { doTypingTest("<caret>", "'", "'<caret>'");            }
  public void testRawStringAfter()      { doTypingTest("'<caret>'", "'", "''<caret>");          }
  public void testString()              { doTypingTest("<caret>", "\"", "\"<caret>\"");         }
  public void testStringAfter()         { doTypingTest("\"<caret>\"", "\"", "\"\"<caret>");     }
  public void testBackQuote()           { doTypingTest("<caret>", "`", "`<caret>`");           }
  public void testBackQuoteAfter()      { doTypingTest("`<caret>`", "`", "``<caret>");         }
  // @formatter:on

  private void doTypingTest(String before, String forType, String after) {
    myFixture.configureByText("a.sh", before);
    myFixture.type(forType);
    myFixture.checkResult(after);
  }
}
