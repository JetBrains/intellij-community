// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.editor;

import com.intellij.testFramework.LightPlatformCodeInsightTestCase;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

public class ShTypingTest extends BasePlatformTestCase {
  // @formatter:off
  public void testString()              { doTypingTest("<caret>", "\"", "\"<caret>\"");         }
  public void testRawString()           { doTypingTest("<caret>", "'", "'<caret>'");            }
  public void testBackQuote()           { doTypingTest("<caret>", "`", "`<caret>`");            }

  public void testStringBeforeFoo()     { doTypingTest("<caret>foo", "\"", "\"<caret>foo");     }
  public void testRawStringBeforeFoo()  { doTypingTest("<caret>foo", "'", "'<caret>foo");       }
  public void testBackQuoteBeforeFoo()  { doTypingTest("<caret>foo", "`", "`<caret>foo");       }

  public void testQuoteAfterFoo()      { doTypingTest("\"foo<caret>", "\"", "\"foo\"<caret>");   }
  public void testRawStringAfterFoo()  { doTypingTest("'foo<caret>", "'", "'foo'<caret>");       }
  public void testBackQuoteAfterFoo()  { doTypingTest("`foo<caret>", "`", "`foo`<caret>");       }

  public void testStringAfter()         { doTypingTest("\"<caret>\"", "\"", "\"\"<caret>");     }
  public void testRawStringAfter()      { doTypingTest("'<caret>'", "'", "''<caret>");          }
  public void testBackQuoteAfter()      { doTypingTest("`<caret>`", "`", "``<caret>");          }

  public void testStringBackspace()     { doBackspaceTest("\"<caret>\"", "<caret>");            }
  public void testRawStringBackspace()  { doBackspaceTest("'<caret>'", "<caret>");              }
  public void testQuoteBackspace()      { doBackspaceTest("`<caret>`", "<caret>");              }
  // @formatter:on

  private void doTypingTest(String before, String forType, String after) {
    myFixture.configureByText("a.sh", before);
    myFixture.type(forType);
    myFixture.checkResult(after);
  }

  private void doBackspaceTest(String before, String after) {
    myFixture.configureByText("a.sh", before);
    LightPlatformCodeInsightTestCase.backspace(myFixture.getEditor(), getProject());
    myFixture.checkResult(after);
  }
}
