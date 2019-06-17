package org.intellij.plugins.markdown.spellchecking;

import com.intellij.spellchecker.inspections.SpellCheckingInspection;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.intellij.plugins.markdown.MarkdownTestingUtil;

public class MarkdownSpellcheckerTest extends BasePlatformTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new SpellCheckingInspection());
  }

  public void testAll() {
    myFixture.testHighlighting(false, false, true, getTestName(true) + ".md");
  }

  @Override
  protected String getTestDataPath() {
    return MarkdownTestingUtil.TEST_DATA_PATH + "/spellchecker/";
  }
}
