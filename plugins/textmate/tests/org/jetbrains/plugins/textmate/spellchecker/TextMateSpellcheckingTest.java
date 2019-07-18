package org.jetbrains.plugins.textmate.spellchecker;

import com.intellij.spellchecker.inspections.SpellCheckingInspection;
import org.jetbrains.plugins.textmate.TextMateAcceptanceTestCase;

public class TextMateSpellcheckingTest extends TextMateAcceptanceTestCase {
  public void testSimple() {
    myFixture.enableInspections(SpellCheckingInspection.class);
    myFixture.configureByText("test.md_hack", "some <TYPO>typooo</TYPO>");
    myFixture.checkHighlighting();
  }
}
