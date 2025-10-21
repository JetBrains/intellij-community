package org.jetbrains.plugins.textmate.spellchecker;

import com.intellij.grazie.spellcheck.GrazieSpellCheckingInspection;
import org.jetbrains.plugins.textmate.TextMateAcceptanceTestCase;

public class TextMateSpellcheckingTest extends TextMateAcceptanceTestCase {
  public void testSimple() {
    myFixture.enableInspections(GrazieSpellCheckingInspection.class);
    myFixture.configureByText("test.md_hack", "some <TYPO>typooo</TYPO>");
    myFixture.checkHighlighting();
  }
}
