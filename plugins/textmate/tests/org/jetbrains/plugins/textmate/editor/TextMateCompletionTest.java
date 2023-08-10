package org.jetbrains.plugins.textmate.editor;

import org.jetbrains.plugins.textmate.TextMateAcceptanceTestCase;

public class TextMateCompletionTest extends TextMateAcceptanceTestCase {
  public void testCompletionWithEmptyPrefix() {
    myFixture.configureByText("test.md_hack", """
      Hello, I love you
      Let me jump in your game
      <caret>""");
    myFixture.completeBasic();
    assertSameElements(myFixture.getLookupElementStrings(), "Hello", "I", "love", "you", "Let", "me", "jump", "in", "your", "game");
  }
}
